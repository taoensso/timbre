(ns taoensso.timbre.appenders.3rd-party.android
  "Android LogCat appender. Requires Android runtime."
  {:author "Adam Clements"}
  (:require [clojure.string  :as str]
            [taoensso.timbre :as timbre]))

(defn make-appender
  "Returns an appender that writes to Android LogCat. Obviously only works if
  running within the Android runtime (device or emulator). You may want to
  disable std-out to prevent printing nested timestamps, etc."
  [& [appender-config make-config]]
  (let [default-appender-config
        {:enabled?  true
         :min-level :debug}]

    (merge default-appender-config appender-config
      {:fn
       (fn [data]
         (let [{:keys [level ?ns-str ?err_ msg_ timestamp_]} data
               msg       (or (force msg_) "")
               timestamp (force timestamp_)
               ns        (or ?ns-str "")
               output    (format "%s %s - %s" timestamp
                           (-> level name str/upper-case)
                           msg)]

           (if-let [throwable (force ?err_)]
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
