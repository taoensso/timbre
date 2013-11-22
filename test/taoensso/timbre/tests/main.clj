(ns taoensso.timbre.tests.main
  (:require [expectations    :as test   :refer :all]
            [taoensso.timbre :as timbre :refer (trace debug info warn
                                                error fatal spy)]
            [taoensso.timbre.profiling :as profiling :refer (p profile)]
            taoensso.timbre.tests.called))

(defn- before-run {:expectations-options :before-run} [])
(defn- after-run  {:expectations-options :after-run}  [])

; Helper functions.

(defn- isolate-config-changes
  "Restore the timbre/config to its original state after a test runs."
  {:expectations-options :in-context}
  [work]
  (let [orig-config @timbre/config]
    (work)
    (reset! timbre/config orig-config)))

(defn- line-count
  [string]
  (->> string
       seq
       (filter #{\newline})
       count))


; Verify stdout behaviour and log levels.

(expect 0
        (line-count (with-out-str (trace "hi"))))

(expect 1
        (line-count (with-out-str (do (timbre/set-level! :trace)
                                      (trace "hi")))))

(expect 1
        (line-count (with-out-str (info "hi"))))

(expect 0
        (line-count (with-out-str (do (timbre/set-level! :warn)
                                      (info "hi")))))

(expect 1
        (line-count (with-out-str (taoensso.timbre.tests.called/test-info))))

(expect 2
        (line-count (with-out-str (do (info "hi")
                                      (taoensso.timbre.tests.called/test-info)))))


; Verify blacklists/whitelists

(defn- blacklist
  [namespaces]
  (timbre/set-config! [:ns-blacklist] namespaces))

(defn- whitelist
  [namespaces]
  (timbre/set-config! [:ns-whitelist] namespaces))


(expect 1
        (line-count (with-out-str (do (blacklist ["taoensso.timbre.tests.main"])
                                      (info "hi")
                                      (taoensso.timbre.tests.called/test-info)))))

(expect 1
        (line-count (with-out-str (do (whitelist ["taoensso.timbre.tests.main"])
                                      (info "hi")
                                      (taoensso.timbre.tests.called/test-info)))))
