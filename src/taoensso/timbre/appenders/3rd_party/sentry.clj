(ns taoensso.timbre.appenders.3rd-party.sentry
  "Sentry appender

  Requires https://github.com/sethtrain/raven-clj"
  {:author "Samuel Otter (@samuelotter)"}
  (:require
   [taoensso.encore :as enc]
   [taoensso.timbre :as timbre]
   [raven-clj.core :as raven]
   [raven-clj.interfaces :as interfaces]))

(def ^:private levels {:fatal "fatal"
                       :error "error"
                       :warn  "warning"
                       :info  "info"
                       :debug "debug"
                       :trace "debug"})

(defn sentry-appender
  "Creates a sentry-appender. Requires the DSN (e.g.
  \"https://<key>:<secret>@sentry.io/<project>\", see sentry documentation) to
  be passed in
  opts may contain additional attributes that will be passed on to sentry. See
  https://docs.sentry.io/clientdev/attributes/ for more information"
  [dsn & [opts]]
  (let [base-event (->> (select-keys opts [:tags :environment :release :modules])
                        (filter (comp not nil? second))
                        (into {}))]
       {:enabled?   true
        :async?     false
        :min-level  :warn
        :rate-limit nil
        :output-fn  :inherit
        :fn
        (fn [data]
          (let [{:keys [instant level output_ ?err msg_ ?ns-str context]} data
                event (merge base-event
                             {:message (force msg_)
                              :logger  ?ns-str
                              :level   (get levels level :warning)}
                             (when context
                               {:extra context}))]
            (raven/capture dsn (if ?err
                                 (interfaces/stacktrace event ?err)
                                 event))))}))

(comment
  ;; Create an example appender with default options:
  (sentry-appender "https://<key>:<secret>@sentry.io/<project>")

  ;; Create an example appender with default options, but override `:min-level`:
  (merge (sentry-appender "https://<key>:<secret>@sentry.io/<project>") {:min-level :debug}))
