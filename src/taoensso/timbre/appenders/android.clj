(ns taoensso.timbre.appenders.android
  "Android LogCat appender. Depends on the android runtime. This is a
  configuration for the timbre logging library"
  {:author "Adam Clements"}
  (:require [taoensso.timbre :as timbre]))

(def logcat-appender
  {:doc (str "Appends to android logcat. Obviously only works if "
             "running within the android runtime, either on a device "
             "or an emulator")
   :min-level :debug
   :enabled? true
   :async? false
   :limit-per-msecs nil
   :prefix-fn :ns
   :fn (fn [{:keys [level prefix throwable message]}]
         (if throwable
           (condp = level
             :error  (android.util.Log/e prefix message throwable)
             :fatal  (android.util.Log/e prefix message throwable)
             :warn   (android.util.Log/w prefix message throwable)
             :info   (android.util.Log/i prefix message throwable)
             :report (android.util.Log/i prefix message throwable)
             :debug  (android.util.Log/d prefix message throwable)
             :report (android.util.Log/d prefix message throwable)
             :trace  (android.util.Log/d prefix message throwable))

           (condp = level
             :error  (android.util.Log/e prefix message)
             :fatal  (android.util.Log/e prefix message)
             :warn   (android.util.Log/w prefix message)
             :info   (android.util.Log/i prefix message)
             :report (android.util.Log/i prefix message)
             :debug  (android.util.Log/d prefix message)
             :report (android.util.Log/d prefix message)
             :trace  (android.util.Log/d prefix message))))})
