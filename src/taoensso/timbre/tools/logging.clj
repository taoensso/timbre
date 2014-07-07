(ns taoensso.timbre.tools.logging
  "clojure.tools.logging.impl/Logger implementation.

  Limitations:
    * No support for zero-overhead compile-time logging levels (`enabled?`
      called as a fn).
    * No support for ns filtering (`write!` called as a fn and w/o compile-time
      ns info).
    * Limited raw `:args` support  (`write!` called w/o raw args)."
  (:require [clojure.tools.logging]
            [taoensso.timbre :as timbre]))

(deftype Logger [logger-ns]
  clojure.tools.logging.impl/Logger
  (enabled? [_ level] (timbre/logging-enabled? level))
  (write!   [_ level throwable message]
    ;; tools.logging message may be a string (for `logp`/`logf` calls) or
    ;; single raw argument (for `log` calls). The best we can do for :args is
    ;; therefore `[message]`:
    (timbre/send-to-appenders! level {} [message] logger-ns throwable
      (when (string? message)
        (delay ; Mimic Timbre's lazy message creation
          message)))))

(deftype LoggerFactory []
  clojure.tools.logging.impl/LoggerFactory
  (name [_] "Timbre")
  (get-logger [_ logger-ns] (->Logger logger-ns)))

(defn use-timbre []
  (alter-var-root (var clojure.tools.logging/*logger-factory*)
                  (constantly (->LoggerFactory))))
