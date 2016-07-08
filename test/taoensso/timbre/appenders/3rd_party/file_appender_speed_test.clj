(ns taoensso.timbre.appenders.3rd-party.file-appender-speed-test
  (:require
    [clojure.test :refer :all]
    [clojure.string :as str]
    [clojure.java.io :as io]
    [taoensso.timbre :as logger]
    [taoensso.timbre.appenders.core :as appenders-core]
    [taoensso.timbre.appenders.3rd-party.file-appender :as file-appender]))


(defn time1 [f & args]
  (let [stime (System/currentTimeMillis)
        res (apply f args)
        etime (System/currentTimeMillis)]
    [(- etime stime) res]))

(def message (str/join (repeat 100 "x")))

(defn appender-test [message times appender]
  (logger/with-config
    (assoc logger/*config* :appenders appender)
    (doseq [_ (range times)]
      (logger/info message))
    (logger/stop!)))

(def spit-file "spit-appender.log")
(def file "file-appender.log")


(defn teardown []
  (io/delete-file spit-file true)
  (io/delete-file file true))


(deftest speed-test
  (testing "Performance Test : Spit Appender vs File Appender"
    (let [ spit-appender {:spit (appenders-core/spit-appender {:fname spit-file})}
           [t1 _] (time1 appender-test message 1000 spit-appender)
           file-appender {:file-appender (file-appender/file-appender {:fname file})}
           [t2 _] (time1 appender-test message 1000 file-appender)]
      (println "Spit Appender takes " t1 "ms to log 1000 messages")
      (println "File Appender takes " t2 "ms to log 1000 messages")
      (is (< t2  (* 2 t1))))
    (teardown)))
