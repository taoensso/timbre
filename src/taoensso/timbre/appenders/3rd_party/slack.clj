(ns taoensso.timbre.appenders.3rd-party.slack
  "Requires https://github.com/julienXX/clj-slack"
  {:author "Simon Belak (@sbelak)"}
  (:require
   [taoensso.timbre :as timbre]
   [taoensso.encore :as enc]
   [clj-slack.chat  :as slack.chat]
   [clj-slack.core  :as slack]))

(defn slack-appender
  "Returns Slack appender. Required params:
    `token`   - Slack API token. See: Browse apps > Custom Integrations > Bots
    `channel` - Channel ID

  Optional params: same as `clj-slack.chat/post-message` `optionals`,
  Ref. http://julienblanchard.com/clj-slack/clj-slack.chat.html"
  [& [opts]]
  (let [{:keys [token channel]} opts
        conn {:api-url "https://slack.com/api"
              :token token}]
    {:enabled?   true
     :async?     true
     :min-level  nil
     :rate-limit [[1 (enc/ms :secs 1)]]
     :output-fn :inherit
     :fn
     (fn [data]
       (let [{:keys [output_]} data]
         (slack.chat/post-message conn channel (force output_)
           (dissoc opts :token :channel))))}))
