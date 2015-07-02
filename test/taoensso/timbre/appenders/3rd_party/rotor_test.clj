(ns taoensso.timbre.appenders.3rd-party.rotor-test
  (:require
    [clojure.test :refer :all]
    [clojure.java.io :as io]
    [taoensso.timbre :as timbre]
            [taoensso.timbre.appenders.3rd-party.rotor :as rotor]))

(deftest rotor-test
  []
  (let [logfile "rotor-test.log"
        n-logs 5]
    (timbre/merge-config!
      {:appenders {:rotor (rotor/rotor-appender
                            {:path logfile
                             :max-size 200
                             :backlog n-logs})}})
    (doseq [i (range 100)]
      (timbre/info "testing..."))

    (let [f (io/file logfile)]
      (is (.exists f))
      (.delete f))

    (doseq [i (range 1 (inc n-logs))]
      (let [f (io/file (str logfile ".00" i))]
        (is (.exists f))
        (.delete f)))))


