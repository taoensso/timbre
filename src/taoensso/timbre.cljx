(ns taoensso.timbre
  "Simple, flexible logging for Clojure/Script. No XML."
  {:author "Peter Taoussanis"}
  #+clj  (:require [clojure.string     :as str]
                   [io.aviso.exception :as aviso-ex]
                   [taoensso.encore    :as enc :refer (have have? qb)])
  #+cljs (:require [clojure.string  :as str]
                   [taoensso.encore :as enc :refer ()])
  #+cljs (:require-macros [taoensso.encore :as enc :refer (have have?)])
  #+clj  (:import [java.util Date Locale]
                  [java.text SimpleDateFormat]
                  [java.io File]))

;;;; TODO
;; - Check for successful cljs compile
;; - Cljs default appenders
;; - Try ease backward comp, update README, CHANGELOG
;; - Document shutdown-agents,
;;   Ref. https://github.com/ptaoussanis/timbre/pull/100/files

;;;; Encore version check

#+clj
(let [min-encore-version 1.31]
  (if-let [assert! (ns-resolve 'taoensso.encore 'assert-min-encore-version)]
    (assert! min-encore-version)
    (throw
      (ex-info
        (format
          "Insufficient com.taoensso/encore version (< %s). You may have a Leiningen dependency conflict (see http://goo.gl/qBbLvC for solution)."
          min-encore-version)
        {:min-version min-encore-version}))))

;;;; Config

#+clj
(def default-timestamp-opts
  "Controls (:timestamp_ data)."
  {:pattern     "yy-MMM-dd HH:mm:ss"
   :locale      (java.util.Locale. "en")
   ;; :timezone (java.util.TimeZone/getTimeZone "UTC")
   :timezone    (java.util.TimeZone/getDefault)})

(declare stacktrace)

(defn default-output-fn
  "(fn [data & [opts]]) -> string output."
  [data & [opts]]
  (let [{:keys [level ?err_ vargs_ msg_ ?ns-str hostname_ timestamp_]} data]
    (str
      #+clj (force timestamp_) #+clj " "
      #+clj (force hostname_)  #+clj " "
      (str/upper-case (name level))
      " [" (or ?ns-str "?ns") "] - " (force msg_)
      (when-let [err (force ?err_)] (str "\n" (stacktrace err opts))))))

(declare default-err default-out ensure-spit-dir-exists!)

