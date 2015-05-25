(ns taoensso.timbre.appenders.3rd-party.rolling
  "Rolling file appender."
  {:author "Unknown - please let me know?"}
  (:require [clojure.java.io :as io]
            [taoensso.timbre :as timbre])
  (:import  [java.text SimpleDateFormat]
            [java.util Calendar]))

(defn- rename-old-create-new-log [log old-log]
  (.renameTo log old-log)
  (.createNewFile log))

(defn- shift-log-period [log path prev-cal]
  (let [postfix (-> "yyyyMMdd" SimpleDateFormat. (.format (.getTime prev-cal)))
        old-path (format "%s.%s" path postfix)
        old-log (io/file old-path)]
    (if (.exists old-log)
      (loop [index 0]
        (let [index-path (format "%s.%d" old-path index)
              index-log (io/file index-path)]
          (if (.exists index-log)
            (recur (+ index 1))
            (rename-old-create-new-log log index-log))))
      (rename-old-create-new-log log old-log))))

(defn- log-cal [date]
  (let [now (Calendar/getInstance)]
    (.setTime now date)
    now))

(defn- prev-period-end-cal [date pattern]
  (let [cal (log-cal date)
        offset (case pattern
                 :daily 1
                 :weekly (.get cal Calendar/DAY_OF_WEEK)
                 :monthly (.get cal Calendar/DAY_OF_MONTH)
                 0)]
    (.add cal Calendar/DAY_OF_MONTH (* -1 offset))
    (.set cal Calendar/HOUR_OF_DAY 23)
    (.set cal Calendar/MINUTE 59)
    (.set cal Calendar/SECOND 59)
    (.set cal Calendar/MILLISECOND 999)
    cal))

(defn- make-appender-fn [path pattern]
  (fn [data]
    (let [{:keys [instant appender-opts output-fn]} data
          output (output-fn data)
          path (or path (-> appender-opts :path))
          pattern (or pattern (-> appender-opts :pattern) :daily)
          prev-cal (prev-period-end-cal instant pattern)
          log (io/file path)]
      (when log
        (try
          (if (.exists log)
            (if (<= (.lastModified log) (.getTimeInMillis prev-cal))
              (shift-log-period log path prev-cal))
            (.createNewFile log))
          (spit path (with-out-str (println output)) :append true)
          (catch java.io.IOException _))))))

(defn make-appender
  "Returns a Rolling file appender.
  A rolling config map can be provided here as a second argument, or provided in
  appender's :opts map.

  (make-rolling-appender {:enabled? true}
    {:path \"log/app.log\"
     :pattern :daily})
  path: logfile path
  pattern: frequency of rotation, available values: :daily (default), :weekly, :monthly"
  [& [appender-config {:keys [path pattern]}]]
  (let [default-appender-config {:enabled? true :min-level nil}]
    (merge default-appender-config appender-config
      {:fn (make-appender-fn path pattern)})))
