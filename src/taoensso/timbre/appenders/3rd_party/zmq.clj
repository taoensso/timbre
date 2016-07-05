(ns taoensso.timbre.appenders.3rd-party.zmq
  "ØMQ appender. Requires https://github.com/zeromq/cljzmq."
  {:author "Angus Fletcher (@angusiguess)"}
  (:require [zeromq.zmq      :as zmq]
            [taoensso.timbre :as timbre]))

;; TODO Test port to Timbre v4

(defn make-zmq-socket [context transport address port]
  (doto (zmq/socket context :push)
    (zmq/connect (format "%s://%s:%d" transport address port))))

(defm zmq-appender
  "Returns a ØMQ appender. Opts:
    :transport - string representing transport type: tcp, ipc, inproc, pgm/epgm.
    :address   - string containing an address to connect to.
    :port      - number representing the port to connect to."
  [{:keys [transport address port]}]
  (let [context (zmq/zcontext)
        socket (make-zmq-socket context transport address port)
        poller (doto (zmq/poller context)
                 (zmq/register socket :pollout :pollerr))]
    {:enabled?   true
     :async?     true
     :min-level  :error
     :rate-limit nil
     :output-fn  :inherit
     :fn
     (fn [data]
       (let [{:keys [output_]} data
             output-str (force output_)]
         (loop []
           (zmq/poll poller 500)
           (cond
             (zmq/check-poller poller 0 :pollout) (zmq/send-str socket output-str)
             (zmq/check-poller poller 0 :pollerr) (System/exit 1)
             :else (recur)))))}))

;;;; Deprecated

(defn make-zmq-appender
  "DEPRECATED. Please use `zmq-appender` instead."
  [& [appender-merge opts]]
  (merge (zmq-appender opts) zmq-merge))
