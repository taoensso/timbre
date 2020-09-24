(ns taoensso.timbre.appenders.community.node-spit
  "Requires https://github.com/pkpkpk/cljs-node-io."
  {:author "Mason Vines (@mavines)"}
  (:require
   [taoensso.encore   :as enc]
   [cljs-node-io.core :as nio]))

(defn node-spit-appender
  "Returns a simple `spit` file appender for `cljs-node-io`.
  Based on `taoensso.timbre.appenders.core/spit-appender`."
  [& [{:keys [fname append?]
       :or   {fname "./timbre-spit.log"
              append? true}}]]

  (enc/have? enc/nblank-str? fname)

  {:enabled? true
   :fn
   (fn self [data]
     (let [{:keys [output_]} data]
       (try
         (nio/spit fname (str (force output_) enc/system-newline)
           :append append?)

         (catch :default e
           (if (:spit-appender/retry? data)
             (throw e)
             (do
               (nio/make-parents fname)
               (self (assoc data :spit-appender/retry? true))))))))})
