(ns taoensso.timbre
  "Simple, flexible logging for Clojure/Script. No XML."
  {:author "Peter Taoussanis"}
  #+clj
  (:require
   [clojure.string     :as str]
   [io.aviso.exception :as aviso-ex]
   [taoensso.encore    :as enc :refer (compile-if have have? qb)]
   [taoensso.timbre.appenders.core :as core-appenders])

  #+cljs
  (:require
   [clojure.string  :as str]
   [taoensso.encore :as enc :refer () :refer-macros (compile-if have have?)]
   [taoensso.timbre.appenders.core :as core-appenders])

  #+cljs
  (:require-macros [taoensso.timbre :as timbre-macros :refer ()]))

(if (vector? taoensso.encore/encore-version)
  (enc/assert-min-encore-version [2 29 1])
  (enc/assert-min-encore-version  2.29))

;;;; Config

#+clj
(def default-timestamp-opts
  "Controls (:timestamp_ data)"
  {:pattern     "yy-MM-dd HH:mm:ss" #_:iso8601
   :locale      :jvm-default #_(java.util.Locale. "en")
   :timezone    :utc         #_(java.util.TimeZone/getTimeZone "Europe/Amsterdam")})

(declare stacktrace)
(defn default-output-fn
  "Default (fn [data]) -> string output fn.
  You can modify default options with `(partial default-output-fn <opts-map>)`."
  ([data] (default-output-fn nil data))
  ([{:keys [no-stacktrace? stacktrace-fonts] :as opts} data]
   (let [{:keys [level ?err_ vargs_ msg_ ?ns-str hostname_ timestamp_]} data]
     (str
       #+clj (force timestamp_) #+clj " "
       #+clj (force hostname_)  #+clj " "
       (str/upper-case (name level))  " "
       "[" (or ?ns-str "?ns") "] - "
       (force msg_)
       (when-not no-stacktrace?
         (when-let [err (force ?err_)]
           (str "\n" (stacktrace err opts))))))))

;;; Alias core appenders here for user convenience
(declare default-err default-out)
#+clj  (enc/defalias          core-appenders/println-appender)
#+clj  (enc/defalias          core-appenders/spit-appender)
#+cljs (def println-appender  core-appenders/println-appender)
#+cljs (def console-?appender core-appenders/console-?appender)

(def example-config
  "Example (+default) Timbre v4 config map.

  APPENDERS
    An appender is a map with keys:
      :min-level       ; Level keyword, or nil (=> no minimum level)
      :enabled?        ;
      :async?          ; Dispatch using agent? Useful for slow appenders
      :rate-limit      ; [[ncalls-limit window-ms] <...>], or nil
      :output-fn       ; Optional override for inherited (fn [data]) -> string
      :fn              ; (fn [data]) -> side effects, with keys described below

    An appender's fn takes a single data map with keys:
      :config          ; Entire config map (this map, etc.)
      :appender-id     ; Id of appender currently dispatching
      :appender        ; Entire map of appender currently dispatching

      :instant         ; Platform date (java.util.Date or js/Date)
      :level           ; Keyword
      :error-level?    ; Is level e/o #{:error :fatal}?
      :?ns-str         ; String, or nil
      :?file           ; String, or nil  ; Waiting on CLJ-865
      :?line           ; Integer, or nil ; Waiting on CLJ-865

      :?err_           ; Delay - first-arg platform error, or nil
      :vargs_          ; Delay - raw args vector
      :hostname_       ; Delay - string (clj only)
      :msg_            ; Delay - args string
      :timestamp_      ; Delay - string
      :output-fn       ; (fn [data]) -> formatted output string
                       ; (see `default-output-fn` for details)

      :context         ; *context* value at log time (see `with-context`)
      :profile-stats   ; From `profile` macro

  MIDDLEWARE
    Middleware are simple (fn [data]) -> ?data fns (applied left->right) that
    transform the data map dispatched to appender fns. If any middleware returns
    nil, NO dispatching will occur (i.e. the event will be filtered).

  The `example-config` source code contains further settings and details.
  See also `set-config!`, `merge-config!`, `set-level!`."

  {:level :debug  ; e/o #{:trace :debug :info :warn :error :fatal :report}

   ;; Control log filtering by namespaces/patterns. Useful for turning off
   ;; logging in noisy libraries, etc.:
   :ns-whitelist  [] #_["my-app.foo-ns"]
   :ns-blacklist  [] #_["taoensso.*"]

   :middleware [] ; (fns [data]) -> ?data, applied left->right

   #+clj :timestamp-opts
   #+clj default-timestamp-opts ; {:pattern _ :locale _ :timezone _}

   :output-fn default-output-fn ; (fn [data]) -> string

   :appenders
   #+clj
   {:println (println-appender {:stream :auto})
    ;; :spit (spit-appender {:fname "./timbre-spit.log"})
    }

   #+cljs
   {;; :println (println-appender {})
    :console (console-?appender {})}})

(comment
  (set-config! example-config)
  (infof "Hello %s" "world :-)"))

(enc/defonce* ^:dynamic *config* "See `example-config` for info." example-config)
(defmacro with-config        [config & body] `(binding [*config* ~config] ~@body))
(defmacro with-merged-config [config & body]
  `(binding [*config* (enc/nested-merge *config* ~config)] ~@body))

(defn swap-config! [f & args]
  #+cljs (set!                   *config* (apply f *config* args))
  #+clj  (apply alter-var-root #'*config* f args))

(defn   set-config! [m] (swap-config! (fn [_old] m)))
(defn merge-config! [m] (swap-config! (fn [old] (enc/nested-merge old m))))

(defn     set-level! [level] (swap-config! (fn [m] (merge m {:level level}))))
(defmacro with-level [level & body]
  `(binding [*config* (merge *config* {:level ~level})] ~@body))

(comment (set-level! :info) *config*)

;;;; Levels

(def ordered-levels [:trace :debug :info :warn :error :fatal :report])
(def ^:private scored-levels (zipmap ordered-levels (next (range))))
(def ^:private valid-levels  (set ordered-levels))
(def ^:private valid-level
  (fn [level]
    (or (valid-levels level)
        (throw (ex-info (str "Invalid logging level: " level) {:level level})))))

(comment (valid-level :info))

(defn level>= [x y] (>= (long (scored-levels (valid-level x)))
                        (long (scored-levels (valid-level y)))))

(comment (qb 10000 (level>= :info :debug)))

#+clj (defn- env-val [id] (when-let [s (System/getenv id)] (enc/read-edn s)))
#+clj (def ^:private compile-time-level
        (have [:or nil? valid-level]
          (keyword (or (env-val "TIMBRE_LEVEL")
                       (env-val "TIMBRE_LOG_LEVEL")))))

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

    (when compile-time-level
      (println (str "Compile-time (elision) Timbre level: " compile-time-level)))
    (when whitelist
      (println (str "Compile-time (elision) Timbre ns whitelist: " whitelist)))
    (when blacklist
      (println (str "Compile-time (elision) Timbre ns blacklist: " blacklist)))

    (partial ns-filter whitelist blacklist)))

;;;; Utils

(enc/compile-if (do enc/str-join true) ; Encore v2.29.1+ with transducers
  (defn- str-join [xs]
    (enc/str-join " "
      (map
        (fn [x]
          (let [x (enc/nil->str x)] ; Undefined, nil -> "nil"
            (cond
              (record?          x) (pr-str x)
              ;; (enc/lazy-seq? x) (pr-str x) ; Dubious?
              :else x))))
      xs))
  (defn- str-join [xs] (enc/spaced-str-with-nils xs)))

(comment
  (defrecord MyRec [x])
  (str-join ["foo" (MyRec. "foo")]))

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

#+clj
(enc/defonce* ^:private get-agent
  (enc/memoize_ (fn [appender-id] (agent nil :error-mode :continue))))

(comment (get-agent :my-appender))

(enc/defonce* ^:private get-rate-limiter
  (enc/memoize_ (fn [appender-id specs] (enc/rate-limiter* specs))))

(comment (def rf (get-rate-limiter :my-appender [[10 5000]])))

;;;; Internal logging core

(defn log?
  "Would Timbre currently log at the given logging level?
    * Compile-time `?ns-str` arg required to support ns filtering.
    * `config` arg required to support non-global config."
  [level & [?ns-str config]]
  (let [config (or config *config*)
        active-level (or (:level config) :report)]
    (and (level>= level active-level)
         (ns-filter (:ns-whitelist config) (:ns-blacklist config) (or ?ns-str ""))
         true)))

(comment
  (set-level! :debug)
  (log? :trace)
  (with-level :trace (log? :trace))
  (qb 10000 (log? :trace))       ; ~2.5ms
  (qb 10000 (log? :trace "foo")) ; ~6ms
  (qb 10000 (tracef "foo"))      ; ~7.5ms
  (qb 10000 (when false "foo"))  ; ~0.5ms

  ;;; Full benchmarks
  (defmacro with-sole-appender [appender & body]
    `(with-config (assoc *config* :appenders {:appender ~appender}) ~@body))

  (with-sole-appender {:enabled? true :fn (fn [data] nil)}
    (qb 10000 (info "foo"))) ; ~88ms ; Time to delays ready

  (with-sole-appender {:enabled? true :fn (fn [data] ((:output-fn data) data))}
    (qb 10000 (info "foo"))) ; ~218ms ; Time to output ready
  )

(def ^:dynamic *context*
  "General-purpose dynamic logging context. Context will be included in appender
  data map at logging time." nil)
(defmacro with-context [context & body] `(binding [*context* ~context] ~@body))

(declare get-hostname)

(defn- inherit-over [k appender config default]
  (or
    (let [a (get appender k)] (when-not (enc/kw-identical? a :inherit) a))
    (get config k)
    default))

(defn- inherit-into [k appender config default]
  (merge default
    (get config k)
    (let [a (get appender k)] (when-not (enc/kw-identical? a :inherit) a))))

(comment
  (inherit-over :foo {:foo :inherit} {:foo :bar} nil)
  (inherit-into :foo {:foo {:a :A :b :B :c :C}} {:foo {:a 1 :b 2 :c 3 :d 4}} nil))

(defn log1-fn
  "Core fn-level logger. Implementation detail!"
  [config level ?ns-str ?file ?line msg-type vargs_ ?base-data]
  (when (log? level ?ns-str config)
    (let [instant (enc/now-dt)
          vargs*_ (delay (vsplit-err1 (force vargs_)))
          ?err_   (delay (get @vargs*_ 0))
          vargs_  (delay (get @vargs*_ 1))
          context *context*
          data    (merge ?base-data
                    ;; No, better nest than merge (appenders may want to pass
                    ;; along arb context w/o knowing context keys, etc.):
                    (when (map? context) context) ; DEPRECATED, for back compat
                    {:config  config ; Entire config!
                     :context context
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
                  :p (str-join vargs)
                  :f (let [[fmt args] (enc/vsplit-first vargs)]
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
            (when (and (:enabled? appender)
                       (level>= level (or (:min-level appender) :trace)))

              (let [rate-limit-specs (:rate-limit appender)
                    data-hash-fn (inherit-over :data-hash-fn appender config
                                   default-data-hash-fn)
                    rate-limit-okay?
                    (or (empty? rate-limit-specs)
                        (let [rl-fn     (get-rate-limiter id rate-limit-specs)
                              data-hash (data-hash-fn data)]
                          (not (rl-fn data-hash))))]

                (when rate-limit-okay?
                  (let [{:keys [async?] apfn :fn} appender
                        msg_      (delay (or (msg-fn (:vargs_ data)) #_""))
                        output-fn (inherit-over :output-fn appender config
                                    default-output-fn)

                        #+clj timestamp_
                        #+clj
                        (delay
                          (let [timestamp-opts (inherit-into :timestamp-opts
                                                 appender config
                                                 default-timestamp-opts)
                                {:keys [pattern locale timezone]} timestamp-opts]
                            (.format (enc/simple-date-format pattern
                                       {:locale locale :timezone timezone})
                              (:instant data))))

                        data ; Final data prep before going to appender
                        (merge data
                          {:appender-id  id
                           :appender     appender
                           ;; :appender-opts (:opts appender) ; For convenience
                           :msg_         msg_
                           :msg-fn       msg-fn
                           :output-fn    output-fn
                           :data-hash-fn data-hash-fn
                           #+clj :timestamp_ #+clj timestamp_})]

                    (if-not async?
                      (apfn data) ; Allow errors to throw
                      #+cljs (apfn data)
                      #+clj  (send-off (get-agent id) (fn [_] (apfn data)))))))))
          nil
          (enc/clj1098 (:appenders config))))))
  nil)

(comment
  (log1-fn *config* :info nil nil nil :p (delay [(do (println "hi") :x) :y]) nil))

(defmacro log1-macro
  "Core macro-level logger. Implementation detail!"
  [config level msg-type args & [?base-data]]

  ;; Compile-time elision:
  (when (or (nil? compile-time-level)
            (not (valid-levels level)) ; Not a compile-time level
            (level>= level compile-time-level))

    (when (compile-time-ns-filter (str *ns*))

      (let [ns-str (str *ns*)
            ?file  (let [f *file*] (when (not= f "NO_SOURCE_PATH") f))
            ;; TODO Waiting on http://dev.clojure.org/jira/browse/CLJ-865:
            ?line  (:line (meta &form))]
        `(log1-fn ~config ~level ~ns-str ~?file ~?line ~msg-type
           (delay [~@args]) ~?base-data)))))

