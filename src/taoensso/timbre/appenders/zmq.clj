(ns taoensso.timbre.appenders.zmq
  "ØMQ appender. Requires https://github.com/zeromq/cljzmq"
  {:author "Angus Fletcher"}
  (:require [zeromq.zmq :as zmq]
            [taoensso.timbre :as timbre]))

(defn make-zmq-socket [transport address port]
  (let [context (zmq/zcontext)]
    (doto (zmq/socket context :push)
      (zmq/connect (format "%s://%s:%s" transport address port)))))

(defn appender-fn [socket {:keys [ap-config output]}]
    (zmq/send-str socket output))

(defn make-zmq-appender
  "Returns a ØMQ appender."
  [& [appender-opts {:keys [transport address port]}]]
  (let [default-appender-opts {:enabled? true
                               :min-level :error
                               :async? true}
        socket (make-zmq-socket transport address port)]
    (merge default-appender-opts 
           appender-opts 
           {:socket socket}   
           {:fn (partial appender-fn socket)})))
