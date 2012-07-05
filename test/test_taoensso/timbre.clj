(ns test-taoensso.timbre
  (:use [clojure.test]
        [taoensso.timbre :as timbre :only (info)]
        [taoensso.timbre.profiling :as profiling :only (p profile)]))

(deftest test-nothing) ; TODO