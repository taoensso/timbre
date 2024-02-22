(ns taoensso.timbre.appenders.postal
  "Email (Postal) appender.
  Requires <https://github.com/drewr/postal>."
  {:author "Peter Taoussanis (@ptaoussanis)"}
  (:require
   [clojure.string  :as str]
   [taoensso.encore :as enc :refer [have have?]]
   [taoensso.timbre :as timbre]
   [postal.core     :as postal]))

(defn default-subject-fn
  "Given an `output-str`, returns an appropriate email subject string:
    - Take only the first line
    - Trim it
    - Simplify whitespace
    - Never exceed `max-subject-len` characters."

  [{:keys [max-len]} output-str]

  (let [s (->
            (re-find #"\A.*" output-str) ; 1st line
            (str/trim)
            (str/replace #"\s+" " "))]

    (if (and max-len (> (count s) ^long max-len))
      (str (enc/get-substr-by-len s 0 (- ^long max-len 3)) "...")
      (do                         s))))

(comment
  (default-subject-fn {:max-len 8} "sdfghsjhfdg shj
sfjsdgfjhsdgf s
sfsdf
sfsdf
sdf"))

(defn default-body-fn
  "Given an `output-str`, returns an appropriate Postal email body."
  [{:keys [max-len]} output-str]
  (let [s output-str]
    [{:type "text/plain; charset=utf-8"
      :content
      (if max-len
        (enc/get-substr-by-len s 0 max-len)
        (do                    s))}]))

(defn postal-appender
  "Returns a Postal email appender.
  (postal-appender
    ^{:host \"mail.isp.net\" :user \"jsmith\" :pass \"sekrat!!1\"}
    {:from \"Bob's logger <me@draines.com>\" :to \"foo@example.com\"})"

  [postal-config &
   [{:keys [subject-len body-len subject-fn body-fn]
     :or   {subject-len 150
            subject-fn  (partial default-subject-fn {:max-len (enc/as-?int subject-len)})
            body-fn     (partial default-body-fn    {:max-len (enc/as-?int    body-len)})}}]]

  {:enabled?   true
   :async?     true  ; Slow!
   :min-level  :warn ; Elevated
   :rate-limit [[5  (enc/ms :mins  2)]
                [50 (enc/ms :hours 24)]]
   :output-opts {:stacktrace-fonts {}}
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
  (defn ^:no-doc ^:deprecated make-postal-appender
    "Prefer `postal-appender`."
    [& [appender-merge opts]]
    (merge (postal-appender (:postal-config opts) (dissoc opts :postal-config))
      appender-merge)))
