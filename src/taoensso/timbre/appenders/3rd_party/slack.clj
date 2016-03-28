(ns taoensso.timbre.appenders.3rd-party.slack
  {:author "Simon Belak (@sbelak)"}
  (:require (taoensso [timbre :as timbre]
                      [encore :as encore])
            (clj-slack [chat :as slack.chat]              
                       [core :as slack])))

(defn slack-appender
  "Return Slack appender.
  Required params: 
  `token` - Slack API token. See: Browse apps > Custom Integrations > Bots
  `channel` - Channel ID. 

  Optional params: same as clj-slack.chat/post-message `optionals`. See: http://julienblanchard.com/clj-slack/clj-slack.chat.html"
  [& [opts]]
  (let [{:keys [token channel]} opts
        conn {:api-url "https://slack.com/api"
              :token token}]
    {:enabled?   true 
     :async?     true 
     :min-level  nil
     :rate-limit [[1 (encore/ms :secs 1)]]
     :output-fn :inherit 
     :fn
     (fn [data]
       (let [{:keys [output-fn]} data]
         (slack.chat/post-message conn channel (output-fn data)
                                  (dissoc opts :token :channel))))}))
