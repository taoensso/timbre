(ns taoensso.timbre.appenders.postal
  "Email appender. Requires https://github.com/drewr/postal."
  {:author "Peter Taoussanis"}
  (:require [clojure.string  :as str]
            [postal.core     :as postal]
            [taoensso.timbre :as timbre]
            [taoensso.encore :as enc :refer (have have?)]))

(defn make-appender
  "Returns a Postal email appender.
  A Postal config map can be provided here as an argument, or as a :postal key
  in :shared-appender-config.

  (make-postal-appender {:enabled? true}
   {:postal-config
    ^{:host \"mail.isp.net\" :user \"jsmith\" :pass \"sekrat!!1\"}
    {:from \"Bob's logger <me@draines.com>\" :to \"foo@example.com\"}})"

  [& [appender-config make-config]]
  (let [{:keys [postal-config subject-len body-fn]
         :or   {subject-len 150
                body-fn (fn [output] [{:type "text/plain; charset=utf-8"
                                      :content output}])}}
        make-config

        default-appender-config
        {:enabled?   true
         :min-level  :warn
         :async?     true ; Slow!
         :rate-limit [[5  (enc/ms :mins  2)]
                      [50 (enc/ms :hours 24)]]}]

    (merge default-appender-config appender-config
      {:fn
       (fn [data]
         (let [{:keys [output-fn appender-opts]} data
               {:keys [no-fonts?]} appender-opts]
           (when-let [postal-config (or postal-config (:postal appender-opts))]
             (let [output (str (output-fn data {:stacktrace-fonts {}}))]
               (postal/send-message
                 (assoc postal-config
                   :subject (-> output
                                (str/trim)
                                (str/replace #"\s+" " ")
                                (enc/substr 0 subject-len))
                   :body (body-fn output)))))))})))

;;;; Deprecated

(def make-postal-appender make-appender)
