(ns taoensso.timbre.appenders.3rd-party.sentry
  "Sentry appender. Requires https://github.com/sethtrain/raven-clj."
  {:author "Samuel Otter (@samuelotter)"}
  (:require
   [taoensso.encore :as enc]
   [taoensso.timbre :as timbre]
   [raven-clj.core :as raven]
   [raven-clj.interfaces :as interfaces]))

(def ^:private timbre->sentry-levels
  {:trace  "debug"
   :debug  "debug"
   :info   "info"
   :warn   "warning"
   :error  "error"
   :fatal  "fatal"
   :report "info"})

(defn sentry-appender
  "Returns a raven-clj Sentry appender.

  Requires the DSN (e.g. \"https://<key>:<secret>@sentry.io/<project>\")
  to be passed in, see Sentry documentation for details.

  Common options:
    * :tags, :environment, :release, and :modules will be passed to Sentry
      as attributes, Ref. https://docs.sentry.io/clientdev/attributes/.
    * :event-fn can be used to modify the raw event before sending it
      to Sentry.
    * :data-event-fn can be used to modify the raw event, and is given the
      data from the log message"

  [dsn & [opts]]
  (let [{:keys [event-fn data-event-fn] :or {event-fn identity
                                             data-event-fn (fn [_ e] e)}} opts
        base-event
        (->> (select-keys opts [:tags :environment :release :modules])
             (filter (comp not nil? second))
             (into {}))]

    {:enabled?   true
     :async?     true
     :min-level  :warn ; Reasonable default given how Sentry works
     :rate-limit nil
     :output-fn  :inherit
     :fn
     (fn [data]
       (let [{:keys [instant level output_ ?err msg_ ?ns-str context]} data

             event
             (as-> base-event event
               (merge event
                 {:message (force msg_)
                  :logger  ?ns-str
                  :level   (get timbre->sentry-levels level)}

                 (when context {:extra context}))

               (if ?err
                 (interfaces/stacktrace event ?err)
                 event)

               (event-fn event)
               (data-event-fn data event))]

         (raven/capture dsn event)))}))

(comment
  ;; Create an example appender with default opts:
  (sentry-appender "https://<key>:<secret>@sentry.io/<project>")

  ;; Create an example appender with default opts, but override `:min-level`:
  (merge (sentry-appender "https://<key>:<secret>@sentry.io/<project>")
    {:min-level :debug}))
