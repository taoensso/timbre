(ns taoensso.timbre.tests.main
  (:require [expectations    :as test   :refer :all]
            [taoensso.timbre :as timbre :refer (trace debug info warn
                                                error fatal spy)]
            [taoensso.timbre.profiling :as profiling :refer (p profile)]))

(expect true) ; TODO Add tests (PRs welcome!)