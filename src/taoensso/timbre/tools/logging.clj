(ns taoensso.timbre.tools.logging
  "Interop support for tools.logging -> Timbre."
  (:require
   [clojure.tools.logging :as ctl]
   [taoensso.encore :as enc]
   [taoensso.timbre :as timbre]))

(defmacro ^:private when-debug [& body] (when #_true false `(do ~@body)))
(defn- force-var [x] (if (var? x) (deref x) x))

(deftype TimbreLogger [logger-name config]
  ;; `logger-name` is typically ns string
  clojure.tools.logging.impl/Logger

  (enabled? [_ level]
    (when-debug (println [:tools-logging/enabled? level logger-name]))
    (timbre/may-log? level logger-name (force-var config)))

  (write! [_ level throwable message]
    (when-debug (println [:tools-logging/write! level logger-name]))
    (timbre/log!
      {:may-log? true ; Pre-filtered by `enabled?` call
       :level    level
       :msg-type :p
       :config   (force-var config)
       :loc      {:ns logger-name}
       :?err     throwable
       :vargs    [message]})))

(deftype TimbreLoggerFactory [config]
  clojure.tools.logging.impl/LoggerFactory
  (name       [_            ] "taoensso.timbre")
  (get-logger [_ logger-name] (TimbreLogger. (str logger-name) config)))

(defn use-timbre
  "Sets the root binding of `clojure.tools.logging/*logger-factory*`
  to use Timbre."
  ([      ] (use-timbre #'timbre/*config*))
  ([config]
   (alter-var-root #'clojure.tools.logging/*logger-factory*
     (fn [_] (TimbreLoggerFactory. config)))))

(comment
  (use-timbre)
  (ctl/info                    "a" "b" "c")
  (ctl/error (ex-info "ex" {}) "a" "b" "c"))
