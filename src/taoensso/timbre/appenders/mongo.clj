(ns taoensso.timbre.appenders.mongo
  "MongoDB appender. Depends on  https://github.com/aboekhoff/congomongo."
  {:author "Emlyn Corrin"}
  (:require [somnium.congomongo :as mongo]))

(def conn (atom nil))

;; Note: including :throwable will fail with:
;;   IllegalArgumentException: can't serialize class java.lang.Exception
(def default-keys [:hostname :ns :level :error? :instant :message :args])

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
                           :or {logged-keys default-keys}
                           :as config}]
  (mongo/with-mongo (ensure-conn config)
    (mongo/insert! collection (select-keys params logged-keys))))

(defn appender-fn [{:keys [ap-config] :as params}]
  (when-let [mongo-config (:mongo ap-config)]
    (log-message params mongo-config)))

(def mongo-appender
  {:doc (str "Logs to MongoDB using congomongo.\n"
             "Needs :mongo config map in :shared-appender-config, e.g.:
             {:db \"logs\"
              :collection \"myapp\"
              :logged-keys [:instant :level :message]
              :write-concern :acknowledged
              :server {:host \"127.0.0.1\"
                       :port 27017}}")
   :min-level :warn :enabled? true :async? true
   :max-message-per-msecs 1000 ; 1 entry / sec
   :fn appender-fn})
