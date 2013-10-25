(ns taoensso.timbre.appenders.rotor
  (:import
   [java.io File FilenameFilter])
  (:require
   [clj-stacktrace.repl   :as stacktrace]
   [clojure.java.io :as io]
   [taoensso.timbre :as t]))

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
        logs (->> basepath
                  matching-files
                  (take max-count)
                  (map (fn [^File x] (.getAbsolutePath x)))
                  sort
                  reverse)
        num-logs (count logs)
        overflow? (> num-logs max-count)]
    (when overflow?
      (io/delete-file (first logs)))
    (loop [[log & more] (if overflow? (rest logs) logs) n num-logs]
      (when log
        (.renameTo (io/file log) (io/file (format "%s.%03d" abs-path n)))
        (recur more (dec n))))))

(defn appender-fn [{:keys [ap-config prefix throwable message]}]
  (let [{:keys [path max-size backlog]
         :or   {max-size (* 1024 1024)
                backlog 5}} (:rotor ap-config)]
    (when path
      (try
        (when (> (.length (io/file path)) max-size)
          (rotate-logs path backlog))
        (spit path
              (with-out-str
                (t/str-println prefix "-" message
                       (t/stacktrace throwable)))
              :append true)
        (catch java.io.IOException _)))))

(def rotor-appender
  {:doc (str "Simple Rotating File Appender.\n"
             "Needs :rotor config map in :shared-appender-config, e.g.:
             {:path \"logs/app.log\"
              :max-size (* 512 1024)
              :backlog 5}")
   :min-level nil
   :enabled? true
   :async? false
   :limit-per-msecs nil
   :fn appender-fn})
