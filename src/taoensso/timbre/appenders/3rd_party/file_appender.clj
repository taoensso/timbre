(ns taoensso.timbre.appenders.3rd-party.file-appender
  "Simple file appender which opens and closes the log file only once.
  That is why it is 10 times faster than spit appender which opens and closes the log file
  while writing every log message"
  {:author "Srinivasan Natarjan (@nvseenu)"}
  (:require
   [clojure.java.io :as io])
  (:import
    [java.io File FileWriter BufferedWriter IOException]))

(defn- close-writer
  [^BufferedWriter w]
  (try
    (when-not (nil? w)
      (doto w
        (.flush)
        (.close)))
    (catch IOException ioe
      (.printStackTrace ioe))))

(defn- write-data
  [^BufferedWriter w data]
  (let [{:keys [instant level output_ ]} data
        ^String output-str (force output_)]
    (try
      (.write w (str output-str "\n"))
      (catch IOException ioe
        (.printStackTrace ioe)
        (close-writer w)))))


(defn file-appender
  "Returns a file appender map"
  [& [opts]]
  (let [{:keys [^String fname buffer-size]
        :or { fname "timbre-file-appender.log"
              buffer-size 8192}} opts
        w (->
            fname
            (File.)
            (FileWriter. true)
            (BufferedWriter. buffer-size))]

    {:enabled?   true
     :async?     false
     :min-level  nil
     :rate-limit nil
     :output-fn :inherit
     :fn (partial write-data w)
     ;; close-fn function is a hook to close the writer, and it will be called by timbre/stop!
     :close-fn (partial close-writer w)
     }))
