(ns taoensso.timbre.appenders.3rd-party.android-logcat
  "Android LogCat appender. Requires Android runtime."
  {:author "Adam Clements"}
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
     (let [{:keys [level ?ns-str ?err_ output-fn]} data
           ns         (str ?ns-str "")
           output-str (output-fn data)]

       (if-let [throwable (force ?err_)]
         (case level
           :trace  (android.util.Log/d ns output-str throwable)
           :debug  (android.util.Log/d ns output-str throwable)
           :info   (android.util.Log/i ns output-str throwable)
           :warn   (android.util.Log/w ns output-str throwable)
           :error  (android.util.Log/e ns output-str throwable)
           :fatal  (android.util.Log/e ns output-str throwable)
           :report (android.util.Log/i ns output-str throwable))

         (case level
           :trace  (android.util.Log/d ns output-str)
           :debug  (android.util.Log/d ns output-str)
           :info   (android.util.Log/i ns output-str)
           :warn   (android.util.Log/w ns output-str)
           :error  (android.util.Log/e ns output-str)
           :fatal  (android.util.Log/e ns output-str)
           :report (android.util.Log/i ns output-str)))))})

;;;; Deprecated

(defn make-logcat-appender
  "DEPRECATED. Please use `android-logcat-appender` instead."
  [& [appender-merge opts]]
  (merge (android-logcat-appender opts) appender-merge))
