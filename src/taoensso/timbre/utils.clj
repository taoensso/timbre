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
        (let [now (System/currentTimeMillis)]
          (->> @cache
               (reduce-kv (fn [exp-ks k [dv ms :as cv]]
                            (if (< (- now ms) ttl-ms) exp-ks
                                (conj exp-ks k))) [])
               (apply swap! cache dissoc))))
      (let [[dv ms] (@cache args)]
        (if (and dv (< (- (System/currentTimeMillis) ms) ttl-ms))
          @dv
          (locking cache ; For thread racing
            (let [[dv ms] (@cache args)] ; Retry after lock acquisition!
              (if (and dv (< (- (System/currentTimeMillis) ms) ttl-ms))
                @dv
                (let [dv (delay (apply f args))
                      cv [dv (System/currentTimeMillis)]]
                  (swap! cache assoc args cv)
                  @dv)))))))))

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

(defn round-to
  "Rounds argument to given number of decimal places."
  [places x]
  (if (zero? places)
    (Math/round (double x))
    (let [modifier (Math/pow 10.0 places)]
      (/ (Math/round (* x modifier)) modifier))))

(comment (round-to 0 10)
         (round-to 2 10.123))

(defmacro fq-keyword
  "Returns namespaced keyword for given name."
  [name]
  `(if (and (keyword? ~name) (namespace ~name))
     ~name
     (keyword (str ~*ns*) (clojure.core/name ~name))))

(comment (map #(fq-keyword %) ["foo" :foo :foo/bar]))
