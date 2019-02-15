(ns taoensso.timbre.appenders.3rd-party.rolling
  "Rolling file appender."
  {:author "Unknown - please let me know?"}
  (:require [clojure.java.io :as io]
            [taoensso.timbre :as timbre])
  (:import  [java.text SimpleDateFormat]
            [java.util Calendar]))

;; TODO Test port to Timbre v4

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

(defn- log-cal [date] (let [now (Calendar/getInstance)] (.setTime now date) now))

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

(defn rolling-appender
  "Returns a Rolling file appender. Opts:
    :path    - logfile path.
    :pattern - frequency of rotation, e/o {:daily :weekly :monthly}."
  [& [{:keys [path pattern]
       :or   {path    "./timbre-rolling.log"
              pattern :daily}}]]

  {:enabled?   true
   :async?     false
   :min-level  nil
   :rate-limit nil
   :output-fn  :inherit
   :fn
   (fn [data]
     (let [{:keys [instant output_]} data
           output-str (force output_)
           prev-cal   (prev-period-end-cal instant pattern)]
       (when-let [log (io/file path)]
         (try
           (when-not (.exists log)
             (io/make-parents log))
           (if (.exists log)
             (if (<= (.lastModified log) (.getTimeInMillis prev-cal))
               (shift-log-period log path prev-cal))
             (.createNewFile log))
           (spit path (with-out-str (println output-str)) :append true)
           (catch java.io.IOException _)))))})

;;;; Deprecated

(defn make-rolling-appender
  "DEPRECATED. Please use `rolling-appender` instead."
  [& [appender-merge opts]]
  (merge (rolling-appender opts) appender-merge))
