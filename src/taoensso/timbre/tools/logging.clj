(ns taoensso.timbre.tools.logging
  "`clojure.tools.logging.impl/Logger` implementation.

  Please note that the tools.logging API has some significant limits
  that native Timbre does not. Would strongly recommend against using
  Timbre through tools.logging unless you absolutely must (e.g. you're
  working with a legacy codebase)."

  (:require [clojure.tools.logging]
            [taoensso.encore :as enc]
            [taoensso.timbre :as timbre]))

(defn- force-var "To support dynamic vars, etc."
  [x] (if (var? x) (deref x) x))

(deftype Logger [logger-ns-str timbre-config]
  clojure.tools.logging.impl/Logger

  (enabled? [_ level]
    ;; No support for per-call config
    (timbre/may-log? level logger-ns-str
      (force-var timbre-config)))

  (write! [_ level throwable message]
    (timbre/log! level :p
      [message] ; No support for pre-msg raw args
      {:config  (force-var timbre-config) ; No support for per-call config
       :?ns-str logger-ns-str
       :?file   nil ; No support
       :?line   nil ; ''
       :?err    throwable})))

(deftype LoggerFactory [get-logger-fn]
  clojure.tools.logging.impl/LoggerFactory
  (name [_] "Timbre")
  (get-logger [_ logger-ns] (get-logger-fn logger-ns)))

(defn use-timbre
  "Sets the root binding of `clojure.tools.logging/*logger-factory*`
  to use Timbre."
  ([             ] (use-timbre #'timbre/*config*))
  ([timbre-config]
   (alter-var-root #'clojure.tools.logging/*logger-factory*
     (fn [_]
       (LoggerFactory.
         (enc/memoize_
           (fn [logger-ns] (Logger. (str logger-ns) timbre-config))))))))
