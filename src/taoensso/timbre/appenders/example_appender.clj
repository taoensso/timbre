(ns taoensso.timbre.appenders.example-appender
  "An example of how Timbre library-style appenders should be written for
  bundling with Timbre. Please mention any requirements/dependencies in this
  docstring, thanks!"
  {:author "Your name here"}
  (:require [clojure.string  :as str]
            [taoensso.timbre :as timbre]
            [taoensso.encore :as encore]))

;;;; Any private util fns, etc.

;; ...

;;;;

(defn make-appender-fn
  "(fn [make-config-map]) -> (fn [appender-data-map]) -> logging side effects."
  [make-config] ; Any config that can influence the appender-fn construction
  (let [{:keys []} make-config]

    (fn [data] ; Data map as provided to middleware + appenders
      (let [{:keys [instant level ?err_ vargs_ output-fn

                    config   ; Entire config map in effect
                    appender ; Entire appender map in effect

                    ;; = (:opts <appender-map>), for convenience. You'll
                    ;; usually want to store+access runtime appender config
                    ;; stuff here to let users change config without recreating
                    ;; their appender fn:
                    appender-opts

                    ;; <...>
                    ;; See `timbre/example-config` for info on all available args
                    ]}
            data

            {:keys [my-arbitrary-appender-opt1]} appender-opts

            ;;; Use `force` to realise possibly-delayed args:
            ?err  (force ?err_)  ; ?err non-nil iff first given arg was an error
            vargs (force vargs_) ; Vector of raw args (excl. possible first error)

            ;; You'll often want a formatted string with ns, timestamp, vargs, etc.
            ;; A formatter (fn [logging-data-map & [opts]]) -> string is
            ;; provided for you under the :output-fn key. Prefer using this fn
            ;; to your own formatter when possible, since the user can
            ;; configure the :output-fn formatter in a standard way that'll
            ;; influence all participating appenders. Take a look at the
            ;; `taoensso.timbre/default-output-fn` source for details.
            ;;
            any-special-output-fn-opts {} ; Output-fn can use these opts
            output-string (output-fn data any-special-output-fn-opts)]

        (println (str my-arbitrary-appender-opt1 output-string))))))

(defn make-appender ; Prefer generic name to `make-foo-appender`, etc.
  "Your docstring describing the appender, its options, etc."
  [& [appender-config make-appender-config]]
  (let [default-appender-config
        {:doc "My appender docstring"
         :enabled? true ; Please enable your appender by default
         :min-level :debug
         :rate-limit [[5   (encore/ms :mins  1)] ; 5 calls/min
                      [100 (encore/ms :hours 1)] ; 100 calls/hour
                      ]

         ;; Any default appender-specific opts. These'll be accessible to your
         ;; appender fn under the :appender-opts key for convenience:
         :opts {:my-arbitrary-appender-opt1 "hello world, "}}

        ;;; Here we'll prepare the final appender map as described in
        ;;; `timbre/example-config`:
        appender-config (merge default-appender-config appender-config)
        appender-fn     (make-appender-fn make-appender-config)
        appender        (merge appender-config {:fn appender-fn})]

    appender))

(comment
  ;; Your examples, tests, etc. here
  )
