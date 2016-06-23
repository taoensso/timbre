(ns taoensso.timbre.profiling
  "Simple logging profiler for Timbre. Highly optimized; supports
  sampled profiling in production."
  {:author "Peter Taoussanis (@ptaoussanis)"}
  (:require [taoensso.encore :as enc :refer (qb)]
            [taoensso.timbre :as timbre])
  (:import  [java.util HashMap LinkedList]))

;;;; TODO
;; * Support for explicit `config` args?
;; * Consider a .cljx port? Any demand for this kind of cljs profiling?
;; * Support for real level+ns based elision (zero *pdata* check cost, etc.)?
;;   - E.g. perhaps `p` forms could take a logging level?

;;;; Utils

;; Note that we only support *compile-time* ids
(defn- qualified-kw [ns id] (if (enc/qualified-keyword? id) id (keyword (str ns) (name id))))
(comment (qualified-kw *ns* "foo"))

(def ^:private elide-profiling?
  "Completely elide all profiling? In particular, eliminates proxy checks.
  TODO Temp, until we have a better elision strategy."
  (enc/read-sys-val "TIMBRE_ELIDE_PROFILING"))

;;;;

;; We establish one of these (thread local) to enable profiling.
(deftype PData [m-times m-stats]) ; [?{<id> <LinkedList>} ?{<id> <interim-stats>}]
(defmacro -new-pdata [] `(PData. nil nil))

;; This is substantially faster than a ^:dynamic volatile:
(def -pdata-proxy
  (let [^ThreadLocal proxy (proxy [ThreadLocal] [])]
    (fn
      ([]        (.get proxy)) ; nnil iff profiling enabled
      ([new-val] (.set proxy new-val) new-val))))

(declare ^:private times->stats)
(defn -capture-time!

  ([id t-elapsed] ; Just for dev/debugging
   (-capture-time! (-pdata-proxy) id t-elapsed))

  ([^PData pdata id t-elapsed] ; Common case
   (let [m-times (.m-times pdata)
         m-stats (.m-stats pdata)]

     (if-let [^LinkedList times (get m-times id)]
       (if (== (.size times) #_20 2000000) ; Rare in real-world use
         ;; Compact: merge interim stats to help prevent OOMs
         (let [stats (times->stats times (get m-stats id))
               times (LinkedList.)]
           (.add times t-elapsed)
           (-pdata-proxy
            (PData. (assoc m-times id times)
                    (assoc m-stats id stats))))

         ;; Common case
         (.add times t-elapsed))

       ;; Init case
       (let [times (LinkedList.)]
         (.add times t-elapsed)
         (-pdata-proxy (PData. (assoc m-times id times)
                                      m-stats))))

     nil)))

;; Just for dev/debugging
(defmacro -with-pdata [& body]
  `(try
     (-pdata-proxy (-new-pdata))
     (do ~@body)
     (finally (-pdata-proxy nil))))

(comment
  (-with-pdata (qb 1e6 (-capture-time! :foo 1000))) ; 70.84
  (-with-pdata
   (dotimes [_ 20] (-capture-time! :foo 100000))
   (.m-times ^PData (-pdata-proxy))))

(defn- times->stats [^LinkedList times ?base-stats]
  (let [ntimes       (.size   times)
        times        (into [] times) ; Faster to reduce
        ts-count     (if (zero? ntimes) 1 ntimes)
        ts-time      (reduce (fn [^long acc ^long in] (+ acc in)) times)
        ts-mean      (/ (double ts-time) (double ts-count))
        ts-mad-sum   (reduce (fn [^long acc ^long in] (+ acc (Math/abs (- in ts-mean)))) 0   times)
        ts-min       (reduce (fn [^long acc ^long in] (if (< in acc) in acc)) Long/MAX_VALUE times)
        ts-max       (reduce (fn [^long acc ^long in] (if (> in acc) in acc)) 0              times)]

    (if-let [stats ?base-stats] ; Merge over previous stats
      (let [s-count   (+ ^long (get stats :count) ts-count)
            s-time    (+ ^long (get stats :time)  ts-time)
            s-mean    (/ (double s-time) (double s-count))
            s-mad-sum (+ ^long (get stats :mad-sum) ts-mad-sum)
            s-mad     (/ (double s-mad-sum) (double s-count))
            s0-min    (get stats :min)
            s0-max    (get stats :max)]

        ;; Batched "online" MAD calculation here is >= the standard
        ;; Knuth/Welford method, Ref. http://goo.gl/QLSfOc,
        ;;                            http://goo.gl/mx5eSK.

        {:count   s-count
         :time    s-time
         :mean    s-mean
         :mad-sum s-mad-sum
         :mad     s-mad
         :min     (if (< ^long s0-min ^long ts-min) s0-min ts-min)
         :max     (if (> ^long s0-max ^long ts-max) s0-max ts-max)})

      {:count   ts-count
       :time    ts-time
       :mean    ts-mean
       :mad-sum ts-mad-sum
       :mad     (/ (double ts-mad-sum) (double ts-count))
       :min     ts-min
       :max     ts-max})))

