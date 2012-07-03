(ns taoensso.timbre.profiling
  "Simple all-Clojure profiling adapted from clojure.contrib.profile."
  {:author "Peter Taoussanis"}
  (:require [taoensso.timbre :as timbre]))

(def ^:dynamic *plog* "{::pname [time1 time2 ...] ...}" nil)

(defmacro p
  "When in the context of a *plog* binding, records execution time of named
  body. Always returns the body's result."
  [name & body]
  (let [name (keyword (str *ns*) (clojure.core/name name))]
    `(if *plog*
       (let [start-time# (System/nanoTime)
             result#     (do ~@body)
             elapsed#    (- (System/nanoTime) start-time#)]
         (swap! *plog* #(assoc % ~name (conj (% ~name []) elapsed#)))
         result#)
       (do ~@body))))

(defn plog-stats
  "{::pname [time1 time2 ...] ...} => {::pname {:min <min-time> ...} ...}"
  [plog]
  (reduce (fn [m [pname times]]
            (let [count (count times)
                  total (reduce + times)]
              (assoc m pname {:count count
                              :min   (apply min times)
                              :max   (apply max times)
                              :mean  (int (/ total count))
                              :total total})))
          {} plog))

(defn fqname
  "Like `name` but returns fully-qualified name."
  [keyword]
  (str (namespace keyword) "/" (name keyword)))

(defn plog-table
  "Returns formatted plog stats table for given plog stats."
  ([stats] (plog-table stats :total))
  ([stats sort-field]
     (let [grand-total-time (reduce + (map :total (vals stats)))
           max-name-width   (apply max (map (comp count str)
                                            (conj (keys stats) "Event")))
           pattern   (str "%" max-name-width "s %6d %9s %10s %9s %7d %1s%n")
           s-pattern (.replace pattern \d \s)

           ft (fn [nanosecs]
                (let [pow     #(Math/pow 10 %)
                      ok-pow? #(>= nanosecs (pow %))
                      to-pow  #(long (/ nanosecs (pow %)))]
                  (cond (ok-pow? 9) (str (to-pow 9) "s")
                        (ok-pow? 6) (str (to-pow 6) "ms")
                        (ok-pow? 3) (str (to-pow 3) "μs")
                        :else (str (long nanosecs) "ns"))))]

       (with-out-str
         (printf s-pattern "Event" "Count" "Min" "Max" "Mean" "Total%" "Total")

         (doseq [pname (->> (keys stats)
                            (sort-by #(- (get-in stats [% sort-field]))))]
           (let [{:keys [count min max mean total]} (stats pname)]
             (printf pattern (fqname pname) count (ft min) (ft max) (ft mean)
                     (Math/round (/ total grand-total-time 0.01))
                     (ft total))))

         (printf s-pattern "" "" "" "" "" "" (ft grand-total-time))))))

(defmacro profile*
  "Executes named body with profiling enabled. Body forms wrapped in (p) will be
  timed and time stats sent along with `name` to binary `log-fn`. Returns body's
  result."
  [log-fn name & body]
  (let [name (keyword (str *ns*) (clojure.core/name name))]
    `(binding [*plog* (atom {})]
       (let [result# (do ~@body)]
         (~log-fn ~name (plog-stats @*plog*))))))

(defmacro profile
  "When logging is enabled, executes named body with profiling enabled. Body
  forms wrapped in (p) will be timed and time stats logged. Always returns
  body's result.

  Note that logging appenders will receive both a profiling table string AND the
  raw profiling stats under a special :profiling-stats key. One common use is
  for db appenders to check for this special key and to log profiling stats to
  db in a queryable manner."
  [level name & body]
  (timbre/assert-valid-level level)
  `(if (timbre/logging-enabled? ~level)
     (profile*
      (fn [name# stats#]
        (timbre/log* ~level
                     {:profile-stats stats#}
                     (str "Profiling: " (fqname name#))
                     (str "\n" (plog-table stats#))))
      ~name
      ~@body)
     (do ~@body)))

(defmacro sampling-profile
  "Like `profile`, but only enables profiling every 1/`proportion` calls.
  Always returns body's result."
  [level proportion name & body]
  `(if (> ~proportion (rand))
     (profile ~level ~name ~@body)
     (do ~@body)))

(comment
  (profile :info :Sleepy-threads
           (p :1ms  (Thread/sleep 1))
           (p :2s   (Thread/sleep 2000))
           (p :50ms (Thread/sleep 50))
           (p :10ms (Thread/sleep 10))
           "Result")

  (p :hello "Hello, this is a result") ; Falls through (no *plog* context)

  (let [nums (range 1000)]
    (profile :info :Arithmetic
             (dotimes [n 1000]
               (p :add      (reduce + nums))
               (p :subtract (reduce - nums))
               (p :multiply (reduce * nums))
               (p :divide   (reduce / nums)))))

  (sampling-profile :info 0.2 :Sampling-test (p :string "Hello!")))