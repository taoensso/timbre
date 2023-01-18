(ns taoensso.timbre.appenders.community.rolling-interval
  "Rolling date-interval file appender with optional historical purging."
  {:author "Micah Duke @madhat2r"}
  (:require [cljc.java-time.day-of-week :as dow]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [tick.core :as t]
            [tick.locale-en-us])
  (:import [java.io FilenameFilter]))

(def ^:private date-format "yyyyMMdd")

(defn- format-date [instant fmt]
  (->> instant
       t/date
       (t/format fmt)))

(defn- start-of-week
  "Calculate the start of week for `instant`"
  [instant]
  (let [date (t/date instant)
        day (t/day-of-week date)
        difference (dow/ordinal day)]
    (t/<< date (t/new-period difference :days))))

(defn- start-of-month
  "Calculate the first day of the containing month of `instant`"
  [instant]
  (-> "%s-01"
      (format (-> instant t/date t/year-month))
      t/date))

(defn- beginning-interval-date
  "Calculate beginning `interval` date for `instant`"
  [instant interval]
  (case interval
    :monthly (start-of-month instant)
    :weekly (start-of-week instant)
    ;; defaults to :daily
    (t/date instant)))

(defn- filename-prefix [instant]
  (format-date (t/date instant) date-format))

(defn- earliest-backlog-date
  "Calculate the earliest possible date based on the `interval` and `backlog`"
  [instant interval backlog]
  (let [d (beginning-interval-date instant interval)]
    (if (= 1 backlog) ;; this will be the last one kept
      d
      (let [new-date (t/<< (t/date d) (t/new-period 1 :days))
            backlog (dec backlog)]
        (earliest-backlog-date new-date interval backlog)))))

;; adapted from `taoensso.timbre.appenders.core`
(defn- write-to-file [data fname output self]
  (try
    (with-open [^java.io.BufferedWriter w (io/writer fname :append true)]
      (.write   w ^String output)
      (.newLine w))

    (catch java.io.IOException e
      (if (:spit-appender/retry? data)
        (throw e) ; Unexpected error
        (do
          (io/make-parents fname)
          (self (assoc data :spit-appender/retry? true)))))))

(defn- filename-regex
  "Returns a `re-pattern` that escapes several chars in basename to
  ensure a cleaner match."
  [basename]
  (let [esc {\( "\\(" \) "\\)" \& "\\&" \^ "\\^" \% "\\%" \$ "\\$"
             \# "\\#" \! "\\!" \? "\\?" \* "\\*" \. "\\."}
        pattern (str "[0-9]{8}\\." (s/escape basename esc))]
    (re-pattern pattern)))

;; adapted from `taoensso.timbre.appenders.community.rotor`
(defn- file-filter
  "Returns a Java FilenameFilter instance which only matches
  files with the given `basename` and date prefix.

  Note: only testing for 8 digit prefix (yyyyMMddd) and ending in basename
  not actually testing the parsability of date prefix.
  "
  ^FilenameFilter [basename]
  (reify FilenameFilter
    (accept [_ _ name]
      (boolean (re-matches (filename-regex basename) name)))))

;; adapted from `taoensso.timbre.appenders.community.rotor`
(defn- matching-files
  "Returns a seq of files with the given `basepath` in the
  same directory."
  [dir basename]
  (let [d (io/file dir)]
    (-> d
        (.listFiles (file-filter basename))
        seq)))

(defn- extract-date-from-filename [fname]
  (-> fname
      (s/split #"\.")
      first
      (t/parse-date (t/formatter date-format))))

(defn- file-earlier-than [date file-date]
  (t/< file-date date))

(defn- delete-to-backlog-limit! [log-files first-date]
  (let [pred-fn (fn _pred [f] (file-earlier-than
                               first-date
                               (extract-date-from-filename (.getName f))))
        files-to-delete (filter pred-fn log-files)]
    (doseq [f files-to-delete]
      (io/delete-file f))))

(defn rolling-interval-appender
  "Returns a rolling interval file appender.

  Options:
    * :path      - logfile path, e/o \"logs/timbre-interval.log\"
    * :interval  - period of interval, e/o #{:daily :weekly :monthly}
    * :backlog!  - number of historical files to keep

  IMPORTANT: if `backlog!` is a number greater than ZERO, then ONLY that number
  of historical versions are kept, deleting the oldest one when a new file is
  created; if ZERO or `nil` then no files are deleted.

  Log file names are created based on `path`; they will be formatted with the
  beginning date of the configured `interval` as the filename prefix.

  Example:
    (rolling-interval-appender {:path \"./logs/app.log\"})

    Date: 2018-01-14 (Sunday)
    Formated date string: 20180114

    Resulting filenames for interval:
      :monthly - \"logs/20180101.app.log\" (First day of month)
      :weekly  - \"logs/20180108.app.log\" (First day of week - Monday)
      :daily   - \"logs/20180114.app.log\"
  "
  [& [{:keys [path interval backlog!]
       :or   {path    "logs/timbre-rolling.log"
              interval :daily
              backlog! 0}}]]

  {:enabled? true
   :fn (let [lock (Object.)
             f (io/file path)
             dir (or (.getParent f) ".") ;; default running dir
             basename (.getName f)]
         (tap> [f dir basename])
         (fn self [data]
           (let [{:keys [instant output_]} data
                 output-str (force output_)
                 beggining-date (beginning-interval-date instant interval)
                 date-prefix (filename-prefix beggining-date)
                 fname (format "%s/%s.%s" dir date-prefix basename)]
             (when-let [log (io/file fname)]
               (try
                 (locking lock
                   (write-to-file data fname output-str self) ;; log it
                   (when (> (or backlog! 0) 0) ;;clean up if needed
                     (delete-to-backlog-limit!
                      (matching-files dir basename) ;; log files
                      (earliest-backlog-date instant interval backlog!))))
                 (catch java.io.IOException _))))))})

(comment
  (rolling-interval-appender)
  ;; used to generate a slew of properly dated/formatted files for testing
  (defn- generate-log-files-for-interval [path date interval num-of-logs backlog!]
    (let [f (io/file path)
          dir (or (.getParent f) ".")
          basename "app.log"
          period (case interval
                   :monthly (t/new-period 1 :months)
                   :weekly (t/new-period 7 :days)
                   (t/new-period 1 :days))]
      (loop [d (t/date date) p period b num-of-logs]
        (let [beggining-date (beginning-interval-date d interval)
              prefix (filename-prefix beggining-date)
              fname (format "%s/%s.%s" dir prefix basename)]
          (when-not (= 0 b)
            ((fn self2 []
               (write-to-file {} fname "test" self2)
               (when (> (or backlog! 0) 0)
                 (delete-to-backlog-limit!
                  (matching-files dir basename)
                  (earliest-backlog-date date interval backlog!)))))
            (recur (t/<< d p) p (dec b)))))))

  (generate-log-files-for-interval "/tmp/logs/app.log" (t/now) :daily 20 20)
;;
  )
