(ns taoensso.timbre.graal-test
  (:require [taoensso.timbre :refer [info]])
  (:gen-class))

(defn -main [& args]
  (info args))
