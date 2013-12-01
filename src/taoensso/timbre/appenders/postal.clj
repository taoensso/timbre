(ns taoensso.timbre.appenders.postal
  "Email appender. Requires https://github.com/drewr/postal."
  {:author "Peter Taoussanis"}
  (:require [clojure.string  :as str]
            [postal.core     :as postal]
            [taoensso.timbre :as timbre]))

(defn- str-trunc [^String s max-len]
  (if (<= (.length s) max-len) s
    (.substring s 0 max-len)))

(comment (str-trunc "Hello this is a long string" 5))

(defn make-postal-appender
  "Returns a Postal email appender.
  A Postal config map can be provided here as an argument, or as a :postal key
  in :shared-appender-config.

  (make-postal-appender {:enabled? true}
   {:postal-config
    ^{:host \"mail.isp.net\" :user \"jsmith\" :pass \"sekrat!!1\"}
    {:from \"Bob's logger <me@draines.com>\" :to \"foo@example.com\"}})"
  [& [appender-opts {:keys [postal-config subject-len]
                     :or   {subject-len 150}}]]

  (let [default-appender-opts
        {:enabled?   true
         :min-level  :warn
         :async?     true ; Slow!
         :rate-limit [5 (* 1000 60 2)] ; 5 calls / 2 mins
         :fmt-output-opts {:nofonts? true} ; Disable ANSI-escaped stuff
         }]

    (merge default-appender-opts appender-opts
      {:fn
       (fn [{:keys [ap-config output]}]
         (when-let [postal-config (or postal-config (:postal ap-config))]
           (postal/send-message
            (assoc postal-config
              :subject (-> (str output)
                           (str/trim)
                           (str-trunc subject-len)
                           (str/replace #"\s+" " "))
              :body output))))})))

(def postal-appender "DEPRECATED: Use `make-postal-appender` instead."
  (make-postal-appender))
