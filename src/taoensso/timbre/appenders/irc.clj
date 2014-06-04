(ns taoensso.timbre.appenders.irc
  "IRC appender. Depends on https://github.com/flatland/irclj."
  {:author "Emlyn Corrin"}
  (:require [clojure.string  :as str]
            [irclj.core      :as irc]
            [taoensso.timbre :as timbre]))

(defn default-fmt-output-fn
  [{:keys [level throwable message]}]
  (format "[%s] %s%s"
          (-> level name (str/upper-case))
          (or message "")
          (or (timbre/stacktrace throwable "\n") "")))

(def default-appender-opts
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
  (fn [{:keys [ap-config] :as args}]
    (when-let [irc-config (or irc-config (:irc ap-config))]
      (ensure-conn conn irc-config)
      (let [fmt-fn (or (:fmt-output-fn irc-config)
                       default-fmt-output-fn)]
        (send-message conn (:chan irc-config) (fmt-fn args))))))

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

(comment
  (timbre/set-config!
   [:shared-appender-config :irc]
   {:host "127.0.0.1"
    :nick "lazylog"
    :user "lazare"
    :name "Lazylus Logus"
    :chan "bob"})
  (timbre/set-config! [:appenders :irc] (make-irc-appender))
  (timbre/log :error "A multiple\nline message\nfor you"))
