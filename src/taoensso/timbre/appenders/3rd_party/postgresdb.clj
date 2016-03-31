(ns taoensso.timbre.appenders.3rd-party.postgresdb
  "postgresql database appender"
  (:require [clojure.java.jdbc  :as j]
            [environ.core       :refer [env]]
            [taoensso.timbre    :as timbre]
            [taoensso.encore    :as encore])
  (:import com.mchange.v2.c3p0.ComboPooledDataSource))

(def default-args {:type "postgresql" :host "127.0.0.1" :port 5432})

(defn pool [spec]
  (let [cpds (doto (ComboPooledDataSource.)
              (.setJdbcUrl (str "jdbc:" (:type spec) "://" (:host spec) ":" (:port spec) "/" (:database spec)))
              (.setUser (:username spec))
              (.setPassword (:password spec))
              ;; expire excess connections after 30 minutes of inactivity:
              (.setMaxIdleTimeExcessConnections (* 30 60))
              ;; expire connections after 3 hours of inactivity:
              (.setMaxIdleTime (* 3 60 60)))]
    {:datasource cpds}))

(defn connect [{:keys [server]}]
  (let [args (merge default-args server)]
     @(delay (pool args))))

(def conn (atom nil))
(defn ensure-conn [config] (swap! conn #(or % (connect config))))

(defn log-message [data config]
  (let [entry {:instant   (java.sql.Timestamp. (.getTime (:instant data)))
               :level     (str  (:level         data))
               :namespace (str  (:?ns-str       data))
               :hostname  (str @(:hostname_     data))
               :content   (str @(:vargs_        data))
               :error     (str @(:?err_         data))}]
    (ensure-conn config)
    (j/insert! @conn :logs entry)))

(defn pglog-appender
  "Returns a postgresqlDB appender.
  (pglog-appender {:server {:host \"127.0.0.1\" :port 5432}})

  For log table creation and rollback, here are sql ddl script to use:

  CREATE TABLE IF NOT EXISTS logs (
      log_id bigserial primary key,
      instant timestamp NOT NULL,
      level varchar(20) NOT NULL,
      namespace varchar(50) NOT NULL,
      hostname varchar(30) NOT NULL,
      content text NOT NULL,
      error text NOT NULL
  );

  DROP TABLE IF EXISTS logs;

  To automate database migration, you can use migratus for Leiningen,
  or use ragtime for Boot.

  Leiningen migratus (in profiles.clj)
  ------------------------------------
  :database-url \"postgresql://<db_username>:<db_password>@<db_servername>:<db_port>/<db_schema>\"

  Boot ragtime (in build.boot)
  ----------------------------
  ragtime {:driver-class \"org.postgresql.Driver\"
           :database (str \"jdbc:postgresql://<dbserver_name>:<db_port>/\"
                        \"<db_schema>\"
                        \"?user=<db_usernmae>\"
                        \"&password=<db_password>\")})"

  [db-config]
  {:enabled?   true
   :async?     false
   :min-level  nil
   :rate-limit nil
   :output-fn  :inherit
   :fn (fn [data] (log-message data db-config))})