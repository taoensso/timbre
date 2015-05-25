(ns taoensso.timbre.appenders.socket
  "TCP Socket appender. Depends on https://github.com/technomancy/server-socket."
  {:author "Emlyn Corrin"}
  (:require [server.socket :refer [create-server]]
            [taoensso.timbre :refer [stacktrace]])
  (:import [java.net Socket InetAddress]
           [java.io BufferedReader InputStreamReader PrintWriter]))

(def conn (atom nil))

(defn listener-fun [in out]
  (loop [lines (-> in
                   (InputStreamReader.)
                   (BufferedReader.)
                   (line-seq))]
    (when-not (re-find #"(?i)^quit" (first lines))
      (recur (rest lines)))))

(defn on-thread-daemon [f]
  (doto (Thread. ^Runnable f)
    (.setDaemon true)
    (.start)))

(defn connect [{:keys [port listen-addr]}]
  (let [addr (when (not= :all listen-addr)
               (InetAddress/getByName listen-addr))]
    (with-redefs [server.socket/on-thread on-thread-daemon]
      (create-server port listener-fun 0 ^InetAddress addr))))

(defn ensure-conn [socket-config]
  (swap! conn #(or % (connect socket-config))))

(defn appender-fn [{:keys [ap-config prefix message throwable] :as params}]
  (when-let [socket-config (:socket ap-config)]
    (let [c (ensure-conn socket-config)]
      (doseq [sock @(:connections c)]
        (let [out (PrintWriter. (.getOutputStream ^Socket sock))]
          (binding [*out* out]
            (println prefix message
                     (stacktrace throwable))))))))

(def socket-appender
  {:doc (str "Logs to a listening socket.\n"
             "Needs :socket config map in :shared-appender-config, e.g.:
             {:listen-addr :all
              :port 9000}")
   :min-level :trace :enabled? true
   :fn appender-fn})
