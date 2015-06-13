(defproject com.taoensso/timbre "4.0.1"
  :author "Peter Taoussanis <https://www.taoensso.com>"
  :description "Clojure/Script logging & profiling library"
  :url "https://github.com/ptaoussanis/timbre"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "Same as Clojure"}
  :min-lein-version "2.3.3"
  :global-vars {*warn-on-reflection* true
                *assert*             true}

  :dependencies
  [[org.clojure/clojure "1.4.0"]
   [com.taoensso/encore "1.37.0"]
   [io.aviso/pretty     "0.1.18"]]

  :plugins
  [[lein-pprint       "1.1.2"]
   [lein-ancient      "0.6.7"]
   [lein-expectations "0.0.8"]
   [lein-autoexpect   "1.5.0"]
   [codox             "0.8.12"]]

  :profiles
  {;; :default [:base :system :user :provided :dev]
   :server-jvm {:jvm-opts ^:replace ["-server"]}
   :1.5  {:dependencies [[org.clojure/clojure "1.5.1"]]}
   :1.6  {:dependencies [[org.clojure/clojure "1.6.0"]]}
   :1.7  {:dependencies [[org.clojure/clojure "1.7.0-RC1"]]}
   :test {:dependencies [[expectations              "2.1.0"]
                         [org.clojure/tools.logging "0.3.1"]

                         ;; Appender deps
                         [com.taoensso/nippy   "2.9.0"]
                         [com.taoensso/carmine "2.11.1"]
                         [com.draines/postal   "1.11.3"]
                         [irclj                "0.5.0-alpha4"]]}
   :dev
   [:1.7 :test
    {:dependencies [[org.clojure/clojurescript "0.0-3297"]]
     :plugins
     [;; These must be in :dev, Ref. https://github.com/lynaghk/cljx/issues/47:
      [com.keminglabs/cljx "0.6.0"]
      [lein-cljsbuild      "1.0.6"]]}]}

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

  :codox {:language :clojure ; [:clojure :clojurescript] cljsbuild  ; No support?
          :sources  ["target/classes"]
          :src-linenum-anchor-prefix "L"
          :src-dir-uri "http://github.com/ptaoussanis/encore/blob/master/src/"
          :src-uri-mapping {#"target/classes"
                            #(.replaceFirst (str %) "(.cljs$|.clj$)" ".cljx")}}

  :aliases
  {"test-all"   ["do" "clean," "cljx" "once,"
                 "with-profile" "default:+1.5:+1.6:+1.7" "expectations,"
                 "with-profile" "+test" "cljsbuild" "test"]
   "test-auto"  ["with-profile" "+test" "autoexpect"]
   "build-once" ["do" "clean," "cljx" "once," "cljsbuild" "once" "main"]
   "deploy-lib" ["do" "build-once," "deploy" "clojars," "install"]
   "start-dev"  ["with-profile" "+server-jvm" "repl" ":headless"]}

  :repositories {"sonatype-oss-public"
                 "https://oss.sonatype.org/content/groups/public/"})
