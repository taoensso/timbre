(ns taoensso.timbre.appenders.3rd-party.rolling-test
  (:require
    [clojure.test :refer [deftest is use-fixtures]]
    [taoensso.timbre.appenders.3rd-party.rolling :as rolling])
  (:import
   (java.io File)
   (java.nio.file Files)
   (java.nio.file.attribute FileTime)
   (java.time Instant)
   (java.util Date TimeZone)))

(use-fixtures :once
  (fn [f]
    (let [default-time-zone (TimeZone/getDefault)]
      (try
        ;; Set default time zone to UTC for Calendar/getInstance.
        (TimeZone/setDefault (TimeZone/getTimeZone "Europe/London"))
        (f)
        (finally
          (TimeZone/setDefault default-time-zone))))))

(defn spawns
  "Given a log file (a java.io.File), return that log file and every spawn of
  that log file."
  [log-file]
  (filter #(.startsWith (.getName %) (.getName log-file))
    (-> log-file .getParent File. .listFiles)))

(deftest rolling-appender-concurrency
  (let [log-file (doto (File/createTempFile "timbre.rolling." ".log") (.deleteOnExit))]
    (try
      (let [rolling-appender (rolling/rolling-appender {:path (.getPath log-file) :pattern :daily})
            now (Instant/parse "2021-11-04T00:00:00.00+00:00")
            hour-ago (.minusSeconds now 3600)
            rolled-over-log-file (File. (str log-file ".20211103"))
            old-messages ["AAA" "BBB" "CCC"]
            log-at #((:fn rolling-appender) {:instant %1 :output_ %2})]
        ;; Emulate log entries from an hour ago
        (run! #(log-at (Date/from hour-ago) %) old-messages)

        ;; Set the last modified time of the log file to an hour ago to force rollover
        (Files/setLastModifiedTime (.toPath log-file) (FileTime/from hour-ago))

        (let [new-messages (set (map str (range 100)))]
          ;; Log new messages from many threads
          (run! deref (mapv #(future (log-at (Date/from now) %)) new-messages))
          ;; Rolled-over log file should only have the entries from an hour ago
          (is (= old-messages (vec (.split (slurp rolled-over-log-file) "\n"))))
          ;; New log file should have every message we logged
          (is (= new-messages (set (.split (slurp log-file) "\n"))))
          ;; There should only be two log files: one log file with timestamp
          ;; suffix and one without
          (is (= 2 (count (spawns log-file))))))
      (finally
        (run! #(.delete %) (spawns log-file))))))
