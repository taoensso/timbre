(ns taoensso.timbre.appenders.3rd-party.postgresdb
  "PostgreSQL database appender.
  Requires https://github.com/clojure/java.jdbc,
           https://github.com/swaldman/c3p0"
  {:author "Yue Liu (@yuliu-mdsol)"}
  (:require
   [taoensso.timbre   :as timbre]
   [taoensso.encore   :as enc]
   [clojure.java.jdbc :as jdbc]))

(defn pool [spec]
  (let [cpds
        (doto (com.mchange.v2.c3p0.ComboPooledDataSource.)
          (.setJdbcUrl (str "jdbc:" (:type spec) "://" (:host spec) ":" (:port spec) "/" (:database spec)))
          (.setUser (:username spec))
          (.setPassword (:password spec))
          ;; expire excess connections after 30 minutes of inactivity:
          (.setMaxIdleTimeExcessConnections (* 30 60))
          ;; expire connections after 3 hours of inactivity:
          (.setMaxIdleTime (* 3 60 60)))]
    {:datasource cpds}))

(def default-pool-spec {:type "postgresql" :host "127.0.0.1" :port 5432})
(defn connect [config]
  (let [spec (merge default-pool-spec (:server config))]
    (pool spec)))

(def conn (atom nil))
(defn ensure-conn [config] (swap! conn #(or % (connect config))))

(defn log-message [config data]
  (let [entry
        {:instant   (java.sql.Timestamp. (.getTime ^java.util.Date (:instant data)))
         :level     (str  (:level     data))
         :namespace (str  (:?ns-str   data))
         :hostname  (str @(:hostname_ data))
         :content   (str @(:vargs_    data))
         :error     (str @(:?err_     data))}]

    (ensure-conn config)
    (jdbc/insert! @conn :logs entry)))

(defn pgsql-appender
  "Returns a PostgreSQL appender for `clojure.java.jdbc`.
  (pglog-appender {:server {:host \"127.0.0.1\" :port 5432}})

  SQL ddl script for table creation and rollback
  ----------------------------------------------

  ```
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
  ```

  To automate database migration
  ------------------------------

  * Using Migratus + Leiningen (in profiles.clj):
    :database-url \"postgresql://<db_username>:<db_password>@<db_servername>:<db_port>/<db_schema>\"

  * Using Boot + Ragtime (in build.boot):
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
   :fn (fn [data] (log-message db-config data))})
