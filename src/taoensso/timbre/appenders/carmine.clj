(ns taoensso.timbre.appenders.carmine
  "Carmine (Redis) appender. Requires https://github.com/ptaoussanis/carmine."
  {:author "Peter Taoussanis"}
  (:require [taoensso.carmine :as car]
            [taoensso.timbre  :as timbre]))

(defn- sha48
  "Truncated 160bit SHA hash (48bit Long). Redis can store small collections of
  these quite efficiently."
  [x] (-> (str x)
          (org.apache.commons.codec.digest.DigestUtils/shaHex)
          (.substring 0 11)
          (Long/parseLong 16)))

(comment (sha48 {:key "I'm gonna get hashed!"}))

(defn default-keyfn [level] {:pre [(string? level)]}
  (format "carmine:timbre:default:%s" level))

(defn make-carmine-appender
  "Alpha - subject to change!
  Returns a Carmine Redis appender:
   * All raw logging args are preserved in serialized form (even Throwables!).
   * Only the most recent instance of each unique entry is kept (hash fn used
     to determine uniqueness is configurable).
   * Configurable number of entries to keep per logging level.
   * Log is just a value: a vector of Clojure maps: query+manipulate with
     standard seq fns: group-by hostname, sort/filter by ns & severity, explore
     exception stacktraces, filter by raw arguments, etc. Datomic and `core.logic`
     also offer interesting opportunities here.

  See accompanying `query-entries` fn to return deserialized log entries."
  [& [appender-opts {:keys [conn keyfn args-hash-fn nentries-by-level]
                     :or   {keyfn        default-keyfn
                            args-hash-fn timbre/default-args-hash-fn
                            nentries-by-level {:trace    50
                                               :debug    50
                                               :info     50
                                               :warn    100
                                               :error   100
                                               :fatal   100
                                               :report  100}}}]]
  {:pre [(string? (keyfn "test"))
         (every? #(contains? nentries-by-level %) timbre/levels-ordered)
         (every? #(and (integer? %) (<= 0 % 100000)) (vals nentries-by-level))]}

  (let [default-appender-opts {:enabled? true :min-level nil}]
    (merge default-appender-opts appender-opts
      {:fn
       (fn [{:keys [level instant] :as apfn-args}]
         (let [entry-hash (sha48 (args-hash-fn apfn-args))
               entry      (select-keys apfn-args [:hostname :ns :args :throwable
                                                  :profile-stats])
               k-zset (keyfn (name level))
               k-hash (str k-zset ":entries")
               udt (.getTime ^java.util.Date instant) ; Use as zscore
               nmax-entries (nentries-by-level level)]

           (when (> nmax-entries 0)
             (car/wcar conn
               (car/hset k-hash entry-hash entry)
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
                {:nmax-entries nmax-entries}))))))})))

;;;; Query utils

(defn query-entries
  "Alpha - subject to change!
  Returns latest `n` log entries by level as an ordered vector of deserialized
  maps. Normal sequence fns can be used to query/transform entries. Datomic and
  core.logic are also useful!"
  [conn level & [n asc? keyfn]]
  {:pre  [(or (nil? n) (and (integer? n) (<= 1 n 100000)))]}
  (let [keyfn  (or keyfn default-keyfn)
        k-zset (keyfn (name level))
        k-hash (str k-zset ":entries")

        entries-zset ; [{:hash _ :level _ :instant _} ...]
        (->>
         (car/wcar conn
           (if asc? (car/zrange    k-zset 0 (if n (dec n) -1) :withscores)
                    (car/zrevrange k-zset 0 (if n (dec n) -1) :withscores)))
         (partition 2) ; Reconstitute :level, :instant keys:
         (reduce (fn [v [entry-hash score]]
           (conj v {:level   level
                    :instant (car/as-long score)
                    :hash    entry-hash}))
                 []))

        entries-hash ; [{_} {_} ...]
        (car/wcar conn (apply car/hmget k-hash (mapv :hash entries-zset)))]

    (mapv (fn [m1 m2] (-> (merge m1 m2) (dissoc :hash)))
          entries-zset entries-hash)))

;;;; Dev/tests

(comment
  (timbre/log {:timestamp-pattern "yyyy-MMM-dd HH:mm:ss ZZ"
               :appenders {:carmine (make-carmine-appender)}}
    :info "Hello1" "Hello2")

  (car/wcar {} (car/keys (default-keyfn "*")))
  (count (car/wcar {} (car/hgetall (default-keyfn "info:entries"))))

  (car/wcar {} (car/del  (default-keyfn "info")
                         (default-keyfn "info:entries")))

  (car/wcar {} (car/hgetall (default-keyfn "info:entries")))

  (count (query-entries {} :info 2))
  (count (query-entries {} :info 2 :asc)))
