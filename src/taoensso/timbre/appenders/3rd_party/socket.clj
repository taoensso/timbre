(ns taoensso.timbre.appenders.3rd-party.socket
  "TCP socket appender. Requires https://github.com/technomancy/server-socket."
  {:author "Emlyn Corrin"}
  (:require [server.socket :refer [create-server]]
            [taoensso.timbre :refer [stacktrace]])
  (:import  [java.net Socket InetAddress]
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

(defn make-appender-fn [make-config]
  (fn [data]
    (let [{:keys [appender-opts output-fn ?err_]} data]
      (when-let [socket-config appender-opts]
        (let [c (ensure-conn socket-config)]
          (doseq [sock @(:connections c)]
            (let [out (PrintWriter. (.getOutputStream ^Socket sock))]
              (binding [*out* out]
                (println (output-fn data))))))))))

(defn make-appender
  "Logs to a listening socket.
  Needs :opts map in appender, e.g.:
  {:listen-addr :all
   :port 9000}"
  [& [appender-config make-config]]
  (let [default-appender-config
        {:min-level :trace :enabled? true}]
    (merge default-appender-config appender-config
      {:fn (make-appender-fn make-config)})))
