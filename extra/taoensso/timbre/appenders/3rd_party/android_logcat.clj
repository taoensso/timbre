(ns taoensso.timbre.appenders.3rd-party.android-logcat
  "Android LogCat appender. Requires Android runtime."
  {:author "Adam Clements (@AdamClements)"}
  (:require [clojure.string  :as str]
            [taoensso.timbre :as timbre]))

;; TODO Test port to Timbre v4

(defn android-logcat-appender
  "Returns an appender that writes to Android LogCat. Obviously only works if
  running within the Android runtime (device or emulator). You may want to
  disable std-out to prevent printing nested timestamps, etc."
  []
  {:enabled?   true
   :async?     false
   :min-level  :debug
   :rate-limit nil

   :output-fn ; Drop hostname, ns, stacktrace
   (fn [data]
     (let [{:keys [level timestamp_ msg_]} data]
       (str
         (force timestamp_) " "
         (str/upper-case (name level))  " "
         (force msg_))))

   :fn
   (fn [data]
     (let [{:keys [level ?ns-str ?err output_]} data
           ns-str     (str ?ns-str "")
           output-str (force output_)]

       (if-let [err ?err]
         (case level
           :trace  (android.util.Log/d ns-str output-str err)
           :debug  (android.util.Log/d ns-str output-str err)
           :info   (android.util.Log/i ns-str output-str err)
           :warn   (android.util.Log/w ns-str output-str err)
           :error  (android.util.Log/e ns-str output-str err)
           :fatal  (android.util.Log/e ns-str output-str err)
           :report (android.util.Log/i ns-str output-str err))

         (case level
           :trace  (android.util.Log/d ns-str output-str)
           :debug  (android.util.Log/d ns-str output-str)
           :info   (android.util.Log/i ns-str output-str)
           :warn   (android.util.Log/w ns-str output-str)
           :error  (android.util.Log/e ns-str output-str)
           :fatal  (android.util.Log/e ns-str output-str)
           :report (android.util.Log/i ns-str output-str)))))})

;;;; Deprecated

(defn make-logcat-appender
  "DEPRECATED. Please use `android-logcat-appender` instead."
  [& [appender-merge opts]]
  (merge (android-logcat-appender opts) appender-merge))
