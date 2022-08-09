(ns taoensso.timbre.appenders.postal
  "Email (Postal) appender. Requires https://github.com/drewr/postal."
  {:author "Peter Taoussanis (@ptaoussanis)"}
  (:require
   [clojure.string     :as str]
   [taoensso.encore    :as enc :refer [have have?]]
   [taoensso.timbre    :as timbre]
   [io.aviso.exception :as aviso-ex]
   [postal.core        :as postal]))

(defn default-subject-fn
  "Given an `output-str`, returns an appropriate email subject string:
    - Take only the first line
    - Trim it
    - Simplify whitespace
    - Never exceed `max-subject-len` characters."

  [{:keys [max-len]
    :or   {max-len 150}}
   output-str]

  (let [s (->
            (re-find #"\A.*" output-str) ; 1st line
            (str/trim)
            (str/replace #"\s+" " "))]

    (if (and max-len (> (count s) ^long max-len))
      (str (enc/get-substr-by-len s 0 (- ^long max-len 3)) "...")
      s)))

(comment
  (default-subject-fn {:max-len 8} "sdfghsjhfdg shj
sfjsdgfjhsdgf s
sfsdf
sfsdf
sdf"))

(defn postal-appender
  "Returns a Postal email appender.
  (postal-appender
    ^{:host \"mail.isp.net\" :user \"jsmith\" :pass \"sekrat!!1\"}
    {:from \"Bob's logger <me@draines.com>\" :to \"foo@example.com\"})"

  [postal-config &
   [{:keys [subject-len subject-fn body-fn]
     :or   {subject-len 150
            subject-fn  (partial default-subject-fn {:max-len subject-len})
            body-fn
            (fn [output-str]
              [{:type "text/plain; charset=utf-8"
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
           :subject (subject-fn output-str)
           :body    (body-fn    output-str)))))})

;;;; Deprecated

(enc/deprecated
  (defn make-postal-appender
    "DEPRECATED. Please use `postal-appender` instead."
    [& [appender-merge opts]]
    (merge (postal-appender (:postal-config opts) (dissoc opts :postal-config))
      appender-merge)))
