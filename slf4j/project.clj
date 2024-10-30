(defproject com.taoensso/timbre-slf4j "6.6.1"
  :author "Peter Taoussanis <https://www.taoensso.com>"
  :description "Timbre backend/provider for SLF4J API v2"
  :url "https://www.taoensso.com/timbre"

  :license
  {:name "Eclipse Public License - v 1.0"
   :url  "https://www.eclipse.org/legal/epl-v10.html"}

  :scm {:name "git" :url "https://github.com/taoensso/timbre"}

  :java-source-paths ["src/java"]
  :javac-options     ["--release" "8" "-g"] ; Support Java >= v8
  :dependencies      []

  :profiles
  {:provided
   {:dependencies
    [[org.clojure/clojure "1.12.0"]
     [org.slf4j/slf4j-api "2.0.16"]
     [com.taoensso/timbre "6.6.1"]]}

   :dev
   {:plugins
    [[lein-pprint  "1.3.2"]
     [lein-ancient "0.7.0"]]}}

  :aliases
  {"deploy-lib" ["do" #_["build-once"] ["deploy" "clojars"] ["install"]]})
