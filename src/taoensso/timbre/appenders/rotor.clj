(ns taoensso.timbre.appenders.rotor
  {:author "Yutaka Matsubara"}
  (:import
   [java.io File FilenameFilter])
  (:require
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
  (let [abs-path                        (-> basepath io/file (.getAbsolutePath))
        logs                            (-> basepath matching-files sort)
        [logs-to-rotate logs-to-delete] (split-at max-count logs)]
    (doseq [log-to-delete logs-to-delete]
      (io/delete-file log-to-delete))
    (doseq [[^File log-file n]
            (reverse (map vector logs-to-rotate (iterate inc 1)))]
      (.renameTo log-file (io/file (format "%s.%03d" abs-path n))))))

(defn appender-fn [{:keys [ap-config output]}]
  (let [{:keys [path max-size backlog]
         :or   {max-size (* 1024 1024)
                backlog 5}} (:rotor ap-config)]
    (when path
      (try
        (when (> (.length (io/file path)) max-size)
          (rotate-logs path backlog))
        (spit path
              (str output "\n")
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
   :fn appender-fn})
