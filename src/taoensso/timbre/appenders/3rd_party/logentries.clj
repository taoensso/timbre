(ns taoensso.timbre.appenders.3rd-party.logentries
  "Appender that sends output to Logentries (https://logentries.com/). Based of the logstash 3rd party appender.
   Requires Cheshire (https://github.com/dakrone/cheshire)."
  {:author "Mike Sperber (@mikesperber), David Frese (@dfrese), Ryan Smith (@tanzoniteblack)"}
  (:require [taoensso.timbre :as timbre]
            [cheshire.core :as cheshire]
            [io.aviso.exception])
  (:import  [java.net Socket InetAddress]
            [java.io PrintWriter]))

;; Adapted from taoensso.timbre.appenders.3rd-party.logstash

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

(def stack-trace-processor (comp (remove :omitted)
                                 (map (fn [stack-frame]
                                        (select-keys stack-frame [:formatted-name :file :line])))))

(defn error-to-stacktrace
  "Create a tersely formatted vector of stack traces. This will show up in a
  nicely computationally searchable fashion in the json sent to logentries"
  [e]
  (when e
    (binding [io.aviso.exception/*fonts* nil]
      (let [errors (io.aviso.exception/analyze-exception e {})]
        (try (mapv #(update % :stack-trace (fn [stack-trace]
                                             (into [] stack-trace-processor stack-trace)))
                   errors)
             (catch Exception e
               (remove :omitted errors)))))))

(defn data->json-stream
  [data writer user-tags]
  ;; Note: this it meant to target the logstash-filter-json; especially "message" and "@timestamp" get a special meaning there.
  (cheshire/generate-stream
   (merge user-tags
          (:context data)
          {:level       (:level data)
           :namespace   (:?ns-str data)
           :file        (:?file data)
           :line        (:?line data)
           :stacktrace  (error-to-stacktrace (:?err data))
           :hostname    (force (:hostname_ data))
           :message     (force (:msg_ data))
           "@timestamp" (:instant data)})
   writer
   {:date-format iso-format
    :pretty      false}))

(defn logentries-appender
  "Returns a Logentries appender, which will send each event in JSON format to the
  logentries server. Set `:flush?` to true to flush the writer after every
  event. If you wish to send additional, custom tags, to logentries on each
  logging event, then provide a hash-map in the opts `:user-tags` which will be
  merged into each event."
  [token & [opts]]
  (let [conn   (atom nil)
        flush? (or (:flush? opts) false)
        nl     "\n"
        token    (str token " ")]
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
                                  (connect "data.logentries.com" 80))))]
           (locking sock
             (.write ^java.io.Writer out token)
             (data->json-stream data out (:user-tags opts))
             ;; logstash tcp input plugin: "each event is assumed to be one line of text".
             (.write ^java.io.Writer out nl)
             (when flush? (.flush ^java.io.Writer out))))
         (catch java.io.IOException _
           nil)))}))
