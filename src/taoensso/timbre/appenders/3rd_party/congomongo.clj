(ns taoensso.timbre.appenders.3rd-party.congomongo
  "MongoDB appender. Requires on https://github.com/aboekhoff/congomongo."
  {:author "Emlyn Corrin"}
  (:require [somnium.congomongo :as mongo]
            [taoensso.timbre    :as timbre]
            [taoensso.encore    :as encore]))

;; TODO Test port to Timbre v4

(def default-args {:host "127.0.0.1" :port 27017})
(defn connect [{:keys [db server write-concern]}]
  (let [args (merge default-args server)
        c (mongo/make-connection db args)]
    (when write-concern
      (mongo/set-write-concern c write-concern))
    c))

(def conn (atom nil))
(defn ensure-conn [config] (swap! conn #(or % (connect config))))

(defn log-message [params {:keys [collection logged-keys] :as config}]
  (let [entry {:instant  instant
               :level    level
               :?ns-str  (str  (:?ns-str       data))
               :hostname (str @(:hostname_     data))
               :vargs    (str @(:vargs_        data))
               :?err     (str @(:?err_         data))}]
    (mongo/with-mongo (ensure-conn config)
      (mongo/insert! collection entry))))

(defn congomongo-appender
  "Returns a congomongo MongoDB appender.
  (congomongo-appender
    {:db \"logs\"
     :collection \"myapp\"
     :logged-keys [:instant :level :msg_]
     :write-concern :acknowledged
     :server {:host \"127.0.0.1\"
     :port 27017}})"

  [congo-config]
  {:enabled?   true
   :async?     true
   :min-level  :warn
   :rate-limit [[1 1000]] ; 1/sec
   :output-fn  :inherit
   :fn (fn [data] (log-message data congo-config))})
