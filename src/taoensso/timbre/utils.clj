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

(defn memoize-ttl
  "Like `memoize` but invalidates the cache for a set of arguments after TTL
  msecs has elapsed."
  [ttl f]
  (let [cache (atom {})]
    (fn [& args]
      (let [{:keys [time-cached d-result]} (@cache args)
            now (System/currentTimeMillis)]

        (if (and time-cached (< (- now time-cached) ttl))
          @d-result
          (let [d-result (delay (apply f args))]
            (swap! cache assoc args {:time-cached now :d-result d-result})
            @d-result))))))

(defn deep-merge-with ; From clojure.contrib.map-utils
  "Like `merge-with` but merges maps recursively, applying the given fn
  only when there's a non-map at a particular level.

  (deepmerge-with + {:a {:b {:c 1 :d {:x 1 :y 2}} :e 3} :f 4}
                    {:a {:b {:c 2 :d {:z 9} :z 3} :e 100}})
  => {:a {:b {:z 3, :c 3, :d {:z 9, :x 1, :y 2}}, :e 103}, :f 4}"
  [f & maps]
  (apply
   (fn m [& maps]
     (if (every? map? maps)
       (apply merge-with m maps)
       (apply f maps)))
   maps))

(def deep-merge (partial deep-merge-with (fn [x y] y)))

(comment (deep-merge {:a {:b {:c {:d :D :e :E}}}}
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