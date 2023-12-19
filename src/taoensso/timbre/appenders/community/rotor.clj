(ns taoensso.timbre.appenders.community.rotor
  "Rotating file appender."
  {:author "Karsten Schmidt (@postspectacular)"}
  (:require
   [taoensso.encore :as enc]
   [taoensso.timbre :as timbre]
   [clojure.java.io :as io])

  (:import
   [java.io File FilenameFilter]))

(defn- ^FilenameFilter file-filter
  "Returns a Java FilenameFilter instance which only matches
  files with the given `basename`."
  [basename]
  (reify FilenameFilter
    (accept [_ _ name]
      (.startsWith name basename))))

(defn- matching-files
  "Returns a seq of files with the given `basepath` in the
  same directory."
  [basepath]
  (let [f (-> basepath io/file (.getAbsoluteFile))]
    (-> (.getParentFile f)
        (.listFiles (file-filter (.getName f)))
        seq)))

(defn- rotate-logs
  "Performs log file rotation for the given files matching `basepath`
  and up to a maximum of `max-count`. Historical versions are suffixed
  with a 3-digit index, e.g.

      logs/app.log     ; current log file
      logs/app.log.001 ; most recent log file
      logs/app.log.002 ; second most recent log file etc.

  If the max number of files has been reached, the oldest one
  will be deleted. In future, there will be a suffix fn to customize
  the naming of archived logs."
  [basepath max-count]
  (let [abs-path (-> basepath io/file (.getAbsolutePath))
        logs     (-> basepath matching-files sort)
        [logs-to-rotate logs-to-delete] (split-at max-count logs)]
    (doseq [log-to-delete logs-to-delete]
      (io/delete-file log-to-delete))
    (doseq [[^File log-file n]
            (reverse (map vector logs-to-rotate (iterate inc 1)))]
      (.renameTo log-file (io/file (format "%s.%03d" abs-path n))))))

(defn rotor-appender
  "Returns a rotating file appender."
  [& [{:keys [path max-size backlog]
       :or   {path     "./timbre-rotor.log"
              max-size (* 1024 1024)
              backlog  5}}]]
  {:enabled? true
   :fn
   (let [lock (Object.)
         max-size (long max-size)]
     (fn [data]
       (let [{:keys [output_]} data
             output-str (str (force output_) "\n")]
           (let [log (io/file path)]
             (try
               ;; all the filesystem manipulations are unsafe in the face of concurrency
               (locking lock
                 (when-not (.exists log)
                   (io/make-parents log))
                 (when (> (.length log) max-size)
                   (rotate-logs path backlog)))
               (spit path output-str :append true)
               (catch java.io.IOException _))))))})

;;;; Deprecated

(enc/deprecated
  (defn ^:no-doc ^:deprecated make-rotor-appender
    "Prefer `rotor-appender`."
    [& [appender-merge opts]]
    (merge (rotor-appender opts) appender-merge)))
