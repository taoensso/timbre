(ns taoensso.timbre.appenders.3rd-party.server-socket
  "TCP socket appender.
  Requires https://github.com/technomancy/server-socket."
  {:author "Emlyn Corrin (@emlyn)"}
  (:require [server.socket :refer [create-server]]
            [taoensso.timbre :refer [stacktrace]])
  (:import  [java.net Socket InetAddress]
            [java.io BufferedReader InputStreamReader PrintWriter]))

;; TODO Test port to Timbre v4

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

(def conn (atom nil))
(defn connect [{:keys [port listen-addr]}]
  (let [addr (when (not= :all listen-addr)
               (InetAddress/getByName listen-addr))]
    (with-redefs [server.socket/on-thread on-thread-daemon]
      (create-server port listener-fun 0 ^InetAddress addr))))

(defn ensure-conn [socket-config] (swap! conn #(or % (connect socket-config))))

(defn server-socket-appender
  "Returns a TCP socket appender.
  (socket-appender {:listener-addr :all :port 9000})"
  [& [socket-config]]
  (let [{:keys [listener-addr port]
         :or   {listener-addr :all
                port 9000}} socket-config]

    {:enabled?   true
     :async?     false
     :min-level  nil
     :rate-limit nil
     :output-fn  :inherit
     :fn
     (fn [data]
       (let [{:keys [output_]} data]
         (let [c (ensure-conn socket-config)]
           (doseq [sock @(:connections c)]
             (let [out (PrintWriter. (.getOutputStream ^Socket sock))]
               (binding [*out* out]
                 (println (force output_))))))))}))

;;;; Deprecated

(defn make-socket-appender
  "DEPRECATED. Please use `server-socket-appender` instead."
  [& [appender-merge opts]]
  (merge (server-socket-appender opts) appender-merge))
