(defproject com.taoensso/timbre "6.2.0"
  :author "Peter Taoussanis <https://www.taoensso.com>"
  :description "Pure Clojure/Script logging library"
  :url "https://github.com/ptaoussanis/timbre"
  :min-lein-version "2.3.3"

  :license
  {:name "Eclipse Public License 1.0"
   :url  "http://www.eclipse.org/legal/epl-v10.html"}

  :global-vars
  {*warn-on-reflection* true
   *assert*             true
   *unchecked-math*     false #_:warn-on-boxed}

  :dependencies
  [[com.taoensso/encore "3.59.0"]
   [io.aviso/pretty     "1.1.1"] ; Temporarily use old release, Ref. #369
  ]

  :plugins
  [[lein-pprint    "1.3.2"]
   [lein-ancient   "0.7.0"]
   [lein-codox     "0.10.8"]
   [lein-cljsbuild "1.1.8"]]

  :profiles
  {;; :default [:base :system :user :provided :dev]
   :server-jvm {:jvm-opts ^:replace ["-server"]}
   :provided {:dependencies [[org.clojure/clojurescript "1.11.60"]
                             [org.clojure/clojure "1.11.1"]]}
   :c1.11    {:dependencies [[org.clojure/clojure "1.11.1"]]}
   :c1.10    {:dependencies [[org.clojure/clojure "1.10.1"]]}
   :c1.9     {:dependencies [[org.clojure/clojure "1.9.0"]]}

   :depr     {:jvm-opts ["-Dtaoensso.elide-deprecated=true"]}
   :dev      [:c1.11 :test :server-jvm #_:depr :community #_:extra] ; TODO :depr

   :community
   {:dependencies
    [[irclj                   "0.5.0-alpha4"]
     [org.graylog2/gelfclient "1.5.1"
      :exclusions [com.fasterxml.jackson.core/jackson-core]]
     [org.julienxx/clj-slack  "0.8.0"]
     [org.clojure/java.jdbc   "0.7.12"]
     [com.mchange/c3p0        "0.9.5.5"]
     [cheshire                "5.11.0"]
     [ymilky/franzy           "0.0.1"]
     [com.newrelic.agent.java/newrelic-agent "8.4.0"]
     [net.java.dev.jna/jna    "5.13.0"]
     [raven-clj               "1.7.0"]
     [congomongo              "2.6.0"]
     [server-socket           "1.0.0"]
     [org.zeromq/cljzmq       "0.1.4"]
     [cljs-node-io            "1.1.2"] ; Node spit appender
     ]}

   :extra
   {:source-paths [         "src"                "extra/src"]
    :test-paths   ["test" #_"src" "extra/test" #_"extra/src"]}

   :test
   {:dependencies
    [[org.clojure/test.check    "1.1.1"]
     [org.clojure/tools.logging "1.2.4"]
     [com.taoensso/nippy        "3.2.0"]
     [com.taoensso/carmine      "3.2.0"
      :exclusions [com.taoensso/timbre]]
     [com.draines/postal        "2.0.5"]]}

   :graal-test
   {:dependencies [[org.clojure/clojure "1.11.1"]
                   [com.github.clj-easy/graal-build-time "0.1.4"]]
    :main taoensso.timbre.graal-test
    :aot [taoensso.timbre.graal-test]
    :uberjar-name "graal-test.jar"}}

  :test-paths ["test" #_"src"]

  :cljsbuild
  {:test-commands {"node" ["node" "target/test.js"]}
   :builds
   [{:id :main
     :source-paths ["src"]
     :compiler
     {:output-to "target/main.js"
      :optimizations :advanced}}

    {:id :test
     :source-paths ["src" "test"]
     :compiler
     {:output-to "target/test.js"
      :target :nodejs
      :optimizations :simple}}]}

  :aliases
  {"start-dev"  ["with-profile" "+dev" "repl" ":headless"]
   ;; "deploy-lib" ["do" ["build-once"] ["deploy" "clojars"] ["install"]]
   "deploy-lib" ["with-profile" "+extra"
                 "do" ["build-once"] ["deploy" "clojars"] ["install"]]
   "build-once" ["do" ["clean"] "cljsbuild" "once"]

   "test-cljs"  ["with-profile" "+test" "cljsbuild" "test"]
   "test-all"
   ["do" ["clean"]
    "with-profile" "+c1.11:+c1.10:+c1.9" "test,"
    "test-cljs"]}

  :repositories
  {"sonatype-oss-public"
   "https://oss.sonatype.org/content/groups/public/"})
