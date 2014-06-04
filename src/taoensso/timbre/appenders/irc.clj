(ns taoensso.timbre.appenders.irc
  "IRC appender. Depends on https://github.com/flatland/irclj."
  {:author "Emlyn Corrin"}
  (:require [clojure.string  :as str]
            [irclj.core      :as irc]
            [taoensso.timbre :as timbre]))

(def conn (atom nil))

(defn connect [{:keys [host port pass nick user name chan]
                :or   {port 6667}}]
  (let [conn (irc/connect host port nick
                          :username  user
                          :real-name name
                          :pass      pass
                          :callbacks {})]
    (irc/join conn chan)
    conn))

(defn ensure-conn [conf]
  (or @conn
      (reset! conn (connect conf))))

(defn send-message [{:keys [prefix throwable message chan] :as config}]
  (let [conn (ensure-conn config)
        lines (-> (str message (timbre/stacktrace throwable "\n"))
                  (str/split #"\n"))]
    (irc/message conn chan prefix (first lines))
    (doseq [line (rest lines)]
      (irc/message conn chan ">" line))))

(defn make-irc-appender
  "Sends IRC messages using irc.
  Needs :irc config map in :shared-appender-config, e.g.:
   {:host \"irc.example.org\" :port 6667 :nick \"logger\"
    :name \"My Logger\" :chan \"#logs\"}"
  [& [appender-opts {:keys [irc-config]}]]
  (let [default-appender-opts
        {:enabled?  true
         :min-level :info
         :async?     true
         :prefix-fn (fn [args] (-> args :level name str/upper-case))}]
    (merge default-appender-opts appender-opts
           {:doc (:doc (meta #'make-irc-appender))
            :fn
            (fn [{:keys [ap-config prefix throwable message]}]
              (when-let [irc-config (or irc-config (:irc ap-config))]
                (send-message
                 (assoc irc-config
                   :prefix    prefix
                   :throwable throwable
                   :message   message))))})))

(def irc-appender
  (make-irc-appender))
