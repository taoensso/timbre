(ns taoensso.timbre.appenders.3rd-party.irc
  "IRC appender. Requires https://github.com/flatland/irclj."
  {:author "Emlyn Corrin"}
  (:require [clojure.string  :as str]
            [irclj.core      :as irc]
            [taoensso.timbre :as timbre]))

;; TODO Test port to Timbre v4

(defn- connect [{:keys [host port pass nick user name chan]
                :or   {port 6667}}]
  (let [conn (irc/connect host port nick
                          :username  user
                          :real-name name
                          :pass      pass
                          :callbacks {})]
    (irc/join conn chan)
    conn))

(defn- ensure-conn [conn conf] (if-not @conn (reset! conn @(connect conf))))

(defn- send-message [conn chan output]
  (let [[fst & rst] (str/split output #"\n")]
    (irc/message conn chan fst)
    (doseq [line rst]
      (irc/message conn chan ">" line))))

;;;; Public

(defn irc-appender
  "Returns an IRC appender.
  (irc-appender
    {:host \"irc.example.org\" :port 6667 :nick \"logger\"
     :name \"My Logger\" :chan \"#logs\"})"

  [irc-config]
  (let [conn   (atom nil)
        fmt-fn (or (:fmt-output-fn irc-config) default-fmt-output-fn)]
    {:enabled?   true
     :async?     true
     :min-level  :info
     :rate-limit nil

     :output-fn
     (fn [data]
       (let [{:keys [level ?err_ msg_]}]
         (format "[%s] %s%s"
           (-> level name (str/upper-case))
           (or (force msg_) "")
           (if-let [err (force ?err_)]
             (str "\n" (timbre/stacktrace err))
             ""))))

     :fn
     (fn [data]
       (let [{:keys [output-fn]} data]
         (ensure-conn conn irc-config)
         (send-message conn (:chan irc-config) (output-fn data))))}))

;;;; Deprecated

(defn make-irc-appender
  "DEPRECATED. Please use `irc-appender` instead."
  [& [appender-merge opts]]
  (merge (irc-appender (:irc-config opts) (dissoc :irc-config opts))
    appender-merge))

;;;;

(comment
  (timbre/merge-config! {:appenders {:irc (irc-appender)}})
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
