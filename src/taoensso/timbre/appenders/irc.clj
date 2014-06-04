(ns taoensso.timbre.appenders.irc
  "IRC appender. Depends on https://github.com/flatland/irclj."
  {:author "Emlyn Corrin"}
  (:require [clojure.string  :as str]
            [irclj.core      :as irc]
            [taoensso.timbre :as timbre]))

(def default-appender-opts
  {:enabled?  true
   :min-level :info
   :async?     true
   :prefix-fn (fn [args] (-> args :level name str/upper-case))})

(defn- connect [{:keys [host port pass nick user name chan]
                :or   {port 6667}}]
  (let [conn (irc/connect host port nick
                          :username  user
                          :real-name name
                          :pass      pass
                          :callbacks {})]
    (irc/join conn chan)
    conn))

(defn- ensure-conn [conn conf]
  (if-not @conn
    (reset! conn @(connect conf))))

(defn- send-message [conn {:keys [prefix throwable message chan] :as config}]
  (ensure-conn conn config)
  (let [output (str message (timbre/stacktrace throwable "\n"))
        lines  (str/split output #"\n")]
    (irc/message conn chan prefix (first lines))
    (doseq [line (rest lines)]
      (irc/message conn chan ">" line))))

(defn- make-appender-fn [irc-config conn]
  (fn [{:keys [ap-config prefix throwable message]}]
    (prn ap-config)
    (when-let [irc-config (or irc-config (:irc ap-config))]
      (send-message
       conn
       (assoc irc-config
         :prefix    prefix
         :message   message
         :throwable throwable)))))

;;; Public

(defn make-irc-appender
  "Sends IRC messages using irc.
  Needs :irc config map in :shared-appender-config, e.g.:
   {:host \"irc.example.org\" :port 6667 :nick \"logger\"
    :name \"My Logger\" :chan \"#logs\"}"
  [& [appender-opts {:keys [irc-config]}]]
  (let [conn (atom nil)]
    (merge default-appender-opts
           appender-opts
           {:conn conn
            :doc (:doc (meta #'make-irc-appender))
            :fn  (make-appender-fn irc-config conn)})))

(def irc-appender "DEPRECATED: Use `make-irc-appender` instead."
  (make-irc-appender))
