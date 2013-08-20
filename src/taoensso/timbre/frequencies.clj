(ns taoensso.timbre.frequencies
  "DEPRECATED.
  Frequency logger for Timbre. ALPHA quality."
  {:author "Peter Taoussanis"}
  (:require [clojure.string        :as str]
            [taoensso.timbre       :as timbre]
            [taoensso.timbre.utils :as utils]))

(def ^:dynamic *fdata* "{::fname {form-value frequency}}" nil)

(defmacro with-fdata
  [level & body]
  `(if-not (timbre/logging-enabled? ~level)
     {:result (do ~@body)}
     (binding [*fdata* (atom {})]
       {:result (do ~@body) :stats @*fdata*})))

(declare format-fdata)

(defmacro log-frequencies
  "When logging is enabled, executes named body with frequency counting enabled.
  Body forms wrapped in (fspy) will have their result frequencies logged. Always
  returns body's result.

  Note that logging appenders will receive both a formatted frequencies string
  AND the raw frequency stats under a special :frequency-stats key (useful for
  queryable db logging)."
  [level name & body]
  (let [name (utils/fq-keyword name)]
    `(let [{result# :result stats# :stats} (with-fdata ~level ~@body)]
       (when stats#
         (timbre/log* print-str ~level {:frequency-stats stats#}
                      (str "Frequencies " ~name)
                      (str "\n" (format-fdata stats#))))
       result#)))

(defmacro sampling-log-frequencies
  "Like `log-frequencies`, but only enables frequency counting every
  1/`proportion` calls. Always returns body's result."
  [level proportion name & body]
  `(if-not (> ~proportion (rand))
     (do ~@body)
     (log-frequencies ~level ~name ~@body)))

(defmacro fspy
  "Frequency spy. When in the context of a *fdata* binding, records the frequency
  of each enumerated result. Always returns the body's result."
  [name & body]
  (let [name (utils/fq-keyword name)]
    `(if-not *fdata*
       (do ~@body)
       (let [name#      ~name
             result#    (try (do ~@body) (catch Throwable t# {::throwable t#}))
             throwable# (and (map? result#) (::throwable result#))]
         (swap! *fdata* #(assoc-in % [name# (or throwable# result#)]
           (inc (get-in % [name# (or throwable# result#)] 0))))
         (if throwable# (throw throwable#) result#)))))

(defmacro f [name & body] `(fspy name ~@body)) ; Alias

(defn format-fdata
  [stats]
  (let [sorted-fnames (sort (keys stats))
        sorted-fvals  (fn [form-stats] (reverse (sort-by form-stats
                                                        (keys form-stats))))]
    (str/join "\n"
     (for [fname sorted-fnames]
       (let [form-stats (stats fname)
             sorted-fvs (sorted-fvals form-stats)]
         (str fname " "
              (str/join " "
                (mapv (fn [v] (vector v (get form-stats v 0)))
                      sorted-fvs))))))))

(comment (format-fdata {:name1 {:a 10 :b 4 :c 20}
                        :name2 {33 8 12 2 false 6}}))

(comment
  (with-fdata :info
    (vec (repeatedly 20 (fn [] (fspy :rand-nth (rand-nth [:a :b :c]))))))

  (log-frequencies
   :info :my-frequencies
   (vec (repeatedly 20 (fn [] (fspy :rand-nth (rand-nth [:a :b :c])))))))
