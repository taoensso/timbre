(ns taoensso.timbre.appenders.irc
  "IRC appender for irclj.
  Ref: https://github.com/flatland/irclj."
  {:author "Emlyn Corrin"}
  (:require [clojure.string  :as str]
            [irclj.core      :as irclj]
            [taoensso.timbre :as timbre]))

(def conn (atom nil))

(defn connect [{:keys [host port pass nick user name chan]
                :or {:port 6667}}]
  (let [conn (irclj/connect host port nick
                            :username  user
                            :real-name name
                            :pass      pass
                            :callbacks {})]
    (irclj/join conn chan)
    conn))

(defn ensure-conn [conf]
  (swap! conn #(or % (connect conf))))

(defn send-message [{:keys [message prefix chan] :as config}]
  (let [conn (ensure-conn config)
        lines (str/split message #"\n")]
    (irclj/message conn chan prefix (first lines))
    (doseq [line (rest lines)]
      (irclj/message conn chan ">" line))))

(defn appender-fn [{:keys [ap-config prefix message]}]
  (when-let [irc-config (:irc ap-config)]
    (send-message
     (assoc irc-config
       :prefix (if-let [prefix-fn (:prefix-fn irc-config)]
                 (prefix-fn prefix)
                 prefix)
       :message message))))

(def irc-appender
  {:doc (str "Sends IRC messages using irclj.\n"
             "Needs :irc config map in :shared-appender-config, e.g.:
             {:host \"irc.example.org\" :port 6667 :nick \"logger\"
              :name \"My Logger\" :chan \"#logs\"")
   :min-level :info :enabled? true :async? false
   :max-message-per-msecs nil ; no rate limit by default
   :fn appender-fn})
