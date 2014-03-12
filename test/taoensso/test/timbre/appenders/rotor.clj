(ns taoensso.test.timbre.appenders.rotor
  (:require [taoensso.timbre.appenders.rotor :as rotor :refer :all]
            [clojure.test                              :refer :all]
            [clojure.java.io                           :refer [file]]))

(defn with-temp-dir-containing-log-files
  "Call f with the temp directory name, that directory having n log
  files created within it"
  [n f]
  (let [tmp-dir (java.io.File/createTempFile "test" "")
        log-file-basename "log"
        log-files (into [log-file-basename]
                        (map #(format "%s.%03d" log-file-basename %) (range 1 n)))]
    (.delete tmp-dir)
    (.mkdirs tmp-dir)
    (doseq [filename log-files] (.createNewFile (file tmp-dir filename)))
    (try
      (f (.getAbsolutePath (file tmp-dir (first log-files))))
      (finally
        (doseq [filename log-files] (.delete (file tmp-dir filename)))
        (.delete (file tmp-dir))))))

(deftest test-rotor
  (testing "rotating logs"
    (testing "when rotating with a full backlog of files, the last should be deleted"
      (with-temp-dir-containing-log-files 5
        (fn [basepath]
          (#'rotor/rotate-logs basepath 2)
          (is (not (.exists (file (str basepath))))
              "log should have been rotated to log.001")
          (is (.exists (file (str basepath ".001")))
              "log.001 should remain")
          (is (.exists (file (str basepath ".002")))
              "log.002 should remain")
          (is (not (.exists (file (str basepath ".003"))))
              "log.003 should be deleted because it is past the max-count threshold")
          (is (not (.exists (file (str basepath ".004"))))
              "log.004 should be deleted because it is past the max-count threshold"))))))
