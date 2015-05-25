(ns taoensso.timbre.appenders.3rd-party.irc
  "IRC appender. Requires https://github.com/flatland/irclj."
  {:author "Emlyn Corrin"}
  (:require [clojure.string  :as str]
            [irclj.core      :as irc]
            [taoensso.timbre :as timbre]))

(defn default-fmt-output-fn
  [{:keys [level ?err_ msg_]}]
  (format "[%s] %s%s"
    (-> level name (str/upper-case))
    (or (force msg_) "")
    (if-let [err (force ?err_)]
      (str "\n" (timbre/stacktrace err))
      "")))

(def default-appender-config
  {:async?        true
   :enabled?      true
   :min-level     :info})

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

(defn- send-message [conn chan output]
  (let [[fst & rst] (str/split output #"\n")]
    (irc/message conn chan fst)
    (doseq [line rst]
      (irc/message conn chan ">" line))))

(defn- make-appender-fn [irc-config conn]
  (fn [data]
    (let [{:keys [appender-opts]} data]
      (when-let [irc-config (or irc-config appender-opts)]
        (ensure-conn conn irc-config)
        (let [fmt-fn (or (:fmt-output-fn irc-config)
                         default-fmt-output-fn)]
          (send-message conn (:chan irc-config) (fmt-fn data)))))))

;;; Public

(defn make-appender
  "Sends IRC messages using irc.
  Needs :opts map in appender, e.g.:
  {:host \"irc.example.org\" :port 6667 :nick \"logger\"
   :name \"My Logger\" :chan \"#logs\"}"
  [& [appender-config {:keys [irc-config]}]]
  (let [conn (atom nil)]
    (merge default-appender-config appender-config
      {:conn conn
       :fn   (make-appender-fn irc-config conn)})))

(comment
  (timbre/merge-config! {:appenders {:irc (make-appender)}})
  (timbre/merge-config!
    {:appenders
     {:irc
      {:opts
       {:host "127.0.0.1"
        :nick "lazylog"
        :user "lazare"
        :name "Lazylus Logus"
        :chan "bob"}}}})
  (timbre/error "A multiple\nline message\nfor you"))
