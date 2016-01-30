(defproject com.taoensso/timbre "4.3.0-RC1"
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
   [com.taoensso/encore "2.33.0"]
   [io.aviso/pretty     "0.1.21"]]

  :plugins
  [[lein-pprint  "1.1.2"]
   [lein-ancient "0.6.8"]
   [lein-codox   "0.9.1"]]

  :profiles
  {;; :default [:base :system :user :provided :dev]
   :server-jvm {:jvm-opts ^:replace ["-server"]}
   :1.7  {:dependencies [[org.clojure/clojure "1.7.0"]]}
   :1.8  {:dependencies [[org.clojure/clojure "1.8.0-RC5"]]}
   :test {:dependencies [[org.clojure/tools.logging "0.3.1"]

                         ;; Appender deps
                         [com.taoensso/nippy   "2.10.0"]
                         [com.taoensso/carmine "2.12.2"]
                         [com.draines/postal   "1.11.4"]
                         [irclj                "0.5.0-alpha4"]
                         [org.graylog2/gelfclient "1.3.1"]]}
   :dev
   [:1.7 :test
    {:dependencies [[org.clojure/clojurescript "1.7.28"]]
     :plugins
     [;; These must be in :dev, Ref. https://github.com/lynaghk/cljx/issues/47:
      [lein-cljsbuild      "1.1.2"]]}]}

  :source-paths ["src" "target/classes"]
  :test-paths   ["src" "test" "target/test-classes"]

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
  :prep-tasks ["javac" "compile"]

  :codox
  {:language :clojure
   :source-uri "https://github.com/ptaoussanis/timbre/blob/master/{filepath}#L{line}"}

  :aliases
  {"test-all"   ["do" "clean,"
                 "with-profile" "+1.7:+1.8" "test"
                 "with-profile" "+test" "cljsbuild" "test"]
   "build-once" ["do" "clean," "cljsbuild" "once" "main"]
   "deploy-lib" ["do" "build-once," "deploy" "clojars," "install"]
   "start-dev"  ["with-profile" "+server-jvm" "repl" ":headless"]}

  :repositories {"sonatype-oss-public"
                 "https://oss.sonatype.org/content/groups/public/"})
