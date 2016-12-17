(ns taoensso.timbre.appenders.carmine
  "Carmine (Redis) appender.
  Requires https://github.com/ptaoussanis/carmine."
  {:author "Peter Taoussanis (@ptaoussanis)"}
  (:require [taoensso.carmine :as car]
            [taoensso.nippy   :as nippy]
            [taoensso.timbre  :as timbre]
            [taoensso.encore  :as enc :refer [have have?]]))

(defn- sha48
  "Truncated 160bit SHA hash (48bit Long). Redis can store small
  collections of these quite efficiently."
  [x] (-> (str x)
          (org.apache.commons.codec.digest.DigestUtils/shaHex)
          (.substring 0 11)
          (Long/parseLong 16)))

(comment (enc/qb 10000 (sha48 "I'm gonna get hashed!")))

(defn default-keyfn [level] (str "timbre:carmine:" (name level)))
(def  default-nentries-by-level
  {:trace   50
   :debug   50
   :info    50
   :warn   100
   :error  100
   :fatal  100
   :report 100})

(defn default-entry-fn "(fn [data])-><db-entry>"
  [data]
  (let [{:keys [instant level vargs hostname_ timestamp_
                context ?err ?ns-str ?file ?line ?msg-fmt profile-stats]}
        data]
    (enc/assoc-some
     {:instant   instant
      :level     level
      :vargs     vargs
      :hostname  (force hostname_)
      ;; :timestamp (force timestamp_)
      }
     :context  context
     :?err     ?err
     :?ns-str  ?ns-str
     :?file    ?file
     :?line    ?line
     :?msg-fmt ?msg-fmt
     :profile-stats profile-stats)))

(defn carmine-appender
  "Alpha, subject to change.

  Returns a Carmine Redis appender:
    * All raw logging args are preserved in serialized form (even errors).
    * Configurable number of entries to keep per logging level.
    * Only the most recent instance of each unique entry is kept.
    * Resulting log is just a Clojure value: a vector of log entries (maps).

  See also `query-entries`."
  [& [{:keys [conn-opts keyfn entry-fn nentries-by-level]
       :or   {keyfn             default-keyfn
              entry-fn          default-entry-fn
              nentries-by-level default-nentries-by-level}}]]

  (have? string?  (keyfn             :info))
  (have? integer? (nentries-by-level :info))

  {:enabled?   true
   :async?     false
   :min-level  nil
   :rate-limit nil
   :output-fn  :inherit
   :fn
   (fn [data]
     (let [{:keys [level instant hash_]} data
           entry-hash (sha48 (force hash_))
           entry      (entry-fn data)

           k-zset (keyfn level)
           k-hash (str k-zset ":entries")
           udt (.getTime ^java.util.Date instant) ; Use as zscore
           nmax-entries (long (nentries-by-level level))]

       (when (> nmax-entries 0)
         (car/wcar conn-opts
           (binding [nippy/*final-freeze-fallback*
                     nippy/freeze-fallback-as-str]
             (car/hset k-hash entry-hash entry))
           (car/zadd k-zset udt entry-hash)

           (when (< ^double (rand) 0.01) ; Occasionally GC
             ;; This is necessary since we're doing zset->entry-hash->entry
             ;; rather than zset->entry. We want the former for the control
             ;; it gives us over what should constitute a 'unique' entry.
             (car/lua
               "-- -ive idx used to prune from the right (lowest score first)
               local max_idx = (0 - (tonumber(_:nmax-entries)) - 1)
               local entries_to_prune =
                 redis.call('zrange', _:k-zset, 0, max_idx)
               redis.call('zremrangebyrank', _:k-zset, 0, max_idx) -- Prune zset

               for i,entry in pairs(entries_to_prune) do
                 redis.call('hdel', _:k-hash, entry) -- Prune hash
               end
               return nil"
               {:k-zset k-zset
                :k-hash k-hash}
               {:nmax-entries nmax-entries}))))))})

;;;; Query utils

(defn query-entries
  "Alpha, subject to change.
  Returns latest `n` log entries by level as an ordered vector of
  deserialized maps."
  [conn-opts level & [n asc? keyfn]]
  (have? [:or nil? integer?] n)
  (let [keyfn  (or keyfn default-keyfn)
        k-zset (keyfn level)
        k-hash (str k-zset ":entries")

        zset-maps ; [{:level _ :instant _ :entry <hash>} ...]
        (let [-kvs
              (car/wcar conn-opts
                (if asc?
                  (car/zrange    k-zset 0 (if n (dec n) -1) :withscores)
                  (car/zrevrange k-zset 0 (if n (dec n) -1) :withscores)))]

          (persistent!
           (enc/reduce-kvs
            (fn [acc entry-hash udt-score]
              (conj! acc
                {:level   level
                 :instant (java.util.Date. (enc/as-int udt-score))
                 :entry   entry-hash}))
            (transient [])
            -kvs)))

        entries-by-hash ; [<{}-or-ex> <{}-or-ex> ...]
        (when-let [hashes (seq (map :entry zset-maps))]
          (if (next hashes) ; Careful!
            (car/wcar conn-opts              (apply car/hmget k-hash hashes))
            (car/wcar conn-opts :as-pipeline (apply car/hget  k-hash hashes))))]

    (mapv (fn [zset-m entry] (assoc zset-m :entry entry)) zset-maps entries-by-hash)))

;;;; Deprecated

(defn make-carmine-appender
  "DEPRECATED. Please use `carmine-appender` instead."
  [& [appender-merge opts]]
  (merge (carmine-appender opts) appender-merge))

;;;; Dev/tests

(comment
  (timbre/with-merged-config {:appenders {:carmine (carmine-appender)}}
    (timbre/info "foo" "bar"))

  (car/wcar {} (car/keys    (default-keyfn "*")))
  (car/wcar {} (car/hgetall (default-keyfn "info:entries")))

  (count (query-entries {} :info 5))
  (count (query-entries {} :info 5 :asc))

  (car/wcar {}
    (car/del
     (default-keyfn "info")
     (default-keyfn "info:entries"))))
