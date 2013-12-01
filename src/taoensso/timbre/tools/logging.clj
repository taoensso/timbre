(ns taoensso.timbre.tools.logging
  "clojure.tools.logging.impl/Logger implementation"
  (:require [clojure.tools.logging]
            [taoensso.timbre :as timbre]))

(deftype Logger [logger-ns]
  clojure.tools.logging.impl/Logger
  (enabled? [_ level] (timbre/logging-enabled? level))
  (write! [_ level throwable message]
    ;; tools.logging message may be a string (for `logp`/`logf` calls) or raw
    ;; argument (for `log` calls). Note that without an :args equivalent for
    ;; `write!`, the best we can do is `[message]`. This inconsistency means
    ;; that :args consumers will necessarily behave differently under
    ;; tools.logging.
    (timbre/send-to-appenders! level {} [message] logger-ns throwable
      (when (string? message) message))))

(deftype LoggerFactory []
  clojure.tools.logging.impl/LoggerFactory
  (name [_] "Timbre")
  (get-logger [_ logger-ns] (->Logger logger-ns)))

(defn use-timbre []
  (alter-var-root (var clojure.tools.logging/*logger-factory*)
                  (constantly (->LoggerFactory))))
