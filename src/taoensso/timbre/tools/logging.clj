(ns taoensso.timbre.tools.logging
  "clojure.tools.logging.impl/Logger implementation.

  Please note that the tools.logging API has some significant limits
  that native Timbre does not. Would strongly recommend against using
  Timbre through tools.logging unless you absolutely must (e.g. you're
  working with a legacy codebase)."

  (:require clojure.tools.logging
            [taoensso.timbre :as timbre]))

(deftype Logger [logger-ns config]
  clojure.tools.logging.impl/Logger

  (enabled? [_ level]
    (timbre/log? level (str logger-ns) config))

  (write! [_ level throwable message]
    (timbre/log! level :p
      [message] ; No support for pre-msg raw args
      {:config  config
       :?ns-str (str logger-ns)
       :?file   nil ; ''
       :?line   nil ; ''
       :?err    throwable})))

(deftype LoggerFactory [cache]
  clojure.tools.logging.impl/LoggerFactory
  (name [_] "Timbre")
  (get-logger [_ logger-ns]
    (or (get @cache logger-ns)
        (let [logger (Logger. logger-ns timbre/*config*)]
          (swap! cache assoc logger-ns logger)
          logger))))

(defn use-timbre []
  (alter-var-root (var clojure.tools.logging/*logger-factory*)
    (constantly (LoggerFactory. (atom {})))))
