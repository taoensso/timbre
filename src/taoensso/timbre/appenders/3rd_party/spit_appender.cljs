(ns taoensso.timbre.appenders.3rd-party.spit-appender
  (:require [cljs-node-io.core :as io :refer [slurp spit]]
            [taoensso.encore :as enc :refer-macros [have?]]))

(defn spit-appender
  [& [{:keys [fname append?]
       :or {fname "./timbre-spit.log"
            append? true}}]]
  {:enabled? true
   :async? true
   :min-level nil
   :rate-limit nil
   :output-fn :inherit
   :fn
   (fn self [data]
     (let [{:keys [output_]} data]
       (try
         (spit fname (str (force output_) "\n") :append append?)
         (catch :default e
           (if (:__spit-appender/retry? data)
             (throw e)
             (let [_ (have? enc/nblank-str? fname)]
               (io/make-parents fname)
               (self (assoc data :__spit-appender/retry? true))))))))})
