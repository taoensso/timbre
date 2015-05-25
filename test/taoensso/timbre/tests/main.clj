(ns taoensso.timbre.tests.main
  (:require [expectations    :as test   :refer :all]
            [taoensso.timbre :as timbre]))

;; (timbre/refer-timbre)

(comment (test/run-tests '[taoensso.timbre.tests.main]))

(defn- before-run {:expectations-options :before-run} [])
(defn- after-run  {:expectations-options :after-run}  [])

(expect true)
