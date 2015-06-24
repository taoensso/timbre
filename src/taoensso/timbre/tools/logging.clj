(ns taoensso.timbre.tools.logging
  "clojure.tools.logging.impl/Logger implementation.

  The tools.logging API has some significant limits that native Timbre does not.
  Only use Timbre through tools.logging if you absolutely must (e.g. you're
  working with a legacy codebase)."
  (:require [clojure.tools.logging]
            [taoensso.timbre :as timbre]))

(deftype Logger [logger-ns]
  clojure.tools.logging.impl/Logger

  ;; Limitations: no support for explicit config, or ns filtering
  (enabled? [_ level] (timbre/log? level))

  ;; Limitations inline
  (write! [_ level throwable message]
    (let [config   timbre/*config* ; No support for explicit config
          ?ns-str  nil      ; No support
          ?file    nil      ; ''
          ?line    nil      ; ''
          msg-type :p   ; No support for pre-msg raw args
          ]
      (timbre/log1-fn config level ?ns-str ?file ?line msg-type [message] nil))))

(deftype LoggerFactory []
  clojure.tools.logging.impl/LoggerFactory
  (name [_] "Timbre")
  (get-logger [_ logger-ns] (Logger. logger-ns)))

(defn use-timbre []
  (alter-var-root (var clojure.tools.logging/*logger-factory*)
    (constantly (LoggerFactory.))))