(def example-config
  "Example (+default) Timbre v4 config map.

  APPENDERS

    *** Please see the `taoensso.timbre.appenders.example-appender` ns if you
        plan to write your own Timbre appender ***

    An appender is a map with keys:
      :doc             ; Optional docstring
      :min-level       ; Level keyword, or nil (=> no minimum level)
      :enabled?        ;
      :async?          ; Dispatch using agent? Useful for slow appenders
      :rate-limit      ; [[ncalls-limit window-ms] <...>], or nil
      :data-hash-fn    ; Used by rate-limiter, etc.
      :opts            ; Any appender-specific opts
      :fn              ; (fn [data-map]), with keys described below

    An appender's fn takes a single data map with keys:
      :config          ; Entire config map (this map, etc.)
      :appender-id     ; Id of appender currently being dispatched to
      :appender        ; Entire appender map currently being dispatched to
      :appender-opts   ; Duplicates (:opts <appender-map>), for convenience

      :instant         ; Platform date (java.util.Date or js/Date)
      :level           ; Keyword
      :error-level?    ; Is level :error or :fatal?
      :?ns-str         ; String, or nil
      :?file           ; String, or nil  ; Waiting on CLJ-865
      :?line           ; Integer, or nil ; Waiting on CLJ-865

      :?err_           ; Delay - first-argument platform error
      :vargs_          ; Delay - raw args vector
      :hostname_       ; Delay - string (clj only)
      :msg_            ; Delay - args string
      :timestamp_      ; Delay - string
      :output-fn       ; (fn [data & [opts]]) -> formatted output string

      :profile-stats   ; From `profile` macro

      <Also, any *context* keys, which get merged into data map>

  MIDDLEWARE
    Middleware are simple (fn [data]) -> ?data fns (applied left->right) that
    transform the data map dispatched to appender fns. If any middleware returns
    nil, NO dispatching will occur (i.e. the event will be filtered).

  The `example-config` source code contains further settings and details.
  See also `set-config!`, `merge-config!`, `set-level!`."

  (merge
    {:level :debug  ; e/o #{:trace :debug :info :warn :error :fatal :report}

     :whitelist  [] ; "my-ns.*", etc.
     :blacklist  [] ;
     :middleware [] ; (fns [data]) -> ?data, applied left->right

     :output-fn default-output-fn ; (fn [data]) -> string
     #+clj :timestamp-opts
     #+clj default-timestamp-opts ; {:pattern _ :locale _ :timezone _}

     :appenders
     #+clj
     {:println ; Appender id
      ;; Appender <map>:
      {:doc "Prints to (:stream <appender-opts>) IO stream. Enabled by default."
       :min-level nil :enabled? true :async? false :rate-limit nil

       ;; Any custom appender opts:
       :opts {:stream :auto ; e/o #{:std-err :std-out :auto <stream>}
              }

       :fn
       (fn [data]
         (let [{:keys [output-fn error? appender-opts]} data
               {:keys [stream]} appender-opts
               stream (case stream
                        (nil :auto) (if error? default-err *out*)
                        :std-err    default-err
                        :std-out    default-out
                        stream)]
           (binding [*out* stream] (println (output-fn data)))))}

      :spit
      {:doc "Spits to (:spit-filename <appender-opts>) file."
       :min-level nil :enabled? false :async? false :rate-limit nil
       :opts {:spit-filename "timbre-spit.log"}
       :fn
       (fn [data]
         (let [{:keys [output-fn appender-opts]} data
               {:keys [spit-filename]} appender-opts]
           (when-let [fname (enc/as-?nblank spit-filename)]
             (try (ensure-spit-dir-exists! fname)
                  (spit fname (str (output-fn data) "\n") :append true)
                  (catch java.io.IOException _)))))}}}))

(comment
  (set-config! example-config)
  (infof "Hello %s" "world :-)"))

(enc/defonce* ^:dynamic *config* "See `example-config` for info." example-config)
(defmacro with-config        [config & body] `(binding [*config* ~config] ~@body))
(defmacro with-merged-config [config & body]
  `(binding [*config* (enc/nested-merge *config* ~config)] ~@body))

