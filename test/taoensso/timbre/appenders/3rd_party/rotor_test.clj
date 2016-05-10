(ns taoensso.timbre.appenders.3rd-party.rotor-test
  (:require
    [clojure.test :refer :all]
    [clojure.java.io :as io]
    [clojure.set :as set]
    [taoensso.timbre :as timbre]
    [taoensso.timbre.appenders.3rd-party.rotor :as rotor]))

(def logfile "rotor-test.log")

(defn logname
  [i]
  (format "%s.%03d" logfile i))

(defn setup
  [n-logs]
  (timbre/merge-config!
   {:appenders {:rotor (rotor/rotor-appender
                        {:path logfile
                         :max-size 200
                         :backlog n-logs})}}))

(defn check-logs-present
  [n-logs]
  (let [f (io/file logfile)]
    (is (.exists f)))

  (doseq [i (range 1 (inc n-logs))]
    (let [f (io/file (logname i))]
      (is (.exists f)))))

(defn delete
  [logfile]
  (let [f (io/file logfile)]
    (try
      (.delete f)
      (catch java.io.FileNotFoundException e nil))))

(defn teardown
  [n-logs]
  (delete logfile)

  (doseq [i (range 1 (inc n-logs))]
    (delete (logname i))))

(deftest rotor-test
  (let [n-logs 5]
    (setup n-logs)
    (doseq [i (range 100)]
      (timbre/info "testing..."))
    (check-logs-present n-logs)
    (teardown n-logs)))

(defn check-complete
  [n-logs n-lines]
  (is (= (set (range n-lines))
         (apply set/union
                (for [n (cons logfile (map logname (range 1 (inc n-logs))))]
                  (try
                    (with-open [rdr (io/reader n)]
                      (set (map (fn [line]
                                  (let [[_ x] (re-matches #".*testing: ([0-9]+)$" line)]
                                    (Integer/parseInt x)))
                                (line-seq rdr))))
                    (catch java.io.FileNotFoundException e (set []))))))))


(deftest rotor-complete-test
  (testing "no log entry gets thrown away"
    (let [n-logs 100
          n-lines 100]

      (setup n-logs)

      (doseq [i (range n-lines)]
        (timbre/info "testing:" i))

      (check-complete n-logs n-lines)

      (teardown n-logs))))

(deftest rotor-concurrency-test
  (testing "no race rotating log files"
    (let [n-logs 100
          n-lines 100]

      (setup n-logs)

      (let [futures
            (for [i (range n-lines)]
              (future
                (timbre/info "testing:" i)))]
        (doseq [f futures]
          @f))

      (check-complete n-logs n-lines)

      (teardown n-logs))))



