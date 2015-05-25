(ns taoensso.timbre.appenders.android
  "Android LogCat appender. Depends on the android runtime. This is a
  configuration for the timbre logging library"
  {:author "Adam Clements"}
  (:require [taoensso.timbre :as timbre]
            clojure.string))

(defn make-logcat-appender
  "Returns an appender that writes to Android LogCat. Obviously only works if
  running within the Android runtime (device or emulator). You may want to
  disable std-out to prevent printing nested timestamps, etc."
  [& [appender-opts make-opts]]
  (let [default-appender-opts {:enabled? true
                               :min-level :debug}]
    (merge default-appender-opts appender-opts
           {:fn (fn [{:keys [level ns throwable message]}]
                  (let [output (format "%s %s - %s" timestamp
                                       (-> level name clojure.string/upper-case)
                                       (or message ""))]
                    (if throwable
                      (case level
                        :trace  (android.util.Log/d ns output throwable)
                        :debug  (android.util.Log/d ns output throwable)
                        :info   (android.util.Log/i ns output throwable)
                        :warn   (android.util.Log/w ns output throwable)
                        :error  (android.util.Log/e ns output throwable)
                        :fatal  (android.util.Log/e ns output throwable)
                        :report (android.util.Log/i ns output throwable))

                      (case level
                        :trace  (android.util.Log/d ns output)
                        :debug  (android.util.Log/d ns output)
                        :info   (android.util.Log/i ns output)
                        :warn   (android.util.Log/w ns output)
                        :error  (android.util.Log/e ns output)
                        :fatal  (android.util.Log/e ns output)
                        :report (android.util.Log/i ns output)))))})))

(def logcat-appender
  "DEPRECATED: Use `make-logcat-appender` instead."
  (make-logcat-appender))
