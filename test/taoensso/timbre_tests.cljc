(ns taoensso.timbre-tests
  (:require
   [clojure.test :as test :refer [deftest testing is]]
   #?(:clj [clojure.tools.logging :as ctl])
   [taoensso.encore :as enc :refer [throws? submap?] :rename {submap? sm?}]
   [taoensso.timbre :as timbre]
   #?(:clj [taoensso.timbre.tools.logging :as ttl]))

  #?(:cljs
     (:require-macros
      [taoensso.timbre-tests :refer [log-data]])))

(comment
  (remove-ns      'taoensso.timbre-tests)
  (test/run-tests 'taoensso.timbre-tests))

;;;; Utils, etc.

(defn capturing-appender
  ([ ] (capturing-appender nil))
  ([m]
   (let [data_ (volatile! nil)]
     (conj
       (with-meta
         {:enabled? true
          :fn (fn [data] (vreset! data_ data))}
         {:data_ data_})
       m))))

(comment (capturing-appender {:min-level :info :ns-filter "*"}))

(defn- captured [appender]
  (when-let [data (deref (get (meta appender) :data_))]
    (update data :msg_ force)))

#?(:clj
   (defmacro log-data
     ([ns level   m-config  m-appender args]
      `(log-data ~m-config ~m-appender
         (timbre/log! ~level :p ~args {:loc {:ns ~ns}})))

     ([                    form] `(log-data nil nil ~form))
     ([m-config m-appender form]
      `(let [appender# (capturing-appender ~m-appender)]
         (binding [timbre/*config*
                   (conj timbre/default-config ~m-config
                     {:appenders {:capturing-appender appender#}})]
           (do ~form))
         (captured appender#)))))

(comment (macroexpand '(log-data "my-ns" :info {:min-level :trace} {} ["x"])))

;;;; Core

(deftest _set-ns-min-level
  [(is (= (timbre/set-ns-min-level {:min-level :info         } "a" :trace) {:min-level [["a" :trace] ["*" :info]]}))
   (is (= (timbre/set-ns-min-level {:min-level [["a" :debug]]} "a" :trace) {:min-level [["a" :trace]]}))
   (is (= (timbre/set-ns-min-level {:min-level [["a" :debug]]} "a" nil)    {:min-level nil}))

   (is (= (timbre/set-ns-min-level {:min-level [["a.b" :trace] ["a.c" :debug] ["a.*" :info] ["a.c" :error]]}
            "a.c" :report)
         {:min-level [["a.c" :report] ["a.b" :trace] ["a.*" :info]]}))

   (is (= (->
            (timbre/set-ns-min-level {:min-level :info} "foo" :debug)
            (timbre/set-ns-min-level "foo" nil))
         {:min-level :info}))])

;;;; High-level

(deftest levels
  [(testing "Levels.global/basic"
     [(is (map? (log-data "ns" :trace {:min-level nil}    {} [])) "call >= default (:trace)")
      (is (map? (log-data "ns" :trace {:min-level :trace} {} [])) "call >= min")
      (is (nil? (log-data "ns" :trace {:min-level :info}  {} [])) "call <  min")
      (is (map? (log-data "ns" :info  {:min-level :info}  {} [])) "call >= min")

      (is (not     (:error-level? (log-data "ns" :info  {:min-level :info}  {} []))))
      (is (boolean (:error-level? (log-data "ns" :error {:min-level :error} {} []))))])

   (testing "Levels.global/by-ns"
     [(is (map? (log-data "ns.2" :trace  {:min-level [["ns.1" :info]                         ]} {} [])) "call >= default (:trace)")
      (is (map? (log-data "ns.2" :info   {:min-level [["ns.1" :warn] ["ns.2"           :info]]} {} [])) "call >= match")
      (is (map? (log-data "ns.2" :info   {:min-level [["ns.1" :warn] [#{"ns.3" "ns.2"} :info]]} {} [])) "call >= match")
      (is (map? (log-data "ns.2" :info   {:min-level [["ns.1" :warn] ["*"              :info]]} {} [])) "call >= *")
      (is (map? (log-data "ns.1" :info   {:min-level [["ns.1" :info] ["*"              :warn]]} {} [])) "Ordered pattern search")])

   (testing "Levels.appender/basic"
     [(is (map? (log-data "ns" :info {:min-level :info}   {:min-level :info}   [])) "call >= both global and appender")
      (is (nil? (log-data "ns" :info {:min-level :report} {:min-level :info}   [])) "call <  global")
      (is (nil? (log-data "ns" :info {:min-level :info}   {:min-level :report} [])) "call <  appender")])

   (testing "Levels.appender/by-ns"
     [(is (map? (log-data "ns" :info {:min-level [["ns" :info]]}  {:min-level :info}          [])) "call >= both global and appender")
      (is (map? (log-data "ns" :info {:min-level [["ns" :info]]}  {:min-level [["ns" :info]]} [])) "call >= both global and appender")
      (is (nil? (log-data "ns" :info {:min-level [["ns" :warn]]}  {:min-level [["ns" :info]]} [])) "call <  global")
      (is (nil? (log-data "ns" :info {:min-level [["ns" :info]]}  {:min-level [["ns" :warn]]} [])) "call <  appender")])])

(deftest namespaces
  [(testing "Namespaces/global"
     [(is (map? (log-data "ns.1.a" :report {:min-level :trace :ns-filter "ns.1.*"}                         {} [])))
      (is (nil? (log-data "ns.1.b" :report {:min-level :trace :ns-filter "ns.2.*"}                         {} [])))
      (is (nil? (log-data "ns.1.c" :report {:min-level :trace :ns-filter {:allow "ns.1.*" :deny "ns.1.c"}} {} [])) ":deny match")])

   (testing "Namespaces/appender"
     [(is (map? (log-data "ns.1.a" :report {:min-level :trace :ns-filter "ns.1.*"} {:ns-filter "ns.1.*"} [])) "both global and appender allowed")
      (is (nil? (log-data "ns.1.a" :report {:min-level :trace :ns-filter "ns.2.*"} {:ns-filter "ns.1.*"} [])) "global   denied")
      (is (nil? (log-data "ns.1.a" :report {:min-level :trace :ns-filter "ns.1.*"} {:ns-filter "ns.2.*"} [])) "appender denied")])])

(deftest middleware
  [(is (= :bar (:foo (log-data "ns" :info {:middleware [(fn [m] (assoc m :foo :bar))]} {} []))))
   (is (= nil        (log-data "ns" :info {:middleware [(fn [_] nil)]} {} [])))])

(deftest special-args
  [(testing "Special-args/errors"
     [(is (nil?       (:?err  (log-data "ns" :report {} {} ["foo"                  ]))))
      (is (enc/error? (:?err  (log-data "ns" :report {} {} [(ex-info "ex" {}) "foo"]))) "First-arg ex -> :?err")
      (is (enc/error? (:?err  (log-data "ns" :report {} {} [(ex-info "ex" {})      ]))) "First-arg ex -> :?err")
      (is (= ["foo"]  (:vargs (log-data "ns" :report {} {} [(ex-info "ex" {}) "foo"]))) "First-arg ex dropped from vargs")
      (is (= []       (:vargs (log-data "ns" :report {} {} [(ex-info "ex" {})      ]))) "First-arg ex dropped from vargs")])

   (testing "Special-args/meta"
     [(is (nil?      (:?meta (log-data "ns" :report {} {} [               "foo"]))))
      (is (nil?      (:?meta (log-data "ns" :report {} {} [       {:a :A} "foo"]))))
      (is (map?      (:?meta (log-data "ns" :report {} {} [^:meta {:a :A} "foo"]))) "First-arg ^:meta {} -> :?meta")
      (is (= ["foo"] (:vargs (log-data "ns" :report {} {} [^:meta {:a :A} "foo"]))) "First-arg ^:meta {} dropped from vargs")])])

(deftest output
  [(is (= "o1" @(:output_ (log-data "ns" :report {:output-fn (fn [data] "o1")} {} ["a1"]))))
   (is (= "o2" @(:output_ (log-data "ns" :report
                            {:output-fn (fn [data] "o1")} ; Config
                            {:output-fn (fn [data] "o2")} ; Appender
                            ["a1"])))

     "Appender :output-fn overrides top-level :output-fn")

   (is (= @(:output_ (log-data "ns" :report {:output-fn :output-opts :output-opts {:k :v1}}
                       {} ["a1"]))
         {:k :v1})

     "Log data includes :output-opts")

   (is (= @(:output_ (log-data "ns" :report
                       {:output-fn :output-opts :output-opts {:k :v1}} ; Config
                       {                        :output-opts {:k :v2}} ; Appender
                       ["a1"]))
         {:k :v2})

     "Appender :output-opts overrides top-level :output-opts")])

;;;; Interop

#?(:clj (def dt-pred (enc/pred (fn [x] (instance? java.util.Date x)))))
#?(:clj
   (deftest _interop
     [(testing "tools.logging -> Timbre"
        (ttl/use-timbre)
        [                            (is (sm? (log-data (ctl/info     "a" "b" "c")) {:level :info,  :?ns-str "taoensso.timbre-tests", :instant dt-pred, :msg_ "a b c"}))
         (is (let [ex (ex-info "Ex" {})] (sm? (log-data (ctl/error ex "a" "b" "c")) {:level :error, :?ns-str "taoensso.timbre-tests", :instant dt-pred, :msg_ "a b c", :?err ex})))])]))

;;;;

#?(:cljs
   (defmethod test/report [:cljs.test/default :end-run-tests] [m]
     (when-not (test/successful? m)
       ;; Trigger non-zero `lein test-cljs` exit code for CI
       (throw (ex-info "ClojureScript tests failed" {})))))

#?(:cljs (test/run-tests))
