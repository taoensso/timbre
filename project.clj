(defproject com.taoensso/timbre "6.2.2"
  :author "Peter Taoussanis <https://www.taoensso.com>"
  :description "Pure Clojure/Script logging library"
  :url "https://github.com/taoensso/timbre"

  :license
  {:name "Eclipse Public License - v 1.0"
   :url  "https://www.eclipse.org/legal/epl-v10.html"}

  :dependencies
  [[com.taoensso/encore "3.68.0"]
   [io.aviso/pretty     "1.4.4"]]

  :profiles
  {;; :default [:base :system :user :provided :dev]
   :provided {:dependencies [[org.clojure/clojurescript "1.11.121"]
                             [org.clojure/clojure       "1.11.1"]]}
   :c1.11    {:dependencies [[org.clojure/clojure       "1.11.1"]]}
   :c1.10    {:dependencies [[org.clojure/clojure       "1.10.1"]]}
   :c1.9     {:dependencies [[org.clojure/clojure       "1.9.0"]]}

   :community
   {:dependencies
    [[irclj                   "0.5.0-alpha4"]
     [org.graylog2/gelfclient "1.5.1"
      :exclusions [com.fasterxml.jackson.core/jackson-core]]
     [org.julienxx/clj-slack  "0.8.1"]
     [org.clojure/java.jdbc   "0.7.12"]
     [com.mchange/c3p0        "0.9.5.5"]
     [cheshire                "5.12.0"]
     [ymilky/franzy           "0.0.1"]
     [com.newrelic.agent.java/newrelic-agent "8.6.0"]
     [net.java.dev.jna/jna    "5.13.0"]
     [raven-clj               "1.7.0"]
     [congomongo              "2.6.0"]
     [server-socket           "1.0.0"]
     [org.zeromq/cljzmq       "0.1.4"]
     [cljs-node-io            "1.1.2"] ; Node spit appender
     ]}

   :deploy
   {:source-paths [         "src"                 "deploy/src"]
    :test-paths   ["test" #_"src" "deploy/test" #_"deploy/src"]}

   :test
   {:jvm-opts ["-Dtaoensso.elide-deprecated=true"]
    :global-vars
    {*warn-on-reflection* true
     *assert*             true
     *unchecked-math*     false #_:warn-on-boxed}

    :dependencies
    [[org.clojure/test.check    "1.1.1"]
     [org.clojure/tools.logging "1.2.4"]
     [com.taoensso/nippy        "3.3.0-RC2"]
     [com.taoensso/carmine      "3.3.0-RC1"
      :exclusions [com.taoensso/timbre]]
     [com.draines/postal        "2.0.5"]]}

   :graal-tests
   {:dependencies [[org.clojure/clojure "1.11.1"]
                   [com.github.clj-easy/graal-build-time "1.0.5"]]
    :main taoensso.graal-tests
    :aot [taoensso.graal-tests]
    :uberjar-name "graal-tests.jar"}

   :dev
   [:c1.11 :test :community #_:deploy
    {:jvm-opts ["-server"]
     :plugins
     [[lein-pprint    "1.3.2"]
      [lein-ancient   "0.7.0"]
      [lein-cljsbuild "1.1.8"]
      [com.taoensso.forks/lein-codox "0.10.10"]]

     :codox
     {:language #{:clojure :clojurescript}
      :base-language :clojure}}]}

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
  {"start-dev"     ["with-profile" "+dev" "repl" ":headless"]
   "build-once"    ["do" ["clean"] ["cljsbuild" "once"]]
   ;; "deploy-lib" ["do" ["build-once"] ["deploy" "clojars"] ["install"]]
   "deploy-lib"    ["with-profile" "+deploy" "do"
                    ["build-once"] ["deploy" "clojars"] ["install"]]

   "test-clj"   ["with-profile" "+c1.11:+c1.10:+c1.9" "test"]
   "test-cljs"  ["with-profile" "+test" "cljsbuild"   "test"]
   "test-all"   ["do" ["clean"] ["test-clj"] ["test-cljs"]]})
