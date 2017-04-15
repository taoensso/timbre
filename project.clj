(defproject com.taoensso/timbre "4.10.0"
  :author "Peter Taoussanis <https://www.taoensso.com>"
  :description "Pure Clojure/Script logging library"
  :url "https://github.com/ptaoussanis/timbre"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "Same as Clojure"}
  :min-lein-version "2.3.3"
  :global-vars {*warn-on-reflection* true
                *assert*             true}

  :dependencies
  [[org.clojure/clojure "1.5.1"]
   [com.taoensso/encore "2.91.0"]
   [io.aviso/pretty     "0.1.33"]]

  :plugins
  [[lein-pprint  "1.1.2"]
   [lein-ancient "0.6.10"]
   [lein-codox   "0.10.3"]]

  :profiles
  {;; :default [:base :system :user :provided :dev]
   :server-jvm {:jvm-opts ^:replace ["-server"]}
   :1.5  {:dependencies [[org.clojure/clojure "1.5.1"]]}
   :1.6  {:dependencies [[org.clojure/clojure "1.6.0"]]}
   :1.7  {:dependencies [[org.clojure/clojure "1.7.0"]]}
   :1.8  {:dependencies [[org.clojure/clojure "1.8.0"]]}
   :1.9  {:dependencies [[org.clojure/clojure "1.9.0-alpha10"]]}
   :test
   {:dependencies
    [[org.clojure/tools.logging "0.3.1"]

     ;;; Appender deps
     [com.taoensso/nippy      "2.13.0"]
     [com.taoensso/carmine    "2.16.0"]
     [com.draines/postal      "2.0.2"]
     [irclj                   "0.5.0-alpha4"]
     [org.graylog2/gelfclient "1.4.1" :exclusions [com.fasterxml.jackson.core/jackson-core]]
     [org.julienxx/clj-slack  "0.5.4"]
     [org.clojure/java.jdbc   "0.6.1"]
     [com.mchange/c3p0        "0.9.5.2"]
     [cheshire                "5.6.3"]
     [ymilky/franzy           "0.0.1"]
     [com.newrelic.agent.java/newrelic-agent "3.31.1"]
     [raven-clj               "1.5.0"]]}

   :dev
   [:1.9 :test :server-jvm
    {:dependencies [[org.clojure/clojurescript "1.9.521"]]
     :plugins
     [;; These must be in :dev, Ref. https://github.com/lynaghk/cljx/issues/47:
      [com.keminglabs/cljx "0.6.0"]
      [lein-cljsbuild      "1.1.5"]]}]}

  ;; :jar-exclusions [#"\.cljx|\.DS_Store"]
  :source-paths ["src" "target/classes"]
  :test-paths   ["src" "test" "target/test-classes"]

  :cljx
  {:builds
   [{:source-paths ["src"]        :rules :clj  :output-path "target/classes"}
    {:source-paths ["src"]        :rules :cljs :output-path "target/classes"}
    {:source-paths ["src" "test"] :rules :clj  :output-path "target/test-classes"}
    {:source-paths ["src" "test"] :rules :cljs :output-path "target/test-classes"}]}

  :cljsbuild
  {:test-commands {"node"    ["node" :node-runner "target/tests.js"]
                   "phantom" ["phantomjs" :runner "target/tests.js"]}
   :builds
   [{:id "main"
     :source-paths   ["src" "target/classes"]
     ;; :notify-command ["terminal-notifier" "-title" "cljsbuild" "-message"]
     :compiler       {:output-to "target/main.js"
                      :optimizations :advanced
                      :pretty-print false}}]}

  :auto-clean false
  :prep-tasks [["cljx" "once"] "javac" "compile"]

  :codox
  {:language :clojure
   :source-uri "https://github.com/ptaoussanis/timbre/blob/master/{filepath}#L{line}"}

  :aliases
  {"test-all"   ["do" "clean," "cljx" "once,"
                 "with-profile" "+1.9:+1.8:+1.7:+1.6:+1.5" "test"
                 "with-profile" "+test" "cljsbuild" "test"]
   "build-once" ["do" "clean," "cljx" "once," "cljsbuild" "once" "main"]
   "deploy-lib" ["do" "build-once," "deploy" "clojars," "install"]
   "start-dev"  ["with-profile" "+dev" "repl" ":headless"]}

  :repositories {"sonatype-oss-public"
                 "https://oss.sonatype.org/content/groups/public/"})
