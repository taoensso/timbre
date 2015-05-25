(ns taoensso.timbre
  "Simple, flexible logging for Clojure/Script. No XML."
  {:author "Peter Taoussanis"}

  #+clj
  (:require [clojure.string     :as str]
            [io.aviso.exception :as aviso-ex]
            [taoensso.encore    :as enc :refer (have have? qb)])

  #+cljs
  (:require [clojure.string  :as str]
            [taoensso.encore :as enc :refer ()])
  #+cljs
  (:require-macros
   [taoensso.encore :as enc :refer (have have?)])

  #+clj
  (:import [java.util Date Locale]
           [java.text SimpleDateFormat]
           [java.io File]))

;;;; TODO
;; - Check for successful cljs compile
;; - Bump encore version + min version check
;; - Clj default appenders
;; - Simple config flag to log std err -> out
;; - Cljs default appenders
;; - Port profiling ns (cljs support?)
;; - Document shutdown-agents,
;;   Ref. https://github.com/ptaoussanis/timbre/pull/100/files
;; - Try ease backward comp
;; - Port appenders
;; - Update README, CHANGELOG

;;;; Encore version check

#+clj
(let [min-encore-version 1.30]
  (if-let [assert! (ns-resolve 'taoensso.encore 'assert-min-encore-version)]
    (assert! min-encore-version)
    (throw
      (ex-info
        (format
          "Insufficient com.taoensso/encore version (< %s). You may have a Leiningen dependency conflict (see http://goo.gl/qBbLvC for solution)."
          min-encore-version)
        {:min-version min-encore-version}))))

;;;; Config

(def example-config
  "Example (+default) Timbre config map." ; TODO
  {:level :debug
   :appenders ; TODO
   {:println
    {:min-level nil :enabled? true :async? false :rate-limit nil
     :fn (fn [data]
           (println ((:output-fn data) data)))}}})

(enc/defonce* ^:dynamic *config* example-config)
(defmacro with-config [config & body] `(binding [*config* ~config] ~@body))
(defn swap-config! [f]
  #+cljs (set!             *config* (f *config*))
  #+clj  (alter-var-root #'*config*  f))

(defn   set-config! [m] (swap-config! (fn [_] m)))
(defn merge-config! [m] (swap-config! (fn [old] (enc/nested-merge old m))))

(defn set-level! [level] (swap-config! (fn [m] (merge m {:level level}))))
(defn with-level [level & body]
  `(binding [*config* (merge *config* {:level ~level})] ~@body))

(comment (set-level! :info) *config*)

;;;; Levels

(def ^:private ordered-levels [:trace :debug :info :warn :error :fatal :report])
(def ^:private scored-levels  (zipmap ordered-levels (next (range))))
(def ^:private valid-level
  (let [valid-level-set (set ordered-levels)]
    (fn [level]
      (or (valid-level-set level)
        (throw (ex-info (str "Invalid logging level: " level) {:level level}))))))

(comment (valid-level :info))

(defn level>= [x y] (>= (long (scored-levels (valid-level x)))
                        (long (scored-levels (valid-level y)))))

(comment (level>= :info :debug))

#+clj (defn- env-val [id] (when-let [s (System/getenv id)] (enc/read-edn s)))
#+clj (def ^:private compile-time-level
        (have [:or nil? valid-level] (keyword (env-val "TIMBRE_LEVEL"))))

(defn get-active-level [& [config]] (or (:level (or config *config*)) :report))
(comment (qb 10000 (get-active-level)))

(comment (binding [*config* {:level :trace}] (level>= :trace (get-active-level))))

;;;; ns filter

