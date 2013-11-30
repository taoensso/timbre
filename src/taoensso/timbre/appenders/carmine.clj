(ns taoensso.timbre.appenders.carmine
  "Carmine (Redis) appender. Requires https://github.com/ptaoussanis/carmine."
  {:author "Peter Taoussanis"}
  (:require [taoensso.carmine :as car]
            [taoensso.timbre  :as timbre]))

(defn default-keyfn [level] {:pre [(string? level)]}
  (format "carmine:timbre:default:%s" level))

(defn make-carmine-appender
  "Alpha - subject to change!
  Returns a Carmine Redis appender that logs serialized entries as follows:
   * Logs only the most recent instance of each unique entry.
   * Limits the number of entries per level (configurable).
   * Sorts entries by date of most recent occurence.

  See accompanying `query-entries` fn to return deserialized log entries."
  [& [appender-opts {:keys [conn keyfn nentries-by-level]
                     :or   {conn  {}
                            keyfn default-keyfn
                            nentries-by-level {:trace    20
                                               :debug    20
                                               :info     50
                                               :warn    100
                                               :error   300
                                               :fatal   500
                                               :report  500}}}]]
  {:pre [(string? (keyfn "debug"))
         (every? #(contains? nentries-by-level %) timbre/levels-ordered)
         (every? #(and (integer? %) (<= 0 % 100000)) (vals nentries-by-level))]}

  (let [default-appender-opts {:enabled? true :min-level nil}]
    (merge default-appender-opts appender-opts
      {:fn
       (fn [{:keys [level instant] :as apfn-args}]
         (let [k (keyfn (name level))
               nmax-entries (nentries-by-level level)
               ;; Note that we _exclude_ :instant for uniqueness and efficiency
               ;; (we'll use it as zset score):
               entry (select-keys apfn-args [:level :throwable :args
                                             :profile-stats :hostname :ns])
               udt (.getTime ^java.util.Date instant)]
           (car/wcar conn
             (car/zadd k udt entry)
             (car/zremrangebyrank k 0 (dec (- nmax-entries))))))})))

;;;; Query utils

(defn query-entries
  "Alpha - subject to change!
  Returns latest `n` log entries by level as an ordered vector of deserialized
  maps. Normal sequence fns can be used to query/transform entries."
  [conn level & [n asc? keyfn]]
  {:pre  [(or (nil? n) (and (integer? n) (<= 1 n 100000)))]}
  (let [keyfn (or keyfn default-keyfn)
        k     (keyfn (name level))]
    (->>
     (car/wcar conn
       (if asc? (car/zrange    k 0 (if n (dec n) -1) :withscores)
                (car/zrevrange k 0 (if n (dec n) -1) :withscores)))
     ;; Reconstitute :instant keys from scores:
     (partition 2)
     (reduce (fn [v [m-entry score]]
               (conj v (assoc m-entry :instant (car/as-long score))))
             []))))

;;;; Dev/tests

(comment
  (timbre/log {:timestamp-pattern "yyyy-MMM-dd HH:mm:ss ZZ"
               :appenders {:carmine (make-carmine-appender)}}
    :info "Hello1" "Hello2")

  (car/wcar {} (car/del  (default-keyfn "info")))
  (car/wcar {} (car/keys (default-keyfn "*")))
  (count (query-entries {} :info 2 :asc)))
