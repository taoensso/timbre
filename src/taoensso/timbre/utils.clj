(ns taoensso.timbre.utils
  {:author "Peter Taoussanis"}
  (:require [clojure.tools.macro :as macro]))

(defmacro defonce*
  "Like `clojure.core/defonce` but supports optional docstring and attributes
  map for name symbol."
  {:arglists '([name expr])}
  [name & sigs]
  (let [[name [expr]] (macro/name-with-attributes name sigs)]
    `(clojure.core/defonce ~name ~expr)))

(defn memoize-ttl "Low-overhead, common-case `memoize*`."
  [ttl-ms f]
  (let [cache (atom {})]
    (fn [& args]
      (when (<= (rand) 0.001) ; GC
        (let [instant (System/currentTimeMillis)]
          (swap! cache
            (fn [m] (reduce-kv (fn [m* k [dv udt :as cv]]
                                (if (> (- instant udt) ttl-ms) m*
                                    (assoc m* k cv))) {} m)))))
      (let [[dv udt] (@cache args)]
        (if (and dv (< (- (System/currentTimeMillis) udt) ttl-ms)) @dv
          (locking cache ; For thread racing
            (let [[dv udt] (@cache args)] ; Retry after lock acquisition!
              (if (and dv (< (- (System/currentTimeMillis) udt) ttl-ms)) @dv
                (let [dv (delay (apply f args))
                      cv [dv (System/currentTimeMillis)]]
                  (swap! cache assoc args cv)
                  @dv)))))))))

(defn rate-limiter
  "Returns a `(fn [& [id]])` that returns either `nil` (limit okay) or number of
  msecs until next rate limit window (rate limited)."
  [ncalls-limit window-ms]
  (let [state (atom [nil {}])] ; [<pull> {<id> {[udt-window-start ncalls]}}]
    (fn [& [id]]

      (when (<= (rand) 0.001) ; GC
        (let [instant (System/currentTimeMillis)]
          (swap! state
            (fn [[_ m]]
              [nil (reduce-kv
                    (fn [m* id [udt-window-start ncalls]]
                      (if (> (- instant udt-window-start) window-ms) m*
                          (assoc m* id [udt-window-start ncalls]))) {} m)]))))

      (->
       (let [instant (System/currentTimeMillis)]
         (swap! state
           (fn [[_ m]]
             (if-let [[udt-window-start ncalls] (m id)]
               (if (> (- instant udt-window-start) window-ms)
                 [nil (assoc m id [instant 1])]
                 (if (< ncalls ncalls-limit)
                   [nil (assoc m id [udt-window-start (inc ncalls)])]
                   [(- (+ udt-window-start window-ms) instant) m]))
               [nil (assoc m id [instant 1])]))))
       (nth 0)))))

(comment
  (def rl (rate-limit 10 10000))
  (repeatedly 10 #(rl (rand-nth [:a :b :c])))
  (rl :a)
  (rl :b)
  (rl :c))

(defn merge-deep-with ; From clojure.contrib.map-utils
  "Like `merge-with` but merges maps recursively, applying the given fn
  only when there's a non-map at a particular level.

  (merge-deep-with + {:a {:b {:c 1 :d {:x 1 :y 2}} :e 3} :f 4}
                     {:a {:b {:c 2 :d {:z 9} :z 3} :e 100}})
  => {:a {:b {:z 3, :c 3, :d {:z 9, :x 1, :y 2}}, :e 103}, :f 4}"
  [f & maps]
  (apply
   (fn m [& maps]
     (if (every? map? maps)
       (apply merge-with m maps)
       (apply f maps)))
   maps))

(def merge-deep (partial merge-deep-with (fn [x y] y)))

(comment (merge-deep {:a {:b {:c {:d :D :e :E}}}}
                     {:a {:b {:g :G :c {:c {:f :F}}}}}))

(defn round-to "Rounds argument to given number of decimal places."
  [places x]
  (if (zero? places)
    (Math/round (double x))
    (let [modifier (Math/pow 10.0 places)]
      (/ (Math/round (* x modifier)) modifier))))

(comment (round-to 0 10)
         (round-to 2 10.123))

(defmacro fq-keyword "Returns namespaced keyword for given name."
  [name]
  `(if (and (keyword? ~name) (namespace ~name)) ~name
     (keyword (str ~*ns*) (clojure.core/name ~name))))

(comment (map #(fq-keyword %) ["foo" :foo :foo/bar]))

(defmacro sometimes "Executes body with probability e/o [0,1]."
  [probability & body]
  `(do (assert (<= 0 ~probability 1) "Probability: 0 <= p <= 1")
       (when (< (rand) ~probability) ~@body)))
