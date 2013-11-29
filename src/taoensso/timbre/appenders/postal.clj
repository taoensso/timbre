(ns taoensso.timbre.appenders.postal
  "Email appender. Depends on https://github.com/drewr/postal."
  {:author "Peter Taoussanis"}
  (:require [clojure.string  :as str]
            [postal.core     :as postal]
            [taoensso.timbre :as timbre]))

(defn- str-trunc [^String s max-len]
  (if (<= (.length s) max-len) s
      (.substring s 0 max-len)))

(comment (str-trunc "Hello this is a long string" 5))

(def postal-appender
  {:doc (str "Sends an email using com.draines/postal.\n"
             "Needs :postal config map in :shared-appender-config, e.g.:
             ^{:host \"mail.isp.net\" :user \"jsmith\" :pass \"sekrat!!1\"}
             {:from \"Bob's logger <me@draines.com>\" :to \"foo@example.com\"}")
   :min-level :error :enabled? true :async? true
   :rate-limit [5 (* 1000 60 2)] ; 5 calls / 2 mins
   :fn (fn [{:keys [ap-config default-output]}]
         (when-let [postal-config (:postal ap-config)]
           (postal/send-message
            (assoc postal-config
              :subject (-> default-output (str/trim) (str-trunc 150)
                           (str/replace #"\s+" " "))
              :body    default-output))))})