;;;; API-level stuff

;;; Log using print-style args
(defmacro log* [config level & args] `(log1-macro ~config  ~level  :p ~args))
(defmacro log         [level & args] `(log1-macro *config* ~level  :p ~args))
(defmacro trace             [& args] `(log1-macro *config* :trace  :p ~args))
(defmacro debug             [& args] `(log1-macro *config* :debug  :p ~args))
(defmacro info              [& args] `(log1-macro *config* :info   :p ~args))
(defmacro warn              [& args] `(log1-macro *config* :warn   :p ~args))
(defmacro error             [& args] `(log1-macro *config* :error  :p ~args))
(defmacro fatal             [& args] `(log1-macro *config* :fatal  :p ~args))
(defmacro report            [& args] `(log1-macro *config* :report :p ~args))

;;; Log using format-style args
(defmacro logf* [config level & args] `(log1-macro ~config  ~level  :f ~args))
(defmacro logf         [level & args] `(log1-macro *config* ~level  :f ~args))
(defmacro tracef             [& args] `(log1-macro *config* :trace  :f ~args))
(defmacro debugf             [& args] `(log1-macro *config* :debug  :f ~args))
(defmacro infof              [& args] `(log1-macro *config* :info   :f ~args))
(defmacro warnf              [& args] `(log1-macro *config* :warn   :f ~args))
(defmacro errorf             [& args] `(log1-macro *config* :error  :f ~args))
(defmacro fatalf             [& args] `(log1-macro *config* :fatal  :f ~args))
(defmacro reportf            [& args] `(log1-macro *config* :report :f ~args))

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

