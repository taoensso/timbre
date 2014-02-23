(ns taoensso.timbre.tests.main
  (:require [expectations    :as test   :refer :all]
            [taoensso.timbre :as timbre]))

(timbre/refer-timbre)

(defn- before-run {:expectations-options :before-run} [])
(defn- after-run  {:expectations-options :after-run}  [])

(expect true) ; TODO Add tests (PRs welcome!)
