(ns taoensso.timbre.tests.called
  (:require [taoensso.timbre :as timbre :refer (trace debug info warn
                                                error fatal spy)]
            [taoensso.timbre.profiling :as profiling :refer (p profile)]))

(defn test-info []
  (info "Hi."))

