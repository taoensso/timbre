(ns taoensso.timbre.appenders.3rd-party.mongo
  "MongoDB appender. Requires on https://github.com/aboekhoff/congomongo."
  {:author "Emlyn Corrin"}
  (:require [somnium.congomongo :as mongo]
            [taoensso.timbre    :as timbre]
            [taoensso.encore    :as encore]))

(def conn (atom nil))

(def default-args {:host "127.0.0.1" :port 27017})

(defn connect [{:keys [db server write-concern]}]
  (let [args (merge default-args server)
        c (mongo/make-connection db args)]
    (when write-concern
      (mongo/set-write-concern c write-concern))
    c))

(defn ensure-conn [config]
  (swap! conn #(or % (connect config))))

(defn log-message [params {:keys [collection logged-keys]
                           :as config}]
  (let [selected-params (if logged-keys
                          (select-keys params logged-keys)
                          (dissoc params :config :appender :appender-opts))
        logged-params (encore/map-vals #(str (force %)) selected-params)]
    (mongo/with-mongo (ensure-conn config)
      (mongo/insert! collection logged-params))))

(defn- make-appender-fn [make-config]
  (fn [data]
    (let [{:keys [appender-opts]} data]
      (when-let [mongo-config appender-opts]
        (log-message data mongo-config)))))

(defn make-appender
  "Logs to MongoDB using congomongo. Needs :opts map in appender, e.g.:
  {:db \"logs\"
   :collection \"myapp\"
   :logged-keys [:instant :level :msg_]
   :write-concern :acknowledged
   :server {:host \"127.0.0.1\"
   :port 27017}}"
  [& [appender-config make-config]]
  (let [default-appender-config
        {:min-level :warn :enabled? true :async? true
         :rate-limit [[1 1000]]}]
    (merge default-appender-config appender-config
      {:fn (make-appender-fn make-config)})))
