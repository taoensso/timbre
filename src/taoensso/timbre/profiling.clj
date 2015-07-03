(ns taoensso.timbre.profiling
  "Logging profiler for Timbre, adapted from clojure.contrib.profile."
  {:author "Peter Taoussanis"}
  (:require [taoensso.encore :as enc]
            [taoensso.timbre :as timbre]))

;;;; TODO ns could use some housekeeping
;; * Boxed math optimizations
;; * Possible porting to .cljx (any point?)
;; * Support for explicit `config` args?
;; * General housekeeping, perf work

;;;; Utils

(defmacro fq-keyword "Returns namespaced keyword for given id."
  [id] `(if (and (keyword? ~id) (namespace ~id)) ~id
          (keyword ~(str *ns*) (name ~id))))

(comment (map #(fq-keyword %) ["foo" :foo :foo/bar]))

;;;;

(def ^:dynamic *pdata*
  "{::pid {:times [t1 t2 ...] ; Times awaiting merge into stats
           :ntimes _          ; (count times)
           :stats {}          ; Cumulative stats
          }}"
  nil)

(declare capture-time! merge-times>stats!)

(defmacro pspy
  "Profile spy. When in the context of a *pdata* binding, records execution time
  of named body. Always returns the body's result."
  ;; Note: do NOT implement as `(pspy* ~id (fn [] ~@body))`. The fn wrapping
  ;; can cause unnecessary lazy seq head retention, Ref. http://goo.gl/42Vxph.
  [id & body]
  `(if-not *pdata* (do ~@body)
     (let [id# (fq-keyword ~id)
           t0# (System/nanoTime)]
       (try (do ~@body)
            (finally (capture-time! id# (- (System/nanoTime) t0#)))))))

(defmacro p [id & body] `(pspy ~id ~@body)) ; Alias

(defn pspy* [id f]
  (if-not *pdata* (f)
    (let [id (fq-keyword id)
          t0 (System/nanoTime)]
      (try (f)
           (finally (capture-time! id (- (System/nanoTime) t0)))))))

(def p* pspy*) ; Alias

(comment
  (binding [*pdata* {}])
  (time (dotimes [_ 1000000])) ; ~3ms
  (time (dotimes [_ 1000000] (pspy :foo))) ; ~65ms (^:dynamic bound >= once!)
  )

(declare ^:private format-stats)

(defmacro with-pdata [level & body]
  `(if-not (timbre/log? ~level ~(str *ns*))
     {:result (do ~@body)}
     (binding [*pdata* (atom {})]
       {:result (pspy ::clock-time ~@body)
        :stats  (merge-times>stats!)})))

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
       (timbre/log1-macro timbre/*config* ~level :f
         ["Profiling: %s\n%s" (fq-keyword ~id) (format-stats stats#)]
         {:profile-stats stats#}))
     result#))

(defmacro sampling-profile
  "Like `profile`, but only enables profiling with given probability."
  [level probability id & body]
  `(do (assert (<= 0 ~probability 1) "Probability: 0<=p<=1")
       (if-not (< (rand) ~probability) (do ~@body)
         (profile ~level ~id ~@body))))

;;;; Data capturing & aggregation

(def ^:private stats-gc-n 111111)

(defn capture-time! [id t-elapsed]
  (let [ntimes
        (->
         (swap! *pdata*
          (fn [m]
            (let [{:as   m-id
                   :keys [times ntimes]
                   :or   {times [] ntimes 0}} (get m id {})]
              (assoc m id
                (assoc m-id :times  (conj times t-elapsed)
                            :ntimes (inc  ntimes))))))
         (get-in [id :ntimes]))]
    (when (= ntimes stats-gc-n) ; Merge to reduce memory footprint
      ;; This is so much slower than `capture-time!` swaps that it gets delayed
      ;; until after entire profiling call completes!:
      ;; (future (merge-times>stats! id)) ; Uses binding conveyance
      (p :timbre/stats-gc (merge-times>stats! id)))
    nil))

(comment
  (binding [*pdata* (atom {})]
    (capture-time! :foo 100000)
    (capture-time! :foo 100000)
    *pdata*))

(defn merge-times>stats!
  ([] ; -> {<pid> <merged-stats>}
   (reduce (fn [m pid] (assoc m pid (merge-times>stats! pid)))
    {} (keys (or @*pdata* {}))))

  ([id] ; -> <merged-stats>
   (->
    (swap! *pdata*
      (fn [m]
        (let [{:as   m-id
               :keys [times ntimes stats]
               :or   {times  []
                      ntimes 0
                      stats  {}}} (get m id {})]
          (if (empty? times) m
            (let [ts-count   (max 1 ntimes)
                  ts-time    (reduce + times)
                  ts-mean    (/ ts-time ts-count)
                  ;; Batched "online" MAD calculation here is >= the standard
                  ;; Knuth/Welford method, Ref. http://goo.gl/QLSfOc,
                  ;;                            http://goo.gl/mx5eSK.
                  ts-mad-sum (reduce + (map #(Math/abs (long (- % ts-mean)))
                                            times)) ; Mean absolute deviation
                  ;;
                  s-count   (+ (:count stats 0) ts-count)
                  s-time    (+ (:time  stats 0) ts-time)
                  s-mean    (/ s-time s-count)
                  s-mad-sum (+ (:mad-sum stats 0) ts-mad-sum)
                  s-mad     (/ s-mad-sum s-count)
                  s-min (apply min (:min stats Double/POSITIVE_INFINITY) times)
                  s-max (apply max (:max stats 0)                        times)]
              (assoc m id
                (assoc m-id
                  :times []
                  :ntimes 0
                  :stats {:count   s-count
                          :min     s-min
                          :max     s-max
                          :mean    s-mean
                          :mad-sum s-mad-sum
                          :mad     s-mad
                          :time    s-time})))))))
    (get-in [id :stats]))))

(comment
  (binding [*pdata* (atom {})]
    (capture-time!       :foo 10)
    (capture-time!       :foo 20)
    (merge-times>stats!  :foo)
    (capture-time!       :foo 30)
    (merge-times>stats!  :foo)
    (merge-times>stats!  :bar)
    (capture-time!       :foo 10)
    *pdata*))

(defn format-stats [stats & [sort-field]]
  (let [clock-time (-> stats ::clock-time :time) ; How long entire profile body took
        stats        (dissoc stats ::clock-time)
        accounted    (reduce + (map :time (vals stats)))
        max-id-width (apply max (map (comp count str)
                                     (conj (keys stats) "Accounted Time")))
        pattern   (str "%" max-id-width "s %,11d %9s %10s %9s %9s %7d %1s%n")
        s-pattern (str "%" max-id-width "s %11s %9s %10s %9s %9s %7s %1s%n")
        perc      #(Math/round (/ %1 %2 0.01))
        ft (fn [nanosecs]
             (let [nanosecs (long nanosecs) ; Truncate any fractional nanosecs
                   pow     #(Math/pow 10 %)
                   ok-pow? #(>= nanosecs (pow %))
                   to-pow  #(enc/round (/ nanosecs (pow %1)) :round %2)]
               (cond (ok-pow? 9) (str (to-pow 9 1) "s")
                     (ok-pow? 6) (str (to-pow 6 0) "ms")
                     (ok-pow? 3) (str (to-pow 3 0) "μs")
                     :else       (str nanosecs     "ns"))))]

    (with-out-str
      (printf s-pattern "Id" "nCalls" "Min" "Max" "MAD" "Mean" "Time%" "Time")
      (doseq [pid (->> (keys stats)
                       (sort-by #(- (get-in stats [% (or sort-field :time)]))))]
        (let [{:keys [count min max mean mad time]} (stats pid)]
          (printf pattern pid count (ft min) (ft max) (ft mad)
                  (ft mean) (perc time clock-time) (ft time))))

      (printf s-pattern "Clock Time" "" "" "" "" "" 100 (ft clock-time))
      (printf s-pattern "Accounted Time" "" "" "" "" ""
              (perc accounted clock-time) (ft accounted)))))

;;;;

(defmacro fnp "Like `fn` but wraps fn bodies with `p` macro."
  {:arglists '([name?  [params*] prepost-map? body]
               [name? ([params*] prepost-map? body)+])}
  [& sigs]
  (let [[?fn-name sigs]
        (if (symbol? (first sigs)) [(first sigs) (next sigs)] ['anonymous-fn sigs])

        single-arity? (vector? (first sigs))
        [sigs get-pid]
        (if single-arity?
          [(list sigs) (fn [?fn-name _params]      (name ?fn-name))]
          [sigs        (fn [?fn-name  params] (str (name ?fn-name) \_ (count params)))])

        new-sigs
        (map
          (fn [[params & others]]
            (let [has-prepost-map?     (and (map? (first others)) (next others))
                  [prepost-map & body] (if has-prepost-map? others (cons {} others))]
              `(~params ~prepost-map (pspy ~(get-pid ?fn-name params) ~@body))))
          sigs)]

    (if ?fn-name
      `(fn ~?fn-name ~@new-sigs)
      `(fn           ~@new-sigs))))

(comment
  (macroexpand '(fnp [x] {:pre [x]} (* x x)))
  (macroexpand '(fn   [x] {:pre [x]} (* x x))))

(defmacro defnp "Like `defn` but wraps fn bodies with `p` macro."
  {:arglists
   '([name doc-string? attr-map?  [params*] prepost-map? body]
     [name doc-string? attr-map? ([params*] prepost-map? body)+ attr-map?])}
  [& sigs]
  (let [[fn-name sigs] (enc/name-with-attrs (first sigs) (next sigs))
        single-arity?  (vector? (first sigs))
        [sigs get-pid]
        (if single-arity?
          [(list sigs) (fn [fn-name _params]      (name fn-name))]
          [sigs        (fn [fn-name  params] (str (name fn-name) \_ (count params)))])

        new-sigs
        (map
          (fn [[params & others]]
            (let [has-prepost-map?     (and (map? (first others)) (next others))
                  [prepost-map & body] (if has-prepost-map? others (cons {} others))]
              `(~params ~prepost-map (pspy ~(get-pid fn-name params) ~@body))))
          sigs)]
    `(defn ~fn-name ~@new-sigs)))

(comment
  (defnp foo "Docstring "[x] "boo" (* x x))
  (macroexpand '(defnp foo "Docstring" [x] "boo" (* x x)))
  (macroexpand '(defnp foo "Docstring" ([x]   (* x x))
                                       ([x y] (* x y))))
  (profile :info :defnp-test (foo 5)))

;;;;

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
  (profile :info :high-n     (dotimes [n 1e6] (p :divs (/ 1 2 3 4 5 6 7 8 9))))
  (let [;; MAD = 154.0ms, natural:
        ;; n->s {0 10 1 100 2 50 3 500 4 8 5 300 6 32 7 433 8 213 9 48}
        ;; MAD = 236.0ms, pathological:
        n->s {0 10 1 11 2 5 3 18 4 7 5 2 6 300 7 400 8 600 9 700}]
    (with-redefs [stats-gc-n 3]
      (profile :info :high-sigma (dotimes [n 10]  (p :sleep (Thread/sleep (n->s n)))))))

  (sampling-profile :info 0.2 :sampling-test (p :string "Hello!")))