(defn -compile-final-stats! "Returns {<id> <stats>}"
  [clock-time]
  (let [^PData pdata (-pdata-proxy)
        m-times (.m-times pdata)
        m-stats (.m-stats pdata)]
    (reduce-kv
     (fn [m id times]
       (assoc m id (times->stats times (get m-stats id))))
     {:clock-time clock-time} m-times)))

(comment
  (qb 1e5
    (-with-pdata
     (-capture-time! :foo 10)
     (-capture-time! :foo 20)
     (-capture-time! :foo 30)
     (-capture-time! :foo 10)
     (-compile-final-stats! 0))) ; 121.83
  )

;;;;

(defn- perc [n d] (Math/round (/ (double n) (double d) 0.01)))
(comment (perc 14 24))

(defn- ft [nanosecs]
  (let [ns (long nanosecs)] ; Truncate any fractionals
    (cond
      (>= ns 1000000000) (str (enc/round2 (/ ns 1000000000))  "s") ; 1e9
      (>= ns    1000000) (str (enc/round2 (/ ns    1000000)) "ms") ; 1e6
      (>= ns       1000) (str (enc/round2 (/ ns       1000)) "μs") ; 1e3
      :else              (str                ns              "ns"))))

(defn -format-stats
  ([stats           ] (-format-stats stats :time))
  ([stats sort-field]
   (let [clock-time      (get    stats :clock-time)
         stats           (dissoc stats :clock-time)
         ^long accounted (reduce-kv (fn [^long acc k v] (+ acc ^long (:time v))) 0 stats)

         ^long max-id-width
         (reduce-kv
          (fn [^long acc k v]
            (let [c (count (str k))]
              (if (> c acc) c acc)))
          #=(count "Accounted Time")
          stats)

         pattern   (str "%" max-id-width "s %,11d %9s %10s %9s %9s %7d %1s%n")
         s-pattern (str "%" max-id-width  "s %11s %9s %10s %9s %9s %7s %1s%n")

         sorted-stat-ids
         (sort-by
          (fn [id] (get-in stats [id sort-field]))
          enc/rcompare
          (keys stats))]

     (with-out-str
       (printf s-pattern "Id" "nCalls" "Min" "Max" "MAD" "Mean" "Time%" "Time")
       (enc/run!
        (fn [id]
          (let [{:keys [count min max mean mad time]} (get stats id)]
            (printf pattern id count (ft min) (ft max) (ft mad)
                    (ft mean) (perc time clock-time) (ft time))))
        sorted-stat-ids)

       (printf s-pattern "Clock Time"     "" "" "" "" "" 100 (ft clock-time))
       (printf s-pattern "Accounted Time" "" "" "" "" ""
               (perc accounted clock-time) (ft accounted))))))

;;;;

