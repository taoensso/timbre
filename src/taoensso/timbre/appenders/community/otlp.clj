(ns taoensso.timbre.appenders.community.otlp
  "OpenTelemetry Protocol (OTLP) appender.
  Requires com.github.steffan-westcott/clj-otel-api.

  # With Java Agent

  Activate an appender configured by the OpenTelemetry Java Agent:
  ```clj
  (let [logger-provider (.getLogsBridge (GlobalOpenTelemetry/get))
        appender (otlp/appender logger-provider)]
    (timbre/merge-config! {:appenders {:otlp appender}}))
  ```

  Note: When relying on the OpenTelemetry Java Agent 1.x, you need
  to explicitly enable the logs exporter with `OTEL_LOGS_EXPORTER=otlp`.
  This will become the default with the release of Java Agent 2.0, cf.
  * https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/CHANGELOG.md#version-1270-2023-06-14
  * https://github.com/open-telemetry/opentelemetry-java-instrumentation/pull/8647

  # Without Java Agent

  If you want autoconfiguration without the Java Agent, you also need
  io.opentelemetry/opentelemetry-sdk-extension-autoconfigure and
  io.opentelemetry/opentelemetry-exporter-otlp on the classpath.

  Create an autoconfigured appender and activate it:
  ```clj
  (let [logger-provider (.getSdkLoggerProvider
                          (.getOpenTelemetrySdk
                            (.build
                              (AutoConfiguredOpenTelemetrySdk/builder))))
        appender (otlp/appender logger-provider)]
    (timbre/merge-config! {:appenders {:otlp appender}}))
  ```

  If you already have an instance of `GlobalOpenTelemetry`, e.g. created
  by the OpenTelemetry Java Agent, you need to prevent setting the newly
  created SDK as the global default:
  ```clj
  (.build
    (doto (AutoConfiguredOpenTelemetrySdk/builder)
      (.setResultAsGlobal false)))
  ```"
  {:author "Dennis Schridde (@devurandom)"}
  (:require
    [steffan-westcott.clj-otel.api.attributes :as attr]
    [taoensso.encore :as enc])
  (:import
    (io.opentelemetry.api.logs LoggerProvider Severity)
    (java.util Date)))

(set! *warn-on-reflection* true)

(def ^:private default-severity
  Severity/INFO)

(def ^:private timbre->otlp-levels
  {:trace  Severity/TRACE
   :debug  Severity/DEBUG
   :info   Severity/INFO
   :warn   Severity/WARN
   :error  Severity/ERROR
   :fatal  Severity/FATAL
   :report default-severity})

(defn- single-map [xs]
  (let [[x & r] xs]
    (when (and (map? x) (not r))
      x)))

; TODO: taoensso.encore seems to be missing this:
(defn- assoc-some-nx
  ([m k v] (if (contains? m k) m (enc/assoc-some m k v)))
  ([m k v & kvs] (enc/reduce-kvs assoc-some-nx (enc/assoc-some m k v) kvs))
  ([m kvs]
   (reduce-kv
     (fn [m k v] (if (contains? m k) m (enc/assoc-some m k v)))
     (if (nil? m) {} m)
     kvs)))

(defn appender
  [^LoggerProvider logger-provider]
  {:enabled?   true
   :async?     true
   :min-level  nil
   :rate-limit nil
   :output-fn  :inherit
   :fn
   (fn [{:keys [^Date instant level ^String ?ns-str ?file ?line ?err vargs msg_ context]}]
     (let [actual-instant (.toInstant instant)
           severity       (get timbre->otlp-levels level default-severity)
           arg            (single-map vargs)
           message        (if-let [msg (:msg arg)]
                            msg
                            (force msg_))
           ?ex-data       (ex-data ?err)
           extra          (assoc-some-nx context
                                         :file ?file
                                         :line ?line
                                         :ex-data ?ex-data)
           event          (merge (dissoc arg :msg)
                                 extra)
           attributes     (attr/->attributes event)
           ; TODO: Use clj-otel once it supports the logs API.
           ;  cf. https://github.com/steffan-westcott/clj-otel/issues/8
           logger         (.get logger-provider ?ns-str)]
       (.emit
         (doto (.logRecordBuilder logger)
           (.setTimestamp actual-instant)
           (.setSeverity severity)
           (.setSeverityText (.toString severity))
           (.setAllAttributes attributes)
           (.setBody message)))))})
