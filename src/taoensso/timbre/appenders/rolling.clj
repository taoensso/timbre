(ns taoensso.timbre.appenders.rolling "Rolling file appender."
  (:require [clojure.java.io :as io]
            [taoensso.timbre :as timbre])
  (:import [java.text SimpleDateFormat]
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
  (fn [{:keys [ap-config output instant]}]
    (let [path (or path (-> ap-config :rolling :path))
          pattern (or pattern (-> ap-config :rolling :pattern) :daily)
          prev-cal (prev-period-end-cal instant pattern)
          log (io/file path)]
      (when log
        (try
          (if (.exists log)
            (if (<= (.lastModified log) (.getTimeInMillis prev-cal))
              (shift-log-period log path prev-cal))
            (.createNewFile log))
          (spit path (with-out-str (timbre/str-println output)) :append true)
          (catch java.io.IOException _))))))

(defn make-rolling-appender
  "Returns a Rolling file appender.
  A rolling config map can be provided here as a second argument, or provided at
  :rolling in :shared-appender-config.

  (make-rolling-appender {:enabled? true}
    {:path \"log/app.log\"
     :pattern :daily})
  path: logfile path
  pattern: frequency of rotation, available values: :daily (default), :weekly, :monthly"
  [& [appender-opts {:keys [path pattern]}]]
  (let [default-appender-opts {:enabled? true :min-level nil}]
    (merge default-appender-opts appender-opts
      {:fn (make-appender-fn path pattern)})))
