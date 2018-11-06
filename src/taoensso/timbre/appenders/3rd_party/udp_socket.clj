(ns taoensso.timbre.appenders.3rd-party.udp-socket
  {:author "Leo Zovic (@inaimathi)"}
  (:import [java.net DatagramSocket DatagramPacket InetSocketAddress]))

(defn udp-appender
  "Returns a UDP socket appender, which sends each event
  at the datagram socket designated by `host` and `port`.
  Due to the limitations of UDP, truncates output to 512 bytes."
  [{:keys [host port]}]
  {:enabled?   true
   :async?     false
   :min-level  nil
   :rate-limit nil
   :output-fn :inherit
   :fn
   (fn [data]
     (let [{:keys [output_]} data
           formatted-output-str (force output_)
           payload (.getBytes formatted-output-str)
           length (min (count payload) 512)
           address (InetSocketAddress. host port)
           packet (DatagramPacket. payload length address)]
       (.send (DatagramSocket.) packet)))})
