(ns taoensso.timbre.profiling
  "Logging profiler for Timbre, adapted from clojure.contrib.profile."
  {:author "Peter Taoussanis"}
  (:require [taoensso.encore :as encore]
            [taoensso.timbre :as timbre]))

(defmacro fq-keyword "Returns namespaced keyword for given id."
  [id]
  `(if (and (keyword? ~id) (namespace ~id)) ~id
     (keyword (str *ns*) (name ~id))))

(comment (map #(fq-keyword %) ["foo" :foo :foo/bar]))

(def ^:dynamic *pdata* "{::pid [time1 time2 ...]}" nil)

(defn pspy* [id f]
  (if-not *pdata* (f)
    (let [id (fq-keyword id)
          t0 (System/nanoTime)]
      (try (f)
           (finally
             (let [t-elapsed (- (System/nanoTime) t0)]
               (swap! *pdata* #(assoc % id (conj (% id []) t-elapsed)))))))))

(defmacro pspy
  "Profile spy. When in the context of a *pdata* binding, records execution time
  of named body. Always returns the body's result."
  [id & body] `(pspy* ~id (fn [] ~@body)))

;;; Aliases
(def p* pspy*)
(defmacro p [id & body] `(pspy ~id ~@body))

(comment
  (time (dotimes [_ 1000000])) ; ~20ms
  ;; Note that times are ~= for `pspy` as a pure macro and as a `pspy*` fn caller:
  (time (dotimes [_ 1000000] (pspy :foo))) ; ~300ms
  )

(declare pdata-stats format-pdata)

(defmacro with-pdata [level & body]
  `(if-not (timbre/logging-enabled? ~level ~(str *ns*))
     {:result (do ~@body)}
     (binding [*pdata* (atom {})]
       {:result (p ::clock-time ~@body)
        :stats  (pdata-stats @*pdata*)})))

(defmacro profile
  "When logging is enabled, executes named body with profiling enabled. Body
  forms wrapped in (pspy) will be timed and time stats logged. Always returns
  body's result.

  Note that logging appenders will receive both a formatted profiling string AND
  the raw profiling stats under a special :profiling-stats key (useful for
  queryable db logging)."
  [level id & body]
  `(let [{result# :result stats# :stats} (with-pdata ~level ~@body)]
     (when stats#
       (timbre/log* {:profile-stats stats#} :format ~level
                    "Profiling: %s\n%s" (fq-keyword ~id)
                    (format-pdata stats#)))
     result#))

(defmacro sampling-profile
  "Like `profile`, but only enables profiling with given probability."
  [level probability id & body]
  `(do (assert (<= 0 ~probability 1) "Probability: 0<=p<=1")
       (if-not (< (rand) ~probability) (do ~@body)
         (profile ~level ~id ~@body))))

(defn pdata-stats
  "{::pid [time1 time2 ...] ...} => {::pid {:min <min-time> ...} ...}
  For performance, stats are calculated once only after all data have been
  collected."
  [pdata]
  (reduce-kv
   (fn [m pid times]
     (let [count (max 1 (count times))
           time  (reduce + times)
           mean  (long (/ time count))
           mad   (long (/ (reduce + (map #(Math/abs (long (- % mean)))
                                         times)) ; Mean absolute deviation
                          count))]
       (assoc m pid {:count count
                     :min   (apply min times)
                     :max   (apply max times)
                     :mean  mean
                     :mad   mad
                     :time  time})))
   {} (or pdata {})))

(defn format-pdata [stats & [sort-field]]
  (let [clock-time (-> stats ::clock-time :time) ; How long entire profile body took
        stats        (dissoc stats ::clock-time)
        accounted    (reduce + (map :time (vals stats)))
        max-id-width (apply max (map (comp count str)
                                     (conj (keys stats) "Accounted Time")))
        pattern   (str "%" max-id-width "s %6d %9s %10s %9s %9s %7d %1s%n")
        s-pattern (.replace pattern \d \s)
        perc      #(Math/round (/ %1 %2 0.01))
        ft (fn [nanosecs]
             (let [pow     #(Math/pow 10 %)
                   ok-pow? #(>= nanosecs (pow %))
                   to-pow  #(encore/round (/ nanosecs (pow %1)) :round %2)]
               (cond (ok-pow? 9) (str (to-pow 9 1) "s")
                     (ok-pow? 6) (str (to-pow 6 0) "ms")
                     (ok-pow? 3) (str (to-pow 3 0) "μs")
                     :else       (str nanosecs     "ns"))))]

    (with-out-str
      (printf s-pattern "Id" "Calls" "Min" "Max" "MAD" "Mean" "Time%" "Time")
      (doseq [pid (->> (keys stats)
                       (sort-by #(- (get-in stats [% (or sort-field :time)]))))]
        (let [{:keys [count min max mean mad time]} (stats pid)]
          (printf pattern pid count (ft min) (ft max) (ft mad)
                  (ft mean) (perc time clock-time) (ft time))))

      (printf s-pattern "Clock Time" "" "" "" "" "" 100 (ft clock-time))
      (printf s-pattern "Accounted Time" "" "" "" "" ""
              (perc accounted clock-time) (ft accounted)))))

(defmacro defnp "Like `defn` but wraps body in `p` macro."
  {:arglists '([name ?doc-string ?attr-map [params] ?prepost-map body])}
  [name & sigs]
  (let [[name [params & sigs]] (encore/name-with-attrs name sigs)
        prepost-map (when (and (map? (first sigs)) (next sigs)) (first sigs))
        body (if prepost-map (next sigs) sigs)]
    `(defn ~name ~params ~prepost-map
       (p ~(clojure.core/name name)
          ~@body))))

(comment (defnp foo "Docstring "[x] "boo" (* x x))
         (macroexpand '(defnp foo "Docstring "[x] "boo" (* x x)))
         (profile :info :defnp-test (foo 5)))

(comment
  (profile :info :sleepy-threads
    (dotimes [n 5]
      (Thread/sleep 100) ; Unaccounted
      (p :1ms  (Thread/sleep 1))
      (p :2s   (Thread/sleep 2000))
      (p :50ms (Thread/sleep 50))
      (p :rand (Thread/sleep (if (> 0.5 (rand)) 10 500)))
      (p :10ms (Thread/sleep 10))
      "Result"))

  (p :hello "Hello, this is a result") ; Falls through (no *pdata* context)

  (defn my-fn
    []
    (let [nums (vec (range 1000))]
      (+ (p :fast-sleep (Thread/sleep 1) 10)
         (p :slow-sleep (Thread/sleep 2) 32)
         (p :add  (reduce + nums))
         (p :sub  (reduce - nums))
         (p :mult (reduce * nums))
         (p :div  (reduce / nums)))))

  (profile :info :Arithmetic (dotimes [n 100] (my-fn)))

  (sampling-profile :info 0.2 :sampling-test (p :string "Hello!")))
