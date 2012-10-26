(ns taoensso.timbre
  "Simple, flexible, all-Clojure logging. No XML!"
  {:author "Peter Taoussanis"}
  (:require [clojure.string        :as str]
            [clj-stacktrace.repl   :as stacktrace]
            [taoensso.timbre.utils :as utils])
  (:import  [java.util Date Locale]
            [java.text SimpleDateFormat]))

;;;; Default configuration and appenders

(defn str-println
  "Like `println` but prints all objects to output stream as a single
  atomic string. This is faster and avoids interleaving race conditions."
  [& xs]
  (print (str (str/join \space xs) \newline))
  (flush))

(defonce config
  ^{:doc
    "This map atom controls everything about the way Timbre operates. In
    particular note the flexibility to add arbitrary appenders.

    An appender is a map with keys:
      :doc, :min-level, :enabled?, :async?, :max-message-per-msecs, :fn?

    An appender's fn takes a single map argument with keys:
      :level, :message, :more ; From all logging macros (`info`, etc.)
      :profiling-stats        ; From `profile` macro
      :ap-config              ; `shared-appender-config`
      :prefix                 ; Output of `prefix-fn`

      Other keys include: :instant, :timestamp, :hostname, :ns, :error?

    See source code for examples and `utils/deep-merge` for a convenient way
    to reconfigure appenders."}
  (atom {:current-level :debug

         ;;; Control log filtering by namespace patterns (e.g. ["my-app.*"]).
         ;;; Useful for turning off logging in noisy libraries, etc.
         :ns-whitelist []
         :ns-blacklist []

         ;;; Control :timestamp format
         :timestamp-pattern "yyyy-MMM-dd HH:mm:ss ZZ" ; SimpleDateFormat pattern
         :timestamp-locale  nil ; A Locale object, or nil

         ;; Control :prefix format
         :prefix-fn
         (fn [{:keys [level timestamp hostname ns]}]
           (str timestamp " " hostname " " (-> level name str/upper-case)
                " [" ns "]"))

         ;; Will be provided to all appenders via :ap-config key
         :shared-appender-config {}

         :appenders
         {:standard-out
          {:doc "Prints to *out* or *err* as appropriate. Enabled by default."
           :min-level nil :enabled? true :async? false
           :max-message-per-msecs nil
           :fn (fn [{:keys [error? prefix message more]}]
                 (binding [*out* (if error? *err* *out*)]
                   (apply str-println prefix "-" message more)))}

          :spit
          {:doc "Spits to (:spit-filename :shared-appender-config) file."
           :min-level nil :enabled? false :async? false
           :max-message-per-msecs nil
           :fn (fn [{:keys [ap-config prefix message more]}]
                 (when-let [filename (:spit-filename ap-config)]
                   (try (spit filename
                              (with-out-str (apply str-println prefix "-"
                                                   message more))
                              :append true)
                        (catch java.io.IOException _))))}}}))

(defn set-config! [[k & ks] val] (swap! config assoc-in (cons k ks) val))
(defn set-level!  [level] (set-config! [:current-level] level))

;;;; Define and sort logging levels

(def ^:private ordered-levels [:trace :debug :info :warn :error :fatal :report])
(def ^:private scored-levels  (assoc (zipmap ordered-levels (range)) nil 0))

(defn error-level? [level] (boolean (#{:error :fatal} level)))

(defn- checked-level-score [level]
  (or (scored-levels level)
      (throw (Exception. (str "Invalid logging level: " level)))))

(def compare-levels
  (memoize (fn [x y] (- (checked-level-score x) (checked-level-score y)))))

(defn sufficient-level?
  [level] (>= (compare-levels level (:current-level @config)) 0))

;;;; Appender-fn decoration

(defn- make-timestamp-fn
  "Returns a unary fn that formats instants using given pattern string and an
  optional Locale."
  [^String pattern ^Locale locale]
  (let [format (if locale
                 (SimpleDateFormat. pattern locale)
                 (SimpleDateFormat. pattern))]
    (fn [^Date instant] (.format ^SimpleDateFormat format instant))))

(comment ((make-timestamp-fn "yyyy-MMM-dd" nil) (Date.)))

(def get-hostname
  (utils/memoize-ttl
   60000 (fn [] (.. java.net.InetAddress getLocalHost getHostName))))

(defn- wrap-appender-fn
  "Wraps compile-time appender fn with additional runtime capabilities
  controlled by compile-time config."
  [{apfn :fn :keys [async? max-message-per-msecs] :as appender}]
  (->
   ;; Wrap to add compile-time stuff to runtime appender arguments
   (let [{ap-config :shared-appender-config
          :keys [timestamp-pattern timestamp-locale prefix-fn]} @config

          timestamp-fn (make-timestamp-fn timestamp-pattern timestamp-locale)]

     (fn [{:keys [instant] :as apfn-args}]
       (let [apfn-args (merge apfn-args {:ap-config ap-config
                                         :timestamp (timestamp-fn instant)
                                         :hostname  (get-hostname)})]
         (apfn (assoc apfn-args :prefix (prefix-fn apfn-args))))))

   ;; Wrap for asynchronicity support
   ((fn [apfn]
      (if-not async?
        apfn
        (let [agent (agent nil :error-mode :continue)]
          (fn [apfn-args] (send-off agent (fn [_] (apfn apfn-args))))))))

   ;; Wrap for runtime flood-safety support
   ((fn [apfn]
      (if-not max-message-per-msecs
        apfn
        (let [;; {:msg last-appended-time-msecs ...}
              flood-timers (atom {})]

          (fn [{:keys [message] :as apfn-args}]
            (let [now    (System/currentTimeMillis)
                  allow? (fn [last-msecs]
                           (if last-msecs
                             (> (- now last-msecs) max-message-per-msecs)
                             true))]

              (when (allow? (@flood-timers message))
                (apfn apfn-args)
                (swap! flood-timers assoc message now))

              ;; Occassionally garbage-collect all expired timers. Note
              ;; that due to snapshotting, garbage-collection can cause
              ;; some appenders to re-append prematurely.
              (when (< (rand) 0.001)
                (let [timers-snapshot @flood-timers
                      expired-timers
                      (->> (keys timers-snapshot)
                           (filter #(allow? (timers-snapshot %))))]
                  (when (seq expired-timers)
                    (apply swap! flood-timers dissoc expired-timers))))))))))))

;;;; Caching

;;; Appender-fns

(def appenders-juxt-cache
  "Per-level, combined relevant appender-fns to allow for fast runtime
  appender-fn dispatch:
  {:level (juxt wrapped-appender-fn wrapped-appender-fn ...) or nil
    ...}"
  (atom {}))

(defn- relevant-appenders
  [level]
  (->> (:appenders @config)
       (filter #(let [{:keys [enabled? min-level]} (val %)]
                  (and enabled? (>= (compare-levels level min-level) 0))))
       (into {})))

(comment (relevant-appenders :debug)
         (relevant-appenders :trace))

(defn- cache-appenders-juxt!
  []
  (->>
   (zipmap
    ordered-levels
    (->> ordered-levels
         (map (fn [l] (let [rel-aps (relevant-appenders l)]
                       ;; Return nil if no relevant appenders
                       (when-let [ap-ids (keys rel-aps)]
                         (->> ap-ids
                              (map #(wrap-appender-fn (rel-aps %)))
                              (apply juxt))))))))
   (reset! appenders-juxt-cache)))

;;; Namespace filter ; TODO Generalize to arbitrary fn filters?

(def ns-filter-cache "@ns-filter-cache => (fn relevant-ns? [ns] ...)"
  (atom (constantly true)))

(defn- ns-match?
  [ns match]
  (-> (str "^" (-> (str match) (.replace "." "\\.") (.replace "*" "(.*)")) "$")
      re-pattern (re-find (str ns)) boolean))

(defn- cache-ns-filter!
  []
  (->>
   (let [{:keys [ns-whitelist ns-blacklist]} @config]
     (memoize
      (fn relevant-ns? [ns]
        (and (or (empty? ns-whitelist)
                 (some (partial ns-match? ns) ns-whitelist))
             (or (empty? ns-blacklist)
                 (not-any? (partial ns-match? ns) ns-blacklist))))))
   (reset! ns-filter-cache)))

;;; Prime initial caches and re-cache on config change

(cache-appenders-juxt!)
(cache-ns-filter!)

(add-watch
 config "config-cache-watch"
 (fn [key ref old-state new-state]
   (when (not= (dissoc old-state :current-level)
               (dissoc new-state :current-level))
     (cache-appenders-juxt!)
     (cache-ns-filter!))))

;;;; Define logging macros

(defmacro logging-enabled?
  "Returns true when current logging level is sufficient and current namespace
  is unfiltered."
  [level]
  `(and (sufficient-level? ~level) (@ns-filter-cache ~*ns*)))

(defmacro log*
  "Prepares given arguments for, and then dispatches to all relevant
  appender-fns."
  [level base-args & sigs]
  `(when-let [juxt-fn# (@appenders-juxt-cache ~level)] ; Any relevant appenders?
     (let [[x1# & xs#] (list ~@sigs)

           has-throwable?# (instance? Throwable x1#)
           appender-args#
           (conj
            ~base-args ; Allow flexibility to inject exta args
            {:level   ~level
             :error?  (error-level? ~level)
             :instant (Date.)
             :ns      (str ~*ns*)
             :message (if has-throwable?# (or (first xs#) x1#) x1#)
             :more    (if has-throwable?#
                        (conj (vec (rest xs#))
                              (str "\n" (stacktrace/pst-str x1#)))
                        (vec xs#))})]

       (juxt-fn# appender-args#)
       nil)))

(defmacro log
  "When logging is enabled, actually logs given arguments with relevant
  appender-fns. Generic form of standard level-loggers (trace, info, etc.)."
  {:arglists '([level message & more] [level throwable message & more])}
  [level & sigs]
  `(when (logging-enabled? ~level)
     (log* ~level {} ~@sigs)))

(defmacro spy
  "Evaluates named expression and logs its result. Always returns the result.
  Defaults to :debug logging level and unevaluated expression as name."
  ([expr] `(spy :debug ~expr))
  ([level expr] `(spy ~level '~expr ~expr))
  ([level name expr]
     `(try
        (let [result# ~expr] (log ~level ~name ~expr) result#)
        (catch Exception e#
          (log ~level '~expr (str "\n" (stacktrace/pst-str e#)))
          (throw e#)))))

(defmacro ^:private def-logger
  [level]
  (let [level-name (name level)]
    `(defmacro ~(symbol level-name)
       ~(str "Log given arguments at " (str/capitalize level-name) " level.")
       ~'{:arglists '([message & more] [throwable message & more])}
       [& sigs#]
       `(log ~~level ~@sigs#))))

(defmacro ^:private def-loggers
  [] `(do ~@(map (fn [level] `(def-logger ~level)) ordered-levels)))

(def-loggers) ; Actually define a logger for each logging level

;;;; Dev/tests

(comment
  (log :fatal "arg1")
  (log :debug "arg1" "arg2")
  (log :debug (Exception.) "arg1" "arg2")
  (log :debug (Exception.))
  (log :trace "arg1")

  (log (or nil :info) "Booya")

  (set-config! [:ns-blacklist] [])
  (set-config! [:ns-blacklist] ["taoensso.timbre*"])

  (info "foo" "bar")
  (trace (Thread/sleep 5000))
  (time (dotimes [n 10000] (trace "This won't log"))) ; Overhead 5ms/10ms
  (time (dotimes [n 5] (info "foo" "bar")))
  (spy (* 6 5 4 3 2 1))
  (spy :debug :factorial6 (* 6 5 4 3 2 1))
  (info (Exception. "noes!") "bar")
  (spy (/ 4 0)))