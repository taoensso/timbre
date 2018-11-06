(ns taoensso.timbre.appenders.3rd-party.udp-socket
  {:author "Leo Zovic (@inaimathi)"}
  (:import [java.net DatagramSocket DatagramPacket InetSocketAddress]))

(defn udp-appender
  [host port]
  {:enabled? true
   :async? false
   :min-level nil
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
