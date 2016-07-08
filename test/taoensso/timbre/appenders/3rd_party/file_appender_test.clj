(ns taoensso.timbre.appenders.3rd-party.file-appender-test
  (:require
    [clojure.test :refer :all]
    [clojure.java.io :as io]
    [clojure.data :as data]
    [clojure.string :as str]
    [clojure.set :as cset]
    [taoensso.timbre :as logger]
    [taoensso.timbre.appenders.3rd-party.file-appender :as file-appender]))


(defn message [i] (->>
                    i
                    (repeat 100)
                    (str/join)
                    (str "log-message")))

(def message-seq (map #(message %) (range)))

(def messages
  (let [mseq (map #(message %) (range))]
    (take 100 mseq)))

(def log-file-name "file-appender.log")


;; Creates a future for each message and logs it
(defn log-messages [messages]
  (let [res (doall (map #(future (logger/info %1)) messages))]
    (doseq [r res]
      @r)))

;; Sets up only file appender by removing default appenders from the configuration
(defn setup
  []
  (let [file-appender {:file-appender (file-appender/file-appender {:fname log-file-name})}]
    (logger/set-config! (assoc logger/*config* :appenders file-appender))))

;; closes the resources opened by the appender
(defn teardown
  []
  (io/delete-file log-file-name true))

(defn file-appender-fixture
  [f]
  (setup)
  (f)
  (teardown))

;; Returns a message sequence which will contain only message by stripping down timestamp parts.
;; Timestamp part is not useful for comparison. That is why we strip it out.
(defn read-messages [fname]
  (let [mseq (->
               (slurp fname)
               (str/split #"\n"))]
    (map #(last (str/split % #" ")) mseq)))


(defn diff-log-messages []
  (let [ source-messages (sort messages)
         target-messages (sort (read-messages log-file-name))]
    (data/diff source-messages target-messages)))


(use-fixtures :once file-appender-fixture)

;; This test logs "n" messages into the file by using "n" threads.
;; We verify the correctness of log messges by comparing the source messages and
;; target messages read from the log file.
(deftest file-appender-test
  (testing "All messages are logged correctly by multi threads"
    (log-messages messages)
    (logger/stop!)
    (let [[source-diff target-diff _] (diff-log-messages)]
      (is (empty? source-diff) "Source has some messages that were missing in log file")
      (is (empty? target-diff) "Target has some extra messages than source"))))
