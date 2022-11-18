#!/usr/bin/env bb

(ns graal-test
  (:require
   [babashka.fs :as fs]
   [babashka.process :refer [shell]]
   [clojure.string :as str]))

(defn uberjar []
  (let [command "lein with-profiles +graal-test uberjar"
        command (if (fs/windows?)
                  (if (fs/which "lein")
                    command
                    ;; assume powershell module
                    (str "powershell.exe -command " (pr-str command)))
                  command)]
    (shell command)))

(defn executable [dir name]
  (-> (fs/glob dir (if (fs/windows?)
                     (str name ".{exe,bat,cmd}")
                     name))
      first
      fs/canonicalize
      str))

(defn native-image []
  (let [graalvm-home (System/getenv "GRAALVM_HOME")
        bin-dir (str (fs/file graalvm-home "bin"))]
    (shell (executable bin-dir "gu") "install" "native-image")
    (shell (executable bin-dir "native-image") "-jar" "target/graal.jar" "--no-fallback" "graal_test")))

(defn test-native-image []
  (let [{:keys [out]}
        (shell {:out :string} (executable "." "graal_test") "1" "2" "3")]
    (assert (str/includes? out (str '("1" "2" "3"))) out)
    (println "Native image works!")))