#+clj
(defn handle-uncaught-jvm-exceptions!
  "Sets JVM-global DefaultUncaughtExceptionHandler."
  [& [handler]]
  (let [handler
        (or handler
          (fn [throwable ^Thread thread]
            (errorf throwable "Uncaught exception on thread: %s"
              (.getName thread))))]

    (Thread/setDefaultUncaughtExceptionHandler
      (reify Thread$UncaughtExceptionHandler
        (uncaughtException [this thread throwable] (handler throwable thread))))))

(comment
  (log-errors             (/ 0))
  (log-and-rethrow-errors (/ 0))
  (logged-future          (/ 0))
  (handle-uncaught-jvm-exceptions!))

(defmacro spy
  "Evaluates named expression and logs its result. Always returns the result.
  Defaults to :debug logging level and unevaluated expression as name."
  ([                  expr] `(spy :debug ~expr))
  ([       level      expr] `(spy ~level '~expr ~expr))
  ([       level name expr] `(spy *config* ~level ~name ~expr))
  ([config level name expr]
   `(log-and-rethrow-errors
      (let [result# ~expr]
        (log* ~config ~level ~name "=>" result#) ; Subject to elision
        result# ; NOT subject to elision
        ))))

(defmacro get-env [] `(zipmap '~(keys &env) [~@(keys &env)]))
(defmacro log-env
  "Logs named &env value.
  Defaults to :debug logging level and \"&env\" as name."
  ([                 ] `(log-env :debug))
  ([       level     ] `(log-env ~level "&env"))
  ([       level name] `(log-env *config* ~level ~name))
  ([config level name] `(log* ~config ~level ~name "=>" (get-env))))

(comment
  (macroexpand '(log-env))
  (defn foo [x y] (log-env) (+ x y))
  (foo 5 10))

#+clj
(defn refer-timbre
  "Shorthand for:
  (require '[taoensso.timbre :as timbre
             :refer (log  trace  debug  info  warn  error  fatal  report
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy get-env log-env)])
  (require '[taoensso.timbre.profiling :as profiling
             :refer (pspy pspy* profile defnp p p*)])"
  []
  (require '[taoensso.timbre :as timbre
             :refer (log  trace  debug  info  warn  error  fatal  report
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy get-env log-env)])
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
  (enc/memoize* (enc/ms :mins 1)
    (fn []
      ;; Android doesn't like this on the main thread. Would use a `future` but
      ;; that starts the Clojure agent threadpool which can slow application
      ;; shutdown w/o a `(shutdown-agents)` call
      (let [executor  (java.util.concurrent.Executors/newSingleThreadExecutor)
            f_ (.submit executor ^java.util.concurrent.Callable
                 (fn []
                   (try
                     (.. java.net.InetAddress getLocalHost getHostName)
                     (catch java.net.UnknownHostException _ "UnknownHost")
                     (finally (.shutdown executor)))))]

        (deref f_ 5000 "UnknownHost")))))

(comment (get-hostname))

(defn stacktrace [err & [{:keys [stacktrace-fonts] :as opts}]]
  #+cljs (str err) ; TODO Alternatives?
  #+clj
  (let [stacktrace-fonts (if (and (nil? stacktrace-fonts)
                                  (contains? opts :stacktrace-fonts))
                           {} stacktrace-fonts)]
    (if-let [fonts stacktrace-fonts]
      (binding [aviso-ex/*fonts* fonts] (aviso-ex/format-exception err))
      (aviso-ex/format-exception err))))

(comment (stacktrace (Exception. "Boo") {:stacktrace-fonts nil}))

(defmacro sometimes "Handy for sampled logging, etc."
  [probability & body]
   `(do (assert (<= 0 ~probability 1) "Probability: 0 <= p <= 1")
        (when (< (rand) ~probability) ~@body)))

;;;; Deprecated

(defn str-println [& xs] (str-join xs))
(defmacro with-log-level      [level  & body] `(with-level  ~level  ~@body))
(defmacro with-logging-config [config & body] `(with-config ~config ~@body))
(defn logging-enabled? [level compile-time-ns] (log? level (str compile-time-ns)))
(defmacro logp [& sigs] `(log ~@sigs))
