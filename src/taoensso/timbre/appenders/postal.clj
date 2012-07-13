(ns taoensso.timbre.appenders.postal
  "Email appender for com.draines/postal.
  Ref: https://github.com/drewr/postal."
  {:author "Peter Taoussanis"}
  (:require [clojure.string  :as str]
            [postal.core     :as postal]
            [taoensso.timbre :as timbre]))

(def postal-appender
  {:doc (str "Sends an email using com.draines/postal.\n"
             "Needs :postal config map in :shared-appender-config, e.g.:
             ^{:host \"mail.isp.net\" :user \"jsmith\" :pass \"sekrat!!1\"}
             {:from \"Bob's logger <me@draines.com>\" :to \"foo@example.com\"}")
   :min-level :error :enabled? true :async? true
   :max-message-per-msecs (* 60 60 2)
   :fn (fn [{:keys [ap-config more] :as args}]
         (when-let [postal-config (:postal ap-config)]
           (postal/send-message
            (assoc postal-config
              :subject (timbre/prefixed-message args)
              :body    (if (seq more) (str/join " " more)
                           "<no additional arguments>")))))})