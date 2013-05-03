(ns taoensso.timbre.appenders.mongo
  "MongoDB appender for congomongo.
  Ref: https://github.com/aboekhoff/congomongo."
  {:author "Emlyn Corrin"}
  (:require [somnium.congomongo :as mongo]))

(def conn (atom nil))

(def default-keys [:level :instant :ns :message :more])

(defn ensure-conn [{:keys [db args]}]
  (swap! conn #(or % (mongo/make-connection db args))))

(defn log-message [params {:keys [collection logged-keys]
                           :or {logged-keys default-keys}
                           :as config}]
  (mongo/with-mongo (ensure-conn config)
    (mongo/insert! collection
                   (select-keys params logged-keys))))

(defn appender-fn [{:keys [ap-config] :as params}]
  (when-let [mongo-config (:mongo ap-config)]
    (log-message params mongo-config)))

(def mongo-appender
  {:doc (str "Logs to MongoDB using congomongo.\n"
             "Needs :mongo config map in :shared-appender-config, e.g.:
             {:db \"logs\" :collection \"myapp\"
              :args {:host \"127.0.0.1\"
                     :port 27017}}")
   :min-level :warn :enabled? true :async? false
   :max-message-per-msecs nil ; no rate limit by default
   :fn appender-fn})
