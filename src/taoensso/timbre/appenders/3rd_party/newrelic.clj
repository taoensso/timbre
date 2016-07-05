(ns taoensso.timbre.appenders.3rd-party.appenders.newrelic
  "New Relic appender. Requires an appropriate New Relic jar,
  Ref. https://goo.gl/3Nv0QX."
  {:author "Camilo Polymeris (@polymeris)"}
  (:import [com.newrelic.api.agent NewRelic]))

(defn newrelic-appender
  "New Relic appender. The Java agent must be installed and configured via
  `newrelic.yml`, Ref. https://goo.gl/hRCGFd."
  []
  {:enabled?   true
   :async?     true
   :min-level  :error ; New Relic API only supports error-level logging
   :rate-limit [[100 60000]] ; Matches New Relic's own cap
   :output-fn  :inherit
   :fn
   (fn [data]
     (let [{:keys [output_ ?err level]} data
           params (java.util.HashMap.
                    {"Log level" (name level)
                     ;; TODO Add other stuff later?
                     })]

       (if-let [err ?err]
         (NewRelic/noticeError err            params)
         (NewRelic/noticeError (force output) params))))})
