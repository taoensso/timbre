(ns ^:no-doc taoensso.timbre.appenders.community.zmq
  ;; `^:no-doc` needed to prevent broken cljdoc build
  "ØMQ appender.
  Requires <https://github.com/zeromq/cljzmq>."
  {:author "Angus Fletcher (@angusiguess)"}
  (:require
   [taoensso.encore :as enc]
   [taoensso.timbre :as timbre]
   [zeromq.zmq      :as zmq]))

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
    {:enabled?  true
     :async?    true
     :min-level :error
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

(enc/deprecated
  (defn ^:no-doc ^:deprecated make-zmq-appender
    "Prefer `zmq-appender`."
    [& [appender-merge opts]]
    (merge (zmq-appender opts) zmq-merge)))
