(ns taoensso.timbre.slf4j
  "Interop support for SLF4Jv2 -> Timbre.
  Adapted from `taoensso.telemere.slf4j`."
  {:author "Peter Taoussanis (@ptaoussanis)"}
  (:require
   [taoensso.truss  :as truss]
   [taoensso.encore :as enc]
   [taoensso.timbre :as timbre])

  (:import
   [org.slf4j Logger]
   [com.taoensso.timbre.slf4j TimbreLogger]))

;;;; Utils

(defmacro ^:private when-debug [& body] (when #_true false `(do ~@body)))

(defn- timbre-level
  "Returns Timbre level for given `org.slf4j.event.Level`."
  [^org.slf4j.event.Level level]
  (enc/case-eval  (.toInt level)
    org.slf4j.event.EventConstants/TRACE_INT :trace
    org.slf4j.event.EventConstants/DEBUG_INT :debug
    org.slf4j.event.EventConstants/INFO_INT  :info
    org.slf4j.event.EventConstants/WARN_INT  :warn
    org.slf4j.event.EventConstants/ERROR_INT :error
    (truss/ex-info! "Unexpected `org.slf4j.event.Level`"
      {:level (truss/typed-val level)})))

(comment (enc/qb 1e6 (timbre-level org.slf4j.event.Level/INFO))) ; 36.47

(defn- get-marker "Private util for tests, etc."
  ^org.slf4j.Marker [n] (org.slf4j.MarkerFactory/getMarker n))

(defn- est-marker!
  "Private util for tests, etc.
  Globally establishes (compound) `org.slf4j.Marker` with name `n` and mutates it
  (all occurences!) to have exactly the given references. Returns the (compound) marker."
  ^org.slf4j.Marker [n & refs]
  (let [m (get-marker n)]
    (enc/reduce-iterator! (fn [_ in] (.remove m in)) nil (.iterator m))
    (doseq [n refs] (.add m (get-marker n)))
    m))

(comment [(est-marker! "a1" "a2") (get-marker  "a1") (= (get-marker "a1") (get-marker "a1"))])

(def ^:private marker-names
  "Returns #{<MarkerName>}. Cached => assumes markers NOT modified after creation."
  ;; We use `BasicMarkerFactory` so:
  ;;   1. Our markers are just labels (no other content besides their name).
  ;;   2. Markers with the same name are identical (enabling caching).
  (enc/fmemoize
    (fn marker-names [marker-or-markers]
      (if (instance? org.slf4j.Marker marker-or-markers)

        ;; Single marker
        (let [^org.slf4j.Marker m marker-or-markers
              acc #{(.getName m)}]

          (if-not (.hasReferences m)
            acc
            (enc/reduce-iterator!
              (fn [acc  ^org.slf4j.Marker in]
                (if-not   (.hasReferences in)
                  (conj acc (.getName     in))
                  (into acc (marker-names in))))
              acc (.iterator m))))

        ;; Vector of markers
        (reduce
          (fn [acc in] (into acc (marker-names in)))
          #{} (truss/have vector? marker-or-markers))))))

(comment
  (let [m1 (est-marker! "M1")
        m2 (est-marker! "M1")
        cm (est-marker! "Compound" "M1" "M2")
        ms [m1 m2]]

    (enc/qb 1e6 ; [45.52 47.48 44.85]
      (marker-names m1)
      (marker-names cm)
      (marker-names ms))))

;;;; Interop fns (called by `TimbreLogger`)

(defn- allowed?
  "Called by `com.taoensso.timbre.slf4j.TimbreLogger`."
  [logger-name level]
  (when-debug (println [:slf4j/allowed? (timbre-level level) logger-name]))
  (timbre/may-log? (timbre-level level) logger-name))

(defn- normalized-log!
  [logger-name level inst error msg-pattern args marker-names kvs]
  (when-debug (println [:slf4j/normalized-log! (timbre-level level) logger-name]))
  (timbre/log!
    {:may-log? true ; Pre-filtered by `allowed?` call
     :level    (timbre-level level)
     :loc      {:ns logger-name}
     :instant  inst
     :msg-type :p
     :?err     error
     :vargs    [(org.slf4j.helpers.MessageFormatter/basicArrayFormat
                  msg-pattern args)]
     :?base-data
     (enc/assoc-some nil
       :slf4j/args         (when args (vec args))
       :slf4j/marker-names marker-names
       :slf4j/kvs          kvs
       :slf4j/context
       (when-let [hmap (org.slf4j.MDC/getCopyOfContextMap)]
         (clojure.lang.PersistentHashMap/create hmap)))})
  nil)

(defn- log!
  "Called by `com.taoensso.timbre.slf4j.TimbreLogger`."

  ;; Modern "fluent" API calls
  ([logger-name ^org.slf4j.event.LoggingEvent event]
   (let [inst        (or (when-let [ts (.getTimeStamp event)] (java.util.Date. ts)) (enc/now-dt*))
         level       (.getLevel     event)
         error       (.getThrowable event)
         msg-pattern (.getMessage   event)
         args        (when-let [args    (.getArgumentArray event)] args)
         markers     (when-let [markers (.getMarkers       event)] (marker-names (vec markers)))
         kvs         (when-let [kvps    (.getKeyValuePairs event)]
                       (reduce
                         (fn [acc ^org.slf4j.event.KeyValuePair kvp]
                           (assoc acc (.-key kvp) (.-value kvp)))
                         nil kvps))]

     (when-debug (println [:slf4j/fluent-log-call (timbre-level level) logger-name]))
     (normalized-log! logger-name level inst error msg-pattern args markers kvs)))

  ;; Legacy API calls
  ([logger-name ^org.slf4j.event.Level level error msg-pattern args marker]
   (let [marker-names (when marker (marker-names marker))]
     (when-debug (println [:slf4j/legacy-log-call (timbre-level level) logger-name]))
     (normalized-log! logger-name level (enc/now-dt*) error msg-pattern args marker-names nil))))

(comment
  (def ^org.slf4j.Logger sl (org.slf4j.LoggerFactory/getLogger "my.class"))
  (-> sl (.info "x={},y={}" "1" "2")))
