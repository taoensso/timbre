(ns taoensso.timbre.appenders.3rd-party.gelf
  "Appender to handle sending messages in the gelf format to a centralized
  logging server (e.g. Graylog)"
  {:author "Dave Owens (@davewo)"}
  (:import
   [org.graylog2.gelfclient
    GelfConfiguration
    GelfMessageBuilder
    GelfTransports
    GelfMessageLevel]
   [java.net InetSocketAddress]))

(let [gelf-levels
      {:info  GelfMessageLevel/INFO
       :warn  GelfMessageLevel/WARNING
       :error GelfMessageLevel/ERROR
       :fatal GelfMessageLevel/CRITICAL}]

  (defn timbre-to-gelf-level [level]
    (get gelf-levels level GelfMessageLevel/WARNING)))

(defn make-gelf-transport
  "Returns a new GelfTransport object, capable of sending a GelfMessage to a
  remote server. Params:
    `host`     - IP address or hostname string of the remote logging server
    `port`     - TCP or UDP port on which the server listens
    `protocol` - e/o #{:tcp :udp}"
  [host port protocol]
  {:pre [(#{:udp :tcp} protocol)]}
  (let [protocols {:udp GelfTransports/UDP :tcp GelfTransports/TCP}
        transport (protocol protocols)
        config    (-> (GelfConfiguration. (InetSocketAddress. host port))
                      (.transport transport))]
    (GelfTransports/create config)))

(defn gelf-appender
  "Returns a Timbre appender that sends gelf messages to a remote host. Params:
    `gelf-server` - IP address or hostname string of the remote logging server
    `port`        - TCP or UDP port on which the server listens
    `protocol`    - e/o #{:tcp :udp}, defaults to :udp"
  ([gelf-server port         ] (gelf-appender gelf-server port :udp))
  ([gelf-server port protocol]
   (let [tranport (make-gelf-transport gelf-server port protocol)]
     {:enabled?       true
      :async?         false
      :min-level      nil
      :rate-limit     nil
      :output-fn      :inherit
      :gelf-transport tranport
      :fn
      (fn [data]
        (let [{:keys [appender msg_ level hostname_]} data
              gelf-transport (:gelf-transport appender)
              log-level      (timbre-to-gelf-level level)
              gelf-message   (-> (GelfMessageBuilder. (force msg_) (force hostname_))
                                 (.level log-level) .build)]
          (.send gelf-transport gelf-message)))})))
