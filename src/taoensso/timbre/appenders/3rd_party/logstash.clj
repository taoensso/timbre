(ns taoensso.timbre.appenders.3rd-party.logstash
  "Appender that sends output to Logstash.
   Requires Cheshire (https://github.com/dakrone/cheshire)."
  {:author "Mike Sperber (@mikesperber), David Frese (@dfrese)"}
  (:require [taoensso.timbre :as timbre]
            [cheshire.core :as cheshire])
  (:import  [java.net Socket InetAddress]
            [java.io PrintWriter]))

;; Adapted from taoensso.timbre.appenders.3rd-party.server-socket

(defn connect
  [host port]
  (let [addr (InetAddress/getByName host)
        sock (Socket. addr (int port))]
    [sock
     (PrintWriter. (.getOutputStream sock))]))

(defn connection-ok?
  [[^Socket sock ^PrintWriter out]]
  (and (not (.isClosed sock))
       (.isConnected sock)
       (not (.checkError out))))

(def iso-format "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")

(defn data->json-stream
  [data writer opts]
  ;; Note: this it meant to target the logstash-filter-json; especially "message" and "@timestamp" get a special meaning there.
  (let [stacktrace-str (if-let [pr (:pr-stacktrace opts)]
                         #(with-out-str (pr %))
                         timbre/stacktrace)]
    (cheshire/generate-stream
     (merge (:context data)
            {:level (:level data)
             :namespace (:?ns-str data)
             :file (:?file data)
             :line (:?line data)
             :stacktrace (some-> (:?err data) (stacktrace-str))
             :hostname (force (:hostname_ data))
             :message (force (:msg_ data))
             "@timestamp" (:instant data)})
     writer
     (merge {:date-format iso-format
             :pretty false}
            opts))))

(defn logstash-appender
  "Returns a Logstash appender, which will send each event in JSON
  format to the logstash server at `host:port`. Additionally `opts`
  may be a map with `:pr-stracktrace` mapped to a function taking an
  exception, which should write the stacktrace of that exception to
  `*out`. Set `:flush?` to true to flush the writer after every
  event."
  [host port & [opts]]
  (let [conn   (atom nil)
        flush? (or (:flush? opts) false)
        nl     "\n"]
    {:enabled?   true
     :async?     false
     :min-level  nil
     :rate-limit nil
     :output-fn  :inherit
     :fn
     (fn [data]
       (try
         (let [[sock out] (swap! conn
                            (fn [conn]
                              (or (and conn (connection-ok? conn) conn)
                                (connect host port))))]
           (locking sock
             (data->json-stream data out (select-keys opts [:pr-stacktrace]))
             ;; logstash tcp input plugin: "each event is assumed to be one line of text".
             (.write ^java.io.Writer out nl)
             (when flush? (.flush ^java.io.Writer out))))
         (catch java.io.IOException _
           nil)))}))