(defn swap-config! [f]
  #+cljs (set!             *config* (f *config*))
  #+clj  (alter-var-root #'*config*  f))

(defn   set-config! [m] (swap-config! (fn [_old] m)))
(defn merge-config! [m] (swap-config! (fn [old] (enc/nested-merge old m))))

(defn set-level! [level] (swap-config! (fn [m] (merge m {:level level}))))
(defn with-level [level & body]
  `(binding [*config* (merge *config* {:level ~level})] ~@body))

(comment (set-level! :info) *config*)

;;;; Levels

(def ordered-levels [:trace :debug :info :warn :error :fatal :report])
(def ^:private scored-levels  (zipmap ordered-levels (next (range))))
(def ^:private valid-level
  (let [valid-level-set (set ordered-levels)]
    (fn [level]
      (or (valid-level-set level)
        (throw (ex-info (str "Invalid logging level: " level) {:level level}))))))

(comment (valid-level :info))

(defn level>= [x y] (>= (long (scored-levels (valid-level x)))
                        (long (scored-levels (valid-level y)))))

(comment (qb 10000 (level>= :info :debug)))

#+clj (defn- env-val [id] (when-let [s (System/getenv id)] (enc/read-edn s)))
#+clj (def ^:private compile-time-level
        (have [:or nil? valid-level] (keyword (env-val "TIMBRE_LEVEL"))))

(defn get-active-level [& [config]] (or (:level (or config *config*)) :report))

(comment
  (qb 10000 (get-active-level))
  (binding [*config* {:level :trace}] (level>= :trace (get-active-level))))

;;;; ns filter

(def ^:private compile-ns-filters
  "(fn [whitelist blacklist]) -> (fn [ns]) -> ?unfiltered-ns"
  (let [->re-pattern
        (fn [x]
          (enc/cond!
            (enc/re-pattern? x) x
            (string? x)
            (let [s (-> (str "^" x "$")
                        (str/replace "." "\\.")
                        (str/replace "*" "(.*)"))]
              (re-pattern s))))]

    (enc/memoize_
      (fn [whitelist blacklist]
        (let [whitelist* (mapv ->re-pattern whitelist)
              blacklist* (mapv ->re-pattern blacklist)

              white-filter
              (cond
                ;; (nil? whitelist)  (fn [ns] false) ; Might be surprising
                (empty?  whitelist*) (fn [ns] true)
                :else (fn [ns] (some #(re-find % ns) whitelist*)))

              black-filter
              (cond
                (empty? blacklist*) (fn [ns] true)
                :else (fn [ns] (not (some #(re-find % ns) blacklist*))))]

          (fn [ns] (when (and (white-filter ns) (black-filter ns)) ns)))))))

(def ^:private ns-filter
  "(fn [whitelist blacklist ns]) -> ?unfiltered-ns"
  (enc/memoize_
    (fn [whitelist blacklist ns]
      ((compile-ns-filters whitelist blacklist) ns))))

(comment (qb 10000 (ns-filter ["foo.*"] ["foo.baz"] "foo.bar")))

#+clj
(def ^:private compile-time-ns-filter
  (let [whitelist (have [:or nil? vector?] (env-val "TIMBRE_NS_WHITELIST"))
        blacklist (have [:or nil? vector?] (env-val "TIMBRE_NS_BLACKLIST"))]
    (partial ns-filter whitelist blacklist)))

;;;; Utils

(defn- ->delay [x] (if (delay? x) x (delay x)))
(defn- vsplit-err1 [[v1 :as v]] (if-not (enc/error? v1) [nil v] (enc/vsplit-first v)))
(comment
  (vsplit-err1 [:a :b :c])
  (vsplit-err1 [(Exception.) :a :b :c]))

(defn default-data-hash-fn
  "Used for rate limiters, some appenders (e.g. Carmine), etc.
  Goal: (hash data-1) = (hash data-2) iff data-1 \"the same\" as data-2 for
  rate-limiting purposes, etc."
  [data]
  (let [{:keys [?ns-str ?line vargs_]} data
        vargs (force vargs_)]
    (str
      (or (some #(and (map? %) (:timbre/hash %)) vargs) ; Explicit hash given
          #_[?ns-str ?line] ; TODO Waiting on http://goo.gl/cVVAYA
          [?ns-str vargs]))))

(comment (default-data-hash-fn {}))

(enc/defonce* ^:private get-agent
  (enc/memoize_ (fn [appender-id] (agent nil :error-mode :continue))))

(comment (get-agent :my-appender))

(enc/defonce* ^:private get-rate-limiter
  (enc/memoize_ (fn [appender-id specs] (enc/rate-limiter* specs))))

(comment (def rf (get-rate-limiter :my-appender [[10 5000]])))

;;;; Logging core

(defn log?
  "Would Timbre currently log at the given logging level?
    * ns filtering requires a compile-time `?ns-str` to be provided.
    * Non-global config requires an explicit `config` to be provided."
  [level & [?ns-str config]]
  (let [config (or config *config*)]
    (and (level>= level (get-active-level config))
         (ns-filter (:whitelist config) (:blacklist config) (or ?ns-str ""))
         true)))

(comment (log? :trace))

(def ^:dynamic *context*
  "General-purpose dynamic logging context. Context will be merged into
  appender data map at logging time." nil)
(defmacro with-context [context & body] `(binding [*context* ~context] ~@body))

(declare get-hostname)

(defn log* "Core fn-level logger. Implementation detail."
  [config level ?ns-str ?file ?line msg-type vargs_ & [base-data]]
  (when (log? level ?ns-str config)
    (let [instant (enc/now-dt)
          vargs*_ (delay (vsplit-err1 (force vargs_)))
          ?err_   (delay (get @vargs*_ 0))
          vargs_  (delay (get @vargs*_ 1))
          data    (merge base-data *context*
                    {:config  config ; Entire config!
                     ;; :context *context* ; Extra destructure's a nuisance
                     :instant instant
                     :level   level
                     :?ns-str ?ns-str
                     :?file   ?file
                     :?line   ?line
                     :?err_   ?err_
                     :vargs_  vargs_
                     #+clj :hostname_ #+clj (delay (get-hostname))
                     :error-level? (#{:error :fatal} level)})
          msg-fn
          (fn [vargs_] ; For use *after* middleware, etc.
            (when-not (nil? msg-type)
              (when-let [vargs (have [:or nil? vector?] (force vargs_))]
                (case msg-type
                  :print  (enc/spaced-str vargs)
                  :format (let [[fmt args] (enc/vsplit-first vargs)]
                            (enc/format* fmt args))))))
          ?data
          (reduce ; Apply middleware: data->?data
            (fn [acc mf]
              (let [result (mf acc)]
                (if (nil? result)
                  (reduced nil)
                  result)))
            data
            (:middleware config))]

      (when-let [data ?data] ; Not filtered by middleware
        (reduce-kv
          (fn [_ id appender]
            (when
                (and
                  (:enabled? appender)
                  (level>= level (or (:min-level appender) :trace))
                  (let [rate-limit-specs (:rate-limit appender)]
                    (if (empty? rate-limit-specs)
                      true
                      (let [rl-fn   (get-rate-limiter id rate-limit-specs)
                            hash-fn (or (:data-hash-fn appender)
                                        (:data-hash-fn config)
                                        default-data-hash-fn)
                            data-hash (hash-fn data)]
                        (not (rl-fn data-hash))))))

              (let [{:keys [async?] apfn :fn} appender
                    msg_      (delay (or (msg-fn (:vargs_ data)) #_""))
                    output-fn (or (:output-fn appender)
                                  (:output-fn config)
                                  default-output-fn)

                    #+clj timestamp_
                    #+clj
                    (delay
                      (let [timestamp-opts (merge default-timestamp-opts
                                                  (:timestamp-opts config)
                                                  (:timestamp-opts appender))
                            {:keys [pattern locale timezone]} timestamp-opts]
                        (.format (enc/simple-date-format pattern
                                   {:locale locale :timezone timezone})
                          (:instant data))))

                    data ; Final data prep before going to appender
                    (merge data
                      {:appender-id id
                       :appender    appender
                       :appender-opts (:opts appender) ; For convenience
                       :msg_        msg_
                       :msg-fn      msg-fn
                       :output-fn   output-fn
                       #+clj :timestamp_ #+clj timestamp_})]

                (if-not async?
                  (apfn data) ; Allow errors to throw
                  (send-off (get-agent id) (fn [_] (apfn data)))))))
          nil
          (enc/clj1098 (:appenders config))))))
  nil)

(comment
  (log* *config* :info nil nil nil :print (delay [(do (println "hi") :x) :y])))

;;;; Logging macros

(defmacro log "Core macro-level logger."
  [config level msg-type args & [base-data]]

  ;; Compile-time elision:
  (when (or (nil? compile-time-level) (level>= level compile-time-level))
    (when (compile-time-ns-filter (str *ns*))

      (let [ns-str (str *ns*)
            ?file  (let [f *file*] (when (not= f "NO_SOURCE_PATH") f))
            ;; TODO Waiting on http://dev.clojure.org/jira/browse/CLJ-865:
            ?line  (:line (meta &form))]
        `(log* ~config ~level ~ns-str ~?file ~?line ~msg-type
           (delay ~(vec args)) ~base-data)))))

(defmacro ^:private def-logger [level]
  (let [level-name (name level)]
    `(do
       (defmacro ~(symbol (str level-name #_"p"))
         ~(str "Logs at " level " level using print-style args.")
         ~'{:arglists '([& message] [error & message])}
         [& sigs#] `(log *config* ~~level :print ~sigs#))

       (defmacro ~(symbol (str level-name "f"))
         ~(str "Logs at " level " level using format-style args.")
         ~'{:arglists '([fmt & fmt-args] [error fmt & fmt-args])}
         [& sigs#] `(log *config* ~~level :format ~sigs#)))))

(defmacro def-loggers []
  `(do ~@(map (fn [level] `(def-logger ~level)) ordered-levels)))

(def-loggers)

(comment
  (infof "hello %s" "world")
  (infof (Exception.) "hello %s" "world")
  (infof (Exception.)))

(defmacro log-errors [& body]
  `(let [[?result# ?error#] (enc/catch-errors ~@body)]
     (when-let [e# ?error#] (error e#))
     ?result#))

(defmacro log-and-rethrow-errors [& body]
  `(let [[?result# ?error#] (enc/catch-errors ~@body)]
     (when-let [e# ?error#] (error e#) (throw e#))
     ?result#))

(defmacro logged-future [& body] `(future (log-errors ~@body)))

(comment
  (log-errors             (/ 0))
  (log-and-rethrow-errors (/ 0))
  (logged-future          (/ 0)))

(defmacro spy
  "Evaluates named expression and logs its result. Always returns the result.
  Defaults to :debug logging level and unevaluated expression as name."
  ([                  expr] `(spy :debug ~expr))
  ([       level      expr] `(spy ~level '~expr ~expr))
  ([       level name expr] `(spy *config* ~level ~name ~expr))
  ([config level name expr]
   `(log-and-rethrow-errors
      (let [result# ~expr]
        (log ~config ~level :print [~name "=>" result#])
        result#))))

#+clj
(defn refer-timbre
  "Shorthand for:
  (require '[taoensso.timbre :as timbre
             :refer (log  trace  debug  info  warn  error  fatal  report
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy)])
  (require '[taoensso.timbre.profiling :as profiling
             :refer (pspy pspy* profile defnp p p*)])"
  []
  (require '[taoensso.timbre :as timbre
             :refer (log  trace  debug  info  warn  error  fatal  report
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy)])
  (require '[taoensso.timbre.profiling :as profiling
             :refer (pspy pspy* profile defnp p p*)]))

;;;; Misc public utils

#+clj
(defn color-str [color & xs]
  (let [ansi-color #(format "\u001b[%sm"
                      (case % :reset  "0"  :black  "30" :red   "31"
                              :green  "32" :yellow "33" :blue  "34"
                              :purple "35" :cyan   "36" :white "37"
                              "0"))]
    (str (ansi-color color) (apply str xs) (ansi-color :reset))))

#+clj (def default-out (java.io.OutputStreamWriter. System/out))
#+clj (def default-err (java.io.PrintWriter.        System/err))
(defmacro with-default-outs [& body]
  `(binding [*out* default-out, *err* default-err] ~@body))

#+clj
(def get-hostname
  ;; Note that this triggers slow shutdown, Ref. http://goo.gl/5hx9oK:
  (enc/memoize* (enc/ms :mins 1)
    (fn []
      (let [f_ (future ; Android doesn't like this on the main thread
                 (try (.. java.net.InetAddress getLocalHost getHostName)
                      (catch java.net.UnknownHostException _ "UnknownHost")))]
        (deref f_ 5000 "UnknownHost")))))

(comment (get-hostname))

(defn stacktrace [err & [opts]]
  #+cljs (str err) ; TODO Alternatives?
  #+clj
  (if-let [fonts (:stacktrace-fonts opts)]
    (binding [aviso-ex/*fonts* fonts] (aviso-ex/format-exception err))
    (aviso-ex/format-exception err)))

(comment (stacktrace (Exception. "Boo")))

#+clj
(def ^:private ensure-spit-dir-exists!
  (enc/memoize* (enc/ms :mins 1)
    (fn [fname]
      (when-not (str/blank? fname)
        (let [file (File. ^String fname)
              dir  (.getParentFile (.getCanonicalFile file))]
          (when-not (.exists dir) (.mkdirs dir)))))))

(defmacro sometimes "Handy for sampled logging, etc."
  [probability & body]
  `(do (assert (<= 0 ~probability 1) "Probability: 0 <= p <= 1")
       (when (< (rand) ~probability) ~@body)))
