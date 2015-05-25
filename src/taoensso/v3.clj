;;;; Default configuration and appenders

(def example-config
  "APPENDERS
     An appender is a map with keys:
      :doc             ; Optional docstring.
      :min-level       ; Level keyword, or nil (=> no minimum level).
      :enabled?        ;
      :async?          ; Dispatch using agent? Useful for slow appenders.
      :rate-limit      ; [ncalls-limit window-ms], or nil.
      :args-hash-fn    ; Used by rate-limiter, etc.
      :appender-config ; Any appender-specific config.
      :fn              ; (fn [appender-args-map]), with keys described below.

     An appender's fn takes a single map with keys:
      :instant       ; java.util.Date.
      :ns            ; String.
      :level         ; Keyword.
      :error?        ; Is level an 'error' level?
      :throwable     ; java.lang.Throwable.
      :args          ; Raw logging macro args (as given to `info`, etc.).
      ;;
      :context       ; Thread-local dynamic logging context.
      :ap-config     ; Content of appender's own `:appender-config` merged over
                     ; `:shared-appender-config`.
      :profile-stats ; From `profile` macro.
      ;;
      ;; Waiting on http://dev.clojure.org/jira/browse/CLJ-865:
      :file          ; String.
      :line          ; Integer.
      ;;
      :message       ; DELAYED string of formatted appender args. Appenders may
                     ; (but are not obligated to) use this as their output.

   MIDDLEWARE
     Middleware are fns (applied right-to-left) that transform the map
     dispatched to appender fns. If any middleware returns nil, no dispatching
     will occur (i.e. the event will be filtered).

  The `example-config` code contains further settings and details.
  See also `set-config!`, `merge-config!`, `set-level!`."

  {

   :shared-appender-config
   {:message-fmt-opts ; `:message` appender argument formatting
    {:timestamp-pattern default-message-timestamp-pattern ; SimpleDateFormat
     :timestamp-locale  nil ; A Locale object, or nil
     :pattern-fn        default-message-pattern-fn}}

   :appenders
   {:standard-out
    {:doc "Prints to *out*/*err*. Enabled by default."
     :min-level nil :enabled? true :async? false :rate-limit nil
     :appender-config {:always-log-to-err? false}
     :fn (fn [{:keys [ap-config error? message]}] ; Can use any appender args
           (binding [*out* (if (or error? (:always-log-to-err? ap-config))
                             *err* *out*)]
             (str-println @message)))}

    :spit
    {:doc "Spits to `(:spit-filename :ap-config)` file."
     :min-level nil :enabled? false :async? false :rate-limit nil
     :appender-config {:spit-filename "timbre-spit.log"}
     :fn (fn [{:keys [ap-config message]}] ; Can use any appender args
           (when-let [filename (:spit-filename ap-config)]
             (try (ensure-spit-dir-exists! filename)
                  (spit filename (str output "\n") :append true)
                  (catch java.io.IOException _))))}}})
