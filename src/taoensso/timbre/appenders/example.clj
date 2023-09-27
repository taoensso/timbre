(ns taoensso.timbre.appenders.example
  "You can copy this namespace if you'd like a starting template for
  writing your own Timbre appender.

  PRs for new *dependency-free* community appenders welcome!

  NB See the `timbre/*config*` docstring for up-to-date info
  Timbre's appender API."

  {:author "TODO Your Name (@your-github-username)"}
  (:require
   [taoensso.encore :as enc]
   [taoensso.timbre :as timbre]))

;; TODO Please mark any implementation vars as ^:private

(defn example-appender ; Appender constructor
  "Docstring to explain any special opts to influence appender construction,
  etc. Returns the appender map. May close over relevant state, etc."

  [{:as appender-opts :keys []}] ; TODO Always take an opts map, even if unused

  (let [shutdown?_ (atom false)] ; See :shutdown-fn below

    ;; Return a new appender (just a map),
    ;; see `timbre/*config*` docstring for info on all available keys:

    {:enabled?     true  ; Enable new appenders by default
     ;; :async?    true  ; Use agent for appender dispatch? Useful for slow dispatch
     ;; :min-level :info ; Optional minimum logging level

     ;; Provide any default rate limits?
     ;; :rate-limit [[5   (enc/ms :mins  1)] ;   5 calls/min
     ;;              [100 (enc/ms :hours 1)] ; 100 calls/hour
     ;;              ]

     ;; :output-fn ; A custom (fn [data]) -> final output appropriate
     ;;            ; for use by this appender (e.g. string, map, etc.).
     ;;            ;
     ;;            ; The fn may use (:output-opts data) for configurable
     ;;            ; behaviour.

     ;; Optional, for appenders that need to open/acquire resources.
     ;; When provided, shutdown-fn will be called without arguments when
     ;; the appender is to be permanently shut down.
     ;;
     ;; Fn must be safe to call repeatedly, even if resources have not yet
     ;; been opened/acquired, or if they've already been closed/released.
     :shutdown-fn
     (fn []
       (when (compare-and-set! shutdown?_ false true)
         ;; Permanently close/release any relevant resources.
         ;; Must safely noop if nothing to do.
         ))

     :fn ; The actual appender (fn [data]) -> possible side effects
     (fn [data]
       ;; If a shutdown-fn is provided, logging calls must permanently noop
       ;; once shutdown has initiated.
       (when-not @shutdown?_
         (let [{:keys
                [instant level output_
                 ;; ... lots more, see `timbre/default-config`
                 ]} data

               ;; Final output, in a format appropriate for this
               ;; appender (string, map, etc.).
               output (force output_)]

           ;; This is where we produce our logging side effects using `output`.
           ;; In this case we'll just call `println`:
           (println (str output)))))}))

(comment
  ;; Create an example appender with default options:
  (example-appender)

  ;; Create an example appender with default options, but override `:min-level`:
  (merge (example-appender) {:min-level :debug}))
