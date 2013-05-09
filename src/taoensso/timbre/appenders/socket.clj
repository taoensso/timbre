(ns taoensso.timbre.appenders.socket
  "Socket appender for sending log messages to a socket."
  {:author "Emlyn Corrin"}
  (:require [server.socket :refer [create-server]])
  (:import [java.net Socket]
           [java.io BufferedReader InputStreamReader PrintWriter]))

(def conn (atom nil))

(defn listener-fun [in out]
  (loop [[line & rest] (in ->
                           #(InputStreamReader. %)
                           #(BufferedReader. %)
                           line-seq)]
    (when-not (re-find #"(?i)^quit" line)
      (recur rest))))

(defn ensure-conn [{:keys [port]}]
  (swap! conn #(or % (create-server port listener-fun))))

(defn appender-fn [{:keys [ap-config prefix message] :as params}]
  (when-let [socket-config (:socket ap-config)]
    (let [c (ensure-conn socket-config)]
      (doseq [s @(:connections @conn)]
        (let [out (PrintWriter. (.getOutputStream ^Socket s))]
          (binding [*out* out]
            (println prefix message)))))))

(def socket-appender
  {:doc (str "Logs to a listening socket.\n"
             "Needs :socket config map in :shared-appender-config, e.g.:
             {:port 9000}")
   :min-level :trace :enabled? true :async? false
   :max-message-per-msecs nil ; no rate limit by default
   :fn appender-fn})
