(ns taoensso.timbre.appenders.community.otlp
  "OpenTelemetry Protocol (OTLP) appender.
  Requires <https://github.com/steffan-westcott/clj-otel>."
  {:author "Dennis Schridde (@devurandom)"}
  (:require
   [taoensso.encore :as enc]
   [steffan-westcott.clj-otel.api.attributes :as attr])

  (:import [io.opentelemetry.api.logs LoggerProvider Severity]))

(comment (set! *warn-on-reflection* true))

(def ^:private default-severity Severity/INFO)
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

(defn- assoc-some-nx
  ([m k v      ] (if (contains? m k) m         (enc/assoc-some m k v)))
  ([m k v & kvs] (enc/reduce-kvs assoc-some-nx (enc/assoc-some m k v) kvs))
  ([m kvs]
   (reduce-kv
     (fn [m k v] (if (contains? m k) m (enc/assoc-some m k v)))
     (if (nil? m) {} m)
     kvs)))

(defn appender
  "Returns a `com.github.steffan-westcott/clj-otel-api` appender.

  For use WITH OpenTelemetry Java Agent-

    Setup a Java Agent appender, e.g.:
      (otlp/appender {:logger-provider (.getLogsBridge (GlobalOpenTelemetry/get))})

    For agent v1.x: enable the logs exporter with `OTEL_LOGS_EXPORTER=otlp`.
    For agent v2.x: the logs exporter should be enabled by default [1].

  For use WITHOUT OpenTelemetry Java Agent-

    Setup an \"autoconfiguration\" appender, e.g.:
      (otlp/appender
        {:logger-provider
         (.getSdkLoggerProvider
           (.getOpenTelemetrySdk
             (.build (AutoConfiguredOpenTelemetrySdk/builder))))})

    You'll need the following on your classpath:
      `io.opentelemetry/opentelemetry-sdk-extension-autoconfigure`,
      `io.opentelemetry/opentelemetry-exporter-otlp`.

    If you already have an instance of `GlobalOpenTelemetry` (e.g. created
    by agent), you'll need to prevent setting the newly-created SDK as the
    global default:
      (.build
        (doto (AutoConfiguredOpenTelemetrySdk/builder)
          (.setResultAsGlobal false)))

  For trace correlation Timbre config should include
    :middleware [... otlp/middleware ...].

  [1] Ref. <https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/CHANGELOG.md#version-200-2024-01-12>"
  [{:keys [^LoggerProvider logger-provider]}]
  {:enabled?   true
   :async?     true
   :min-level  nil
   :rate-limit nil
   :output-fn  :inherit
   :fn
   (fn [{:keys [^java.util.Date instant level ^String ?ns-str
                ?file ?line ?err vargs msg_ context] :as data}]

     (let [logger    (.get logger-provider ?ns-str)
           timestamp (.toInstant instant)
           severity  (get timbre->otlp-levels level default-severity)
           arg       (single-map vargs)
           ^String message (if-let [msg (:msg arg)] msg (force msg_))
           ?ex-data  (ex-data ?err)
           extra
           (assoc-some-nx context
             :file    ?file
             :line    ?line
             :ex-data ?ex-data)

           event (merge (dissoc arg :msg) extra)
           attributes (attr/->attributes event)
           lrb (.logRecordBuilder logger)]

       ;; Ref. https://javadoc.io/doc/io.opentelemetry/opentelemetry-api-logs/latest/io/opentelemetry/api/logs/LogRecordBuilder.html

       (when-let         [otel-context (get data :otel/context)]
         (.setContext lrb otel-context))

       ;; TODO Use clj-otel once it supports the logs API,
       ;; Ref. <https://github.com/steffan-westcott/clj-otel/issues/8>
       (doto lrb
         (.setAllAttributes attributes)
         (.setTimestamp     timestamp)
         (.setBody          message)
         (.setSeverity                severity)
         (.setSeverityText (.toString severity))
         (.emit))))})

(defn middleware
  "Adds `:otel/context` to log data."
  [data]
  (assoc data :otel/context
    (io.opentelemetry.context.Context/current)))