(defmacro pspy
  "Profile spy. When thread-local profiling is enabled, records
  execution time of named body. Always returns the body's result."
  [id & body]
  (let [id (qualified-kw *ns* id)]
    (if elide-profiling?
      `(do ~@body)
      `(let [pdata# (-pdata-proxy)]
         (if pdata#
           (let [t0# (System/nanoTime)
                 result# (do ~@body)]
             (-capture-time! pdata# ~id (- (System/nanoTime) t0#))
             result#)
           (do ~@body))))))

(defmacro p [id & body] `(pspy ~id ~@body)) ; Alias

(comment (macroexpand '(p :foo (+ 4 2))))

(defmacro profile
  "When logging is enabled, executes named body with thread-local profiling
  enabled and logs profiling stats. Always returns body's result."
  [level id & body]
  (let [id (qualified-kw *ns* id)]
    (if elide-profiling?
      `(do ~@body)
      `(if (timbre/log? ~level ~(str *ns*)) ; Runtime check
         (try
           (-pdata-proxy (-new-pdata))
           (let [t0# (System/nanoTime)
                 result# (do ~@body)
                 stats# (-compile-final-stats! (- (System/nanoTime) t0#))
                 stats-str# (-format-stats stats#)]
             (timbre/log! ~level :p
               ["Profiling: " ~id "\n" stats-str#]
               {:?base-data
                {:profile-stats     stats#
                 :profile-stats-str stats-str#}})
             result#)
           (finally (-pdata-proxy nil)))
         (do ~@body)))))

(defmacro sampling-profile
  "Like `profile`, but only enables profiling with given probability."
  [level probability id & body]
  (assert (<= 0 probability 1) "Probability: 0<=p<=1")
  (if elide-profiling?
    `(do ~@body)
    `(if (< (rand) ~probability)
       (profile ~level ~id ~@body)
       (do                 ~@body))))

;;;; fnp stuff

(defn -fn-sigs [fn-name sigs]
  (let [single-arity? (vector? (first sigs))
        sigs    (if single-arity? (list sigs) sigs)
        get-id  (if single-arity?
                  (fn [fn-name _params]      (name fn-name))
                  (fn [fn-name  params] (str (name fn-name) \_ (count params))))
        new-sigs
        (map
          (fn [[params & others]]
            (let [has-prepost-map?      (and (map? (first others)) (next others))
                  [?prepost-map & body] (if has-prepost-map? others (cons nil others))]
              (if ?prepost-map
                `(~params ~?prepost-map (pspy ~(get-id fn-name params) ~@body))
                `(~params               (pspy ~(get-id fn-name params) ~@body)))))
          sigs)]
    new-sigs))

(defmacro fnp "Like `fn` but wraps fn bodies with `p` macro."
  {:arglists '([name?  [params*] prepost-map? body]
               [name? ([params*] prepost-map? body)+])}
  [& sigs]
  (let [[?fn-name sigs] (if (symbol? (first sigs)) [(first sigs) (next sigs)] [nil sigs])
        new-sigs        (-fn-sigs (or ?fn-name 'anonymous-fn) sigs)]
    (if ?fn-name
      `(fn ~?fn-name ~@new-sigs)
      `(fn           ~@new-sigs))))

(comment
  (-fn-sigs "foo"      '([x]            (* x x)))
  (macroexpand '(fnp     [x]            (* x x)))
  (macroexpand '(fn       [x]            (* x x)))
  (macroexpand '(fnp bob [x] {:pre [x]} (* x x)))
  (macroexpand '(fn       [x] {:pre [x]} (* x x))))

(defmacro defnp "Like `defn` but wraps fn bodies with `p` macro."
  {:arglists
   '([name doc-string? attr-map?  [params*] prepost-map? body]
     [name doc-string? attr-map? ([params*] prepost-map? body)+ attr-map?])}
  [& sigs]
  (let [[fn-name sigs] (enc/name-with-attrs (first sigs) (next sigs))
        new-sigs       (-fn-sigs fn-name sigs)]
    `(defn ~fn-name ~@new-sigs)))

(comment
  (defnp foo "Docstring"                [x]   (* x x))
  (macroexpand '(defnp foo "Docstring"  [x]   (* x x)))
  (macroexpand '(defn  foo "Docstring"  [x]   (* x x)))
  (macroexpand '(defnp foo "Docstring" ([x]   (* x x))
                                       ([x y] (* x y))))
  (profile :info :defnp-test (foo 5)))

;;;; Deprecated

(def pspy* "Deprecated" (fn [_id f] (pspy :pspy*/no-id (f))))
(def p*    "Deprecated" pspy*)

(comment (profile :info :pspy* (pspy* :foo (fn [] (Thread/sleep 100)))))

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

  (p :hello "Hello, this is a result") ; Falls through (no thread context)

  (defnp my-fn
    []
    (let [nums (vec (range 1000))]
      (+ (p :fast-sleep (Thread/sleep 1) 10)
         (p :slow-sleep (Thread/sleep 2) 32)
         (p :add  (reduce + nums))
         (p :sub  (reduce - nums))
         (p :mult (reduce * nums))
         (p :div  (reduce / nums)))))

  (profile :info :Arithmetic (dotimes [n 100] (my-fn)))
  (profile :info :high-n     (dotimes [n 1e5] (p :nil nil))) ; ~19ms
  (profile :info :high-n     (dotimes [n 1e6] (p :nil nil))) ; ~116ms
  (sampling-profile :info 0.5 :sampling-test (p :string "Hello!")))
