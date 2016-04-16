(ns taoensso.timbre.appenders.carmine
  "Carmine (Redis) appender. Requires https://github.com/ptaoussanis/carmine."
  {:author "Peter Taoussanis"}
  (:require [taoensso.carmine :as car]
            [taoensso.nippy   :as nippy]
            [taoensso.timbre  :as timbre]
            [taoensso.encore  :as enc :refer (have have?)]))

(defn- sha48
  "Truncated 160bit SHA hash (48bit Long). Redis can store small collections of
  these quite efficiently."
  [x] (-> (str x)
          (org.apache.commons.codec.digest.DigestUtils/shaHex)
          (.substring 0 11)
          (Long/parseLong 16)))

(comment (sha48 {:key "I'm gonna get hashed!"}))

(defn default-keyfn [level] {:pre [(have? string? level)]}
  (str "carmine:timbre:default:" level))

(defn carmine-appender
  "Returns a Carmine Redis appender (experimental, subject to change):
    * All raw logging args are preserved in serialized form (even Throwables!).
    * Only the most recent instance of each unique entry is kept (uniqueness
      determined by data-hash-fn).
    * Configurable number of entries to keep per logging level.
    * Log is just a value: a vector of Clojure maps: query+manipulate with
      standard seq fns: group-by hostname, sort/filter by ns & severity, explore
      exception stacktraces, filter by raw arguments, etc. Datomic and `core.logic`
      also offer interesting opportunities here.

  See accompanying `query-entries` fn to return deserialized log entries."
  [& [{:keys [conn-opts keyfn nentries-by-level]
       :or   {keyfn        default-keyfn
              nentries-by-level {:trace    50
                                 :debug    50
                                 :info     50
                                 :warn    100
                                 :error   100
                                 :fatal   100
                                 :report  100}}}]]

  {:pre [(have? string? (keyfn "test"))
         (have? [:ks>= timbre/ordered-levels] nentries-by-level)
         (have? [:and integer? #(<= 0 % 100000)] :in (vals nentries-by-level))]}

  {:enabled?   true
   :async?     false
   :min-level  nil
   :rate-limit nil
   :output-fn  :inherit
   :fn
   (fn [data]
     (let [{:keys [level instant data-hash-fn]} data
           entry-hash (sha48 (data-hash-fn data))
           entry      (merge
                        {:instant instant
                         :level   level
                         :?ns-str   (:?ns-str       data)
                         :hostname @(:hostname_     data)
                         :vargs    @(:vargs_        data)
                         :?err     @(:?err_         data)}
                        (when-let [pstats (:profile-stats data)]
                          {:profile-stats pstats}))

           k-zset (keyfn (name level))
           k-hash (str k-zset ":entries")
           udt (.getTime ^java.util.Date instant) ; Use as zscore
           nmax-entries (nentries-by-level level)]

       (when (> nmax-entries 0)
         (car/wcar conn-opts
           (binding [nippy/*final-freeze-fallback* nippy/freeze-fallback-as-str]
             (car/hset k-hash entry-hash entry))
           (car/zadd k-zset udt entry-hash)

           (when (< (rand) 0.01) ; Occasionally GC
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
  "Alpha - subject to change!
  Returns latest `n` log entries by level as an ordered vector of deserialized
  maps. Normal sequence fns can be used to query/transform entries. Datomic and
  core.logic are also useful!"
  [conn-opts level & [n asc? keyfn]]
  {:pre [(have? [:or nil? [:and integer? #(<= 1 % 100000)]] n)]}
  (let [keyfn  (or keyfn default-keyfn)
        k-zset (keyfn (name level))
        k-hash (str k-zset ":entries")

        entries-zset ; [{:hash _ :level _ :instant _} ...]
        (->>
         (car/wcar conn-opts
           (if asc? (car/zrange    k-zset 0 (if n (dec n) -1) :withscores)
                    (car/zrevrange k-zset 0 (if n (dec n) -1) :withscores)))
         (partition 2) ; Reconstitute :level, :instant keys:
         (reduce (fn [v [entry-hash score]]
           (conj v {:level   level
                    :instant (java.util.Date. (-> score car/as-long long))
                    :hash    entry-hash}))
                 []))

        entries-hash ; [{_}-or-ex {_}-or-ex ...]
        (when-let [hashes (seq (mapv :hash entries-zset))]
          (if-not (next hashes) ; Careful!
            (car/wcar conn-opts :as-pipeline (apply car/hget  k-hash hashes))
            (car/wcar conn-opts              (apply car/hmget k-hash hashes))))]

    (mapv (fn [m1 m2-or-ex]
            (if (instance? Exception m2-or-ex)
              ;; Should be rare but can happen (e.g. due to faulty Nippy
              ;; extensions or inconsistently-unserializable args):
              (-> (assoc m1 :entry-ex m2-or-ex) (dissoc :hash))
              (-> (merge m1 m2-or-ex)           (dissoc :hash))))
          entries-zset entries-hash)))

;;;; Deprecated

(defn make-carmine-appender
  "DEPRECATED. Please use `carmine-appender` instead."
  [& [appender-merge opts]]
  (merge (carmine-appender opts) appender-merge))

;;;; Dev/tests

(comment
  (timbre/with-merged-config {:appenders {:carmine (carmine-appender)}}
    (timbre/info "Hello1" "Hello2"))

  (car/wcar {} (car/keys (default-keyfn "*")))
  (count (car/wcar {} (car/hgetall (default-keyfn "info:entries"))))

  (car/wcar {} (car/del  (default-keyfn "info")
                         (default-keyfn "info:entries")))

  (car/wcar {} (car/hgetall (default-keyfn "info:entries")))

  (count (query-entries {} :info 2))
  (count (query-entries {} :info 2 :asc)))
