(ns taoensso.timbre.appenders.android
  "Android LogCat appender. Depends on the android runtime. This is a
  configuration for the timbre logging library"
  {:author "Adam Clements"}
  (:require [taoensso.timbre :as timbre]))

(def logcat-appender
  {:doc (str "Appends to Android logcat. Obviously only works if "
             "running within the Android runtime (device or emulator)."
             "You may want to disable std-out to prevent printing nested "
             "timestamps, etc.")
   :min-level :debug
   :enabled? true
   :async? false
   :limit-per-msecs nil
   :prefix-fn :ns
   :fn (fn [{:keys [level prefix throwable message]}]
         (if throwable
           (case level
             :trace  (android.util.Log/d prefix message throwable)
             :debug  (android.util.Log/d prefix message throwable)
             :info   (android.util.Log/i prefix message throwable)
             :warn   (android.util.Log/w prefix message throwable)
             :error  (android.util.Log/e prefix message throwable)
             :fatal  (android.util.Log/e prefix message throwable)
             :report (android.util.Log/i prefix message throwable))

           (case level
             :trace  (android.util.Log/d prefix message)
             :debug  (android.util.Log/d prefix message)
             :info   (android.util.Log/i prefix message)
             :warn   (android.util.Log/w prefix message)
             :error  (android.util.Log/e prefix message)
             :fatal  (android.util.Log/e prefix message)
             :report (android.util.Log/i prefix message))))})
