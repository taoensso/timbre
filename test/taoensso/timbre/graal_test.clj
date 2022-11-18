(ns taoensso.timbre.graal-test
  (:require [taoensso.timbre :as timbre])
  (:gen-class))

(defn -main [& args] (timbre/info args))
