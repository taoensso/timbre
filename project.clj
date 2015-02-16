(defproject com.taoensso/timbre "3.3.1"
  :author "Peter Taoussanis <https://www.taoensso.com>"
  :description "Clojure logging & profiling library"
  :url "https://github.com/ptaoussanis/timbre"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"
            :distribution :repo
            :comments "Same as Clojure"}
  :min-lein-version "2.3.3"
  :global-vars {*warn-on-reflection* true
                *assert* true}

  :dependencies
  [[org.clojure/clojure "1.4.0"]
   [com.taoensso/encore "1.21.0"]
   [io.aviso/pretty     "0.1.16"]]

  :profiles
  {;; :default [:base :system :user :provided :dev]
   :server-jvm {:jvm-opts ^:replace ["-server"]}
   :1.5  {:dependencies [[org.clojure/clojure "1.5.1"]]}
   :1.6  {:dependencies [[org.clojure/clojure "1.6.0"]]}
   :test {:dependencies [[expectations              "2.0.13"]
                         [org.clojure/test.check    "0.7.0"]
                         [org.clojure/tools.logging "0.3.1"]

                         ;; Appender dependencies
                         [com.taoensso/nippy   "2.7.1"]
                         [com.taoensso/carmine "2.9.0"]
                         [com.draines/postal   "1.11.3"]
                         [irclj                "0.5.0-alpha4"]]
          :plugins [[lein-expectations "0.0.8"]
                    [lein-autoexpect   "1.4.2"]]}
   :dev
   [:1.6 :test
    {:dependencies []
     :plugins [[lein-ancient "0.5.4"]
               [codox        "0.8.10"]]}]}

  :test-paths ["test" "src"]

  :aliases
  {"test-all"   ["with-profile" "default:+1.5:+1.6" "expectations"]
   ;; "test-all"   ["with-profile" "default:+1.6" "expectations"]
   "test-auto"  ["with-profile" "+test" "autoexpect"]
   "deploy-lib" ["do" "deploy" "clojars," "install"]
   "start-dev"  ["with-profile" "+server-jvm" "repl" ":headless"]}

  :repositories {"sonatype-oss-public"
                 "https://oss.sonatype.org/content/groups/public/"})
