(ns taoensso.timbre.appenders.postal
  "Email (Postal) appender. Requires https://github.com/drewr/postal."
  {:author "Peter Taoussanis (@ptaoussanis)"}
  (:require [clojure.string     :as str]
            [io.aviso.exception :as aviso-ex]
            [postal.core        :as postal]
            [taoensso.timbre    :as timbre]
            [taoensso.encore    :as enc :refer [have have?]]))

(defn postal-appender
  "Returns a Postal email appender.
  (postal-appender
    ^{:host \"mail.isp.net\" :user \"jsmith\" :pass \"sekrat!!1\"}
    {:from \"Bob's logger <me@draines.com>\" :to \"foo@example.com\"})"

  [postal-config &
   [{:keys [subject-len body-fn]
     :or   {subject-len 150
            body-fn (fn [output-str] [{:type "text/plain; charset=utf-8"
                                      :content output-str}])}}]]
  {:enabled?   true
   :async?     true  ; Slow!
   :min-level  :warn ; Elevated
   :rate-limit [[5  (enc/ms :mins  2)]
                [50 (enc/ms :hours 24)]]
   :output-fn  (partial timbre/default-output-fn
                 {:stacktrace-fonts {}})
   :fn
   (fn [data]
     (let [{:keys [output_]} data
           output-str (force output_)]

       (postal/send-message
         (assoc postal-config
           :subject (-> output-str
                        (str/trim)
                        (str/replace #"\s+" " ")
                        (enc/get-substring 0 subject-len))
           :body (body-fn output-str)))))})

;;;; Deprecated

(defn make-postal-appender
  "DEPRECATED. Please use `postal-appender` instead."
  [& [appender-merge opts]]
  (merge (postal-appender (:postal-config opts) (dissoc opts :postal-config))
    appender-merge))
