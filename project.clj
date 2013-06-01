(defproject com.taoensso/timbre "1.6.0"
  :description "Clojure logging & profiling library"
  :url "https://github.com/ptaoussanis/timbre"
  :license {:name "Eclipse Public License"}
  :dependencies [[org.clojure/clojure     "1.4.0"]
                 [org.clojure/tools.macro "0.1.1"]
                 [clj-stacktrace          "0.2.5"]]
  :profiles {:1.4  {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5  {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :dev  {:dependencies []}
             :test {:dependencies []}}
  :aliases {"test-all" ["with-profile" "test,1.4:test,1.5" "test"]}
  :min-lein-version "2.0.0"
  :warn-on-reflection true)
