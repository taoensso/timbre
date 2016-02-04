(ns taoensso.timbre.appenders.3rd-party.slack
    "A slack appender"
    {:author "Camilo Polymeris"}
    (:require [clj-http.client :as http]))


(defn slack-appender
  "Returns a Slack appender.
  (slack-appender
    {:webhook-url \"https://hooks.slack.com/services/...\"})"
  [{:keys [webhook-url]}]
  {:enabled?   true
   :async?     true
   :min-level  :error
   :rate-limit [[1 1000]]
   :output-fn  :inherit
   :fn         (fn [data]
                 (let [{:keys [output-fn]} data
                       message (output-fn data)]
                   (http/post webhook-url
                              {:content-type :json
                               :form-params  {:text message}})))})
