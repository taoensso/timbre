(defproject timbre "0.5.1-SNAPSHOT"
  :description "Simple, flexible, all-Clojure logging. No XML!"
  :url "https://github.com/ptaoussanis/timbre"
  :license {:name "Eclipse Public License"}
  :dependencies [[com.draines/postal "1.7.1"]]
  :profiles {:1.3   {:dependencies [[org.clojure/clojure "1.3.0"]]}
             :1.4   {:dependencies [[org.clojure/clojure "1.4.0"]]}
             :1.5   {:dependencies [[org.clojure/clojure "1.5.0-master-SNAPSHOT"]]}}
  :repositories {"sonatype" {:url "http://oss.sonatype.org/content/repositories/releases"
                             :snapshots false
                             :releases {:checksum :fail :update :always}}
                 "sonatype-snapshots" {:url "http://oss.sonatype.org/content/repositories/snapshots"
                                       :snapshots true
                                       :releases {:checksum :fail :update :always}}}
  :aliases {"all" ["with-profile" "1.3:1.4:1.5"]}
  :min-lein-version "2.0.0"
  :warn-on-reflection true)