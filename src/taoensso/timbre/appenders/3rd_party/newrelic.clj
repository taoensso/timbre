(ns taoensso.timbre.appenders.3rd-party.appenders.newrelic
  "A New Relic appender"
  {:author "Camilo Polymeris"}
  (:import (com.newrelic.api.agent NewRelic)
           (java.util HashMap)))

(defn newrelic-appender
  "New Relic appender. The Java agent must be installed and configured via newrelic.yml, see
  https://docs.newrelic.com/docs/agents/java-agent/installation/java-agent-manual-installation#h2-install-agent"
  [& _]
  {:enabled?   true
   :async?     true
   :min-level  :warn
   :rate-limit [[100 60000]]                                ;matches New Relic's own cap
   :output-fn  :inherit
   :fn         (fn [data]
                 (let [{:keys [output-fn ?err level]} data
                       params (HashMap. {"Log level" (name level)})]
                   (if ?err
                     (NewRelic/noticeError ?err params)
                     (NewRelic/noticeError (output-fn data) params))))})
