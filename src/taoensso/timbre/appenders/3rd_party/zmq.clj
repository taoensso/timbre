(ns taoensso.timbre.appenders.3rd-party.zmq
  "ØMQ appender. Requires https://github.com/zeromq/cljzmq"
  {:author "Angus Fletcher"}
  (:require [zeromq.zmq      :as zmq]
            [taoensso.timbre :as timbre]))

(defn make-zmq-socket [context transport address port]
  (doto (zmq/socket context :push)
    (zmq/connect (format "%s://%s:%d" transport address port))))

(defn make-appender-fn [socket poller]
  (fn [data]
    (let [{:keys [appender-opts output-fn]} data
          output (output-fn data)]
      (loop []
        (zmq/poll poller 500)
        (cond
          (zmq/check-poller poller 0 :pollout) (zmq/send-str socket output)
          (zmq/check-poller poller 0 :pollerr) (System/exit 1)
          :else (recur))))))

(defn make-appender
  "Returns a ØMQ appender. Takes appender options and a map consisting of:
    transport: a string representing transport type: tcp, ipc, inproc, pgm/epgm
    address: a string containing an address to connect to.
    port: a number representing the port to connect to."
  [& [appender-config {:keys [transport address port]}]]
  (let [default-appender-config
        {:enabled? true
         :min-level :error
         :async? true}
        context (zmq/zcontext)
        socket (make-zmq-socket context transport address port)
        poller (doto (zmq/poller context)
                 (zmq/register socket :pollout :pollerr))]
    (merge default-appender-config appender-config
      {:fn (make-appender-fn socket poller)})))

;;;; Deprecated

(def make-zmq-appender make-appender)