(def ^:private compile-ns-filters
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
                ;; (nil? whitelist)  (fn [ns] false)
                (empty?  whitelist*) (fn [ns] true)
                :else (fn [ns] (some #(re-find % ns) whitelist*)))

              black-filter
              (cond
                (empty? blacklist*) (fn [ns] true)
                :else (fn [ns] (not (some #(re-find % ns) blacklist*))))]

          [white-filter black-filter])))))

(def ^:private ns-filter
  (enc/memoize_
    (fn [whitelist blacklist ns]
      (let [[white-filter black-filter] (compile-ns-filters whitelist blacklist)]
        (when (and (white-filter ns) (black-filter ns)) ns)))))

(comment (qb 10000 (ns-filter ["foo.*"] ["foo.baz"] "foo.bar")))

#+clj
(def ^:private compile-time-ns-filter
  (let [whitelist (have [:or nil? vector?] (env-val "TIMBRE_NS_WHITELIST"))
        blacklist (have [:or nil? vector?] (env-val "TIMBRE_NS_BLACKLIST"))]
    (partial ns-filter whitelist blacklist)))

;;;; Utils

(defmacro delay-vec [coll] (mapv (fn [in] `(delay ~in)) coll))
(comment
  (qb 10000 (delay :foo) (fn [] :foo))
  (macroexpand '(delay-vec [(do (println "hi") :x) :y :z])))

(defn- vsplit-err1 [[v1 :as v]] (if-not (enc/error? v1) [nil v] (enc/vsplit-first v)))
(comment
  (vsplit-err1 [:a :b :c])
  (vsplit-err1 [(Exception.) :a :b :c]))

(declare stacktrace)

(defn default-output-fn [data & [opts]]
  (let [{:keys [level ?err_ vargs_ msg-fn ?ns-str hostname_ timestamp_]} data]
    (str (force timestamp_) " "
      #+clj @hostname_ #+clj " "
      (str/upper-case (name level))
      " [" ?ns-str "] - " (msg-fn vargs_)
      (when-let [err (force ?err_)] (str "\n" (stacktrace err))))))

(comment (infof (Exception.) "Hello %s" "Steve"))

(defn default-data-hash-fn [data]
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

(defn log? [level & [?ns-str config]]
  (let [config (or config *config*)]
    (and (level>= level (get-active-level config))
         (ns-filter (:whitelist config) (:blacklist config) (or ?ns-str ""))
         true)))

(comment (log? :trace))

(def ^:dynamic *context* "General-purpose dynamic logging context." nil)
(defmacro with-context [context & body] `(binding [*context* ~context] ~@body))

(declare get-hostname)

;;;; TODO Temp, work on timestamps
;; want a simple, pattern-based 

(def ^:private default-timestamp-pattern "14-Jul-07 16:42:11"
  "yy-MMM-dd HH:mm:ss")

(.format
  (enc/simple-date-format default-timestamp-pattern
    {:locale (Locale. "en")
     ;; :timezone "foo"
     }) (enc/now-dt))



;;;; TODO


(defn log* "Core fn-level logger. Implementation detail."
  [config level ?ns-str ?file ?line msg-type dvargs & [base-data]]
  (when (log? level ?ns-str config)
    (let [instant  (enc/now-dt)
          vargs*_  (delay (vsplit-err1 (mapv force dvargs)))
          ?err_    (delay (get @vargs*_ 0))
          vargs_   (delay (get @vargs*_ 1))
          msg-fn   (fn [vargs_] ; Post-middleware vargs, etc.
                     (when-not (nil? msg-type)
                       (when-let [vargs (have [:or nil? vector?] (force vargs_))]
                         (case msg-type
                           :print  (enc/spaced-str vargs)
                           :format (let [[fmt args] (enc/vsplit-first vargs)]
                                     (enc/format* fmt args))))))
          data
          (merge base-data *context*
            {:config  config ; Entire config!
             :instant instant
             :level   level
             :?ns-str ?ns-str
             :?file   ?file
             :?line   ?line
             :?err_   ?err_
             :vargs_  vargs_
             :msg-fn  msg-fn
             #+clj :hostname_ #+clj (delay (get-hostname))
             :error-level? (#{:error :fatal} level)})

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
                    output-fn (or (:output-fn appender)
                                  (:output-fn config)
                                  default-output-fn)

                    ;; TODO Grab config (tz, pattern, locale, etc.) from 
                    timestamp_ (delay "TODO")
                    
                    
                    data (assoc data :output-fn
                                )]

                             :timestamp_   timestamp_
             ;; :output-fn output-fn

             ;; :timestamp_   (delay "maybe?") ; TODO Nix?

             
             

             
                (if-not async?
                  (apfn data)
                  (send-off (get-agent id) (fn [_] (apfn data)))))))
          nil
          (enc/clj1098 (:appenders config))))))
  nil)

(comment
  (log* *config* :info
    nil nil nil :print (delay-vec [(do (println "hi") :x) :y])))

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
           (delay-vec ~args) ~base-data)))))

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

(comment (infof "hello %s" "world"))

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

;;;; Public utils

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



;;;; TODO Scratch
;; :keys [timestamp-pattern timestamp-locale
;;        prefix-fn fmt-output-fn]} config
;; timestamp-fn
;; (if-not timestamp-pattern (constantly nil)
;;         (fn [^Date dt]
;;           (.format (enc/simple-date-format timestamp-pattern
;;                      {:locale timestamp-locale}) dt)))]
