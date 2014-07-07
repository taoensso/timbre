(ns taoensso.timbre "Simple, flexible, all-Clojure logging. No XML!"
  {:author "Peter Taoussanis"}
  (:require [clojure.string     :as str]
            [io.aviso.exception :as aviso-ex]
            [taoensso.encore    :as enc])
  (:import  [java.util Date Locale]
            [java.text SimpleDateFormat]
            [java.io File]))

;;;; Encore version check

(let [min-encore-version 1.21] ; Let's get folks on newer versions here
  (if-let [assert! (ns-resolve 'taoensso.encore 'assert-min-encore-version)]
    (assert! min-encore-version)
    (throw
      (ex-info
        (format
          "Insufficient com.taoensso/encore version (< %s). You may have a Leiningen dependency conflict (see http://goo.gl/qBbLvC for solution)."
          min-encore-version)
        {:min-version min-encore-version}))))

;;;; TODO v4
;; * Use `format`, `sprintln` from Encore.
;; * Decide on `:message` format design
;;   - No delay, just require use of tool?
;;   - Delay set (as wrapper) per-appender with merged `:ap-config`?
;;     - :msg-type e/o #{:tools.logging :print-str :pr-str :format nil}
;;     - :message key is set [only, ever] at per-appender wrapper level.
;;     - Pros: great flexibility with easy config, simple.
;;     - Cons: cost of per-appender delay generation. Problem?
;; * Get core working + tested.
;; * Enumerate changes from v3.
;; * Look into v3 backwards compatibility.
;; * Document changes from v3.
;; * Update bundled appenders (?).
;; * Update docs.
;;
;; * Investigate better encore/Cljs interplay: fns?
;; * Do runtime level check even if a compile-time level is in effect if the
;;   provided `log` level arg is not immediately recognized (e.g. it may be a
;;   runtime level form that first requires eval).

;;;; Public utils

(defn str-println
  "Like `println` but prints all objects to output stream as a single
  atomic string. This is faster and avoids interleaving race conditions."
  [& xs] (print (str (str/join \space (filter identity xs)) \newline))
         (flush))

(defn color-str [color & xs]
  (let [ansi-color #(format "\u001b[%sm"
                      (case % :reset  "0"  :black  "30" :red   "31"
                              :green  "32" :yellow "33" :blue  "34"
                              :purple "35" :cyan   "36" :white "37"
                              "0"))]
    (str (ansi-color color) (apply str xs) (ansi-color :reset))))

(def default-out (java.io.OutputStreamWriter. System/out))
(def default-err (java.io.PrintWriter.        System/err))
(defmacro with-default-outs
  "Evaluates body with Clojure's default *out* and *err* bindings."
  [& body] `(binding [*out* default-out
                      *err* default-err] ~@body))

(defn fmt-stacktrace "Default stacktrace formatter for use by appenders."
  [throwable & [separator stacktrace-fonts]]
  (when throwable
    (str separator
      (if-let [fonts stacktrace-fonts] ; nil (defaults), or a map
        (binding [aviso-ex/*fonts* fonts] (aviso-ex/format-exception throwable))
        (aviso-ex/format-exception throwable)))))

(comment (println (fmt-stacktrace (Exception. "foo") nil nil))
         (println (fmt-stacktrace (Exception. "foo") nil {})))

(def get-hostname
  ;; TODO Any way to keep future from affecting shutdown time,
  ;; Ref. http://goo.gl/5hx9oK?
  (encore/memoize* (encore/ms :mins 2)
    (fn []
      (->
       (future ; Android doesn't like this on the main thread
         (try (.. java.net.InetAddress getLocalHost getHostName)
              (catch java.net.UnknownHostException _
                "UnknownHost")))
       (deref 5000 "UnknownHost")))))

(def ^:private default-message-timestamp-pattern "14-Jul-07 16:42:11"
  "yy-MMM-dd HH:mm:ss")

(def ^:private default-message-pattern-fn
  "14-Jul-07 16:42:11 localhost INFO [my-app.foo.bar] - Hello world"
  (fn [{:keys [ns ; & Any other appender args
              ;; These are delays:
              timestamp_ hostname_ level-name_ args-str_ stacktrace_]}]
    (str @timestamp_ " " @hostname_ " " @level-name_ " "
      "[" ns "] - " @args-str_ @stacktrace_)))

(defn fmt-appender-args "Formats appender arguments as a message string."
  [fmt-fn ; `(apply <fmt-fn> args)`: format, print-str, pr-str, etc.
   {:as appender-args :keys [instant ns level throwable args]} &
   [{:as fmt-opts :keys [timestamp-pattern timestamp-locale no-fonts? pattern-fn]
     :or {timestamp-pattern default-message-timestamp-pattern
          timestamp-locale  nil
          pattern-fn        default-message-pattern-fn}}]]

  (when-not (empty? args)
    (pattern-fn
      (merge appender-args
        ;; Delays since user pattern may/not want any of these:
        {:hostname_   (delay (get-hostname))
         :timestamp_  (delay (.format (encore/simple-date-format timestamp-pattern
                                        {:locale timestamp-locale}) instant))
         :level-name_ (delay (-> level name str/upper-case))
         :args-str_   (delay (apply fmt-fn args)) ; `args` is non-empty
         :stacktrace_ (delay (fmt-stacktrace throwable "\n" (when no-fonts? {})))}))))

(comment
  (encore/qbench 1000
    (fmt-appender-args print-str
      {:instant (Date.) :ns *ns* :level :info :throwable nil
       :args ["Hello" "there"]})) ; ~14ms
  )

(defmacro sometimes
  "Executes body with probability e/o [0,1]. Useful for sampled logging."
  [probability & body]
  `(do (assert (<= 0 ~probability 1) "Probability: 0 <= p <= 1")
       (when (< (rand) ~probability) ~@body)))

;;;; Logging levels

(def level-compile-time
  "Constant, compile-time logging level determined by the `TIMBRE_LOG_LEVEL`
  environment variable. When set, overrules dynamically-configurable logging
  level as a performance optimization."
  (keyword (System/getenv "TIMBRE_LOG_LEVEL")))

(def ^:dynamic *level-dynamic* nil)
(defmacro with-log-level
  "Allows thread-local config logging level override. Useful for dev & testing."
  [level & body] `(binding [*level-dynamic* ~level] ~@body))

(def level-atom (atom :debug))
(defn set-level! [level] (reset! level-atom level))

(def levels-ordered [:trace :debug :info :warn :error :fatal :report])
(def levels-scored (zipmap levels-ordered (next (range))))

(defn- level-error? [level] (boolean (#{:error :fatal} level)))
(defn- level-checked-score [level]
  (or (when (nil? level) 0) ; < any valid level
      (levels-scored level)
      (throw (Exception. (format "Invalid logging level: %s" level)))))

(def ^:private levels-compare (memoize (fn [x y] (- (level-checked-score x)
                                                   (level-checked-score y)))))

(defn level-sufficient? "Precendence: compile-time > dynamic > config > atom."
  [level config] (<= 0 (levels-compare level (or level-compile-time
                                                 *level-dynamic*
                                                 (:current-level config)
                                                 @level-atom))))

;;;;

(def ^:private get-hostname
  (enc/memoize* 60000
    (fn []
      (->
       (future ; Android doesn't like this on the main thread
         (try (.. java.net.InetAddress getLocalHost getHostName)
              (catch java.net.UnknownHostException _
                "UnknownHost")))
       (deref 5000 "UnknownHost")))))

(def ^:private ensure-spit-dir-exists!
  (enc/memoize* 60000
    (fn [fname]
      (when-not (str/blank? fname)
        (let [file (File. ^String fname)
              dir  (.getParentFile (.getCanonicalFile file))]
          (when-not (.exists dir)
            (.mkdirs dir)))))))

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

  {;; :current-level :debug ; Prefer `level-atom`

   ;;; Control log filtering by namespace patterns (e.g. ["my-app.*"]).
   ;;; Useful for turning off logging in noisy libraries, etc.
   :ns-whitelist []
   :ns-blacklist []

   ;; Fns (applied right-to-left) to transform/filter appender fn args.
   ;; Useful for obfuscating credentials, pattern filtering, etc.
   :middleware []

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

(enc/defonce* config (atom example-config))
(defn set-config!   [ks val] (swap! config assoc-in ks val))
(defn merge-config! [& maps] (apply swap! config enc/merge-deep maps))

;;;; Appender-fn decoration

(defn default-args-hash-fn
  "Returns a hash id for given appender args such that
  (= (hash args-A) (hash args-B)) iff args A and B are \"the same\" by
  some reasonable-in-the-general-case definition for logging args. Useful for
  rate limiting, deduplicating appenders, etc."
  [{:keys [ns line args] :as apfn-args}]
  (str (or (some #(and (map? %) (:timbre/hash %)) args) ; Explicit hash given
           ;; [ns line] ; TODO Waiting on http://goo.gl/cVVAYA
           [ns args])))

(defn- wrap-appender-fn
  [config {:as appender apfn :fn
           :keys [async? rate-limit args-hash-fn appender-config]
           :or   {args-hash-fn default-args-hash-fn}}]
<<<<<<< HEAD
  (let [rate-limit (or rate-limit ; Backwards comp:
                       (if-let [x (:max-message-per-msecs appender)] [1 x]
                         (when-let [x (:limit-per-msecs   appender)] [1 x])))]

    (assert (or (nil? rate-limit) (vector? rate-limit)))

    (->> ; Wrapping applies per appender, bottom-to-top
     apfn

     ;; Custom appender-level fmt-output-opts
     ((fn [apfn] ; Compile-time:
        (if-not fmt-output-opts apfn ; Common case (no appender-level fmt opts)
          (fn [apfn-args] ; Runtime:
            ;; Replace default (juxt-level) output:
            (apfn (assoc apfn-args :output
                    ((:fmt-output-fn config) apfn-args fmt-output-opts)))))))

     ;; Rate limit support
     ((fn [apfn]
        ;; Compile-time:
        (if-not rate-limit apfn
          (let [[ncalls-limit window-ms] rate-limit
                limiter-any      (enc/rate-limiter ncalls-limit window-ms)
                ;; This is a little hand-wavy but it's a decent general
                ;; strategy and helps us from making this overly complex to
                ;; configure.
                limiter-specific (enc/rate-limiter (quot ncalls-limit 4)
                                                     window-ms)]
            (fn [{:keys [ns args] :as apfn-args}]
              ;; Runtime: (test smaller limit 1st):
              (when-not (or (limiter-specific (args-hash-fn apfn-args))
                            (limiter-any))
                (apfn apfn-args)))))))

     ;; Async (agent) support
     ((fn [apfn]
        ;; Compile-time:
        (if-not async? apfn
          (let [agent (agent nil :error-mode :continue)]
            (fn [apfn-args] ; Runtime:
              (send-off agent (fn [_] (apfn apfn-args)))))))))))

(defn- wrap-appender-juxt
  "Wraps compile-time appender juxt with additional runtime capabilities
  (incl. middleware) controlled by compile-time config. Like `wrap-appender-fn`
  but operates on the entire juxt at once."
  [config juxtfn]
  (->> ; Wrapping applies per juxt, bottom-to-top
   juxtfn

   ;; Post-middleware stuff
   ((fn [juxtfn]
      ;; Compile-time:
      (let [{ap-config :shared-appender-config
             :keys [timestamp-pattern timestamp-locale
                    prefix-fn fmt-output-fn]} config
             timestamp-fn
             (if-not timestamp-pattern (constantly nil)
               (fn [^Date dt]
                 (.format (enc/simple-date-format timestamp-pattern
                            {:locale timestamp-locale}) dt)))]

        (fn [juxtfn-args]
          ;; Runtime:
          (when-let [{:keys [instant msg-type args]} juxtfn-args]
            (let [juxtfn-args (if-not msg-type juxtfn-args ; tools.logging
                                (-> juxtfn-args
                                    (dissoc :msg-type)
                                    ;; TODO Consider a breaking change here to
                                    ;; swap assoc'd message with a delay, as
                                    ;; per http://goo.gl/7YVSfj:
                                    (assoc  :message
                                      (when-not (empty? args)
                                        (case msg-type
                                          :format    (apply format    args)
                                          :print-str (apply print-str args)
                                          :nil       nil)))))
                  juxtfn-args (assoc juxtfn-args :timestamp (timestamp-fn instant))
                  juxtfn-args (assoc juxtfn-args
                    ;; DEPRECATED, here for backwards comp:
                    :prefix (when-let [f prefix-fn]     (f juxtfn-args))
                    :output (when-let [f fmt-output-fn] (f juxtfn-args)))]
              (juxtfn juxtfn-args)))))))

   ;; Middleware transforms/filters support
   ((fn [juxtfn]
      ;; Compile-time:
      (if-let [middleware (seq (:middleware config))]
        (let [composed-middleware
              (apply comp (map (fn [mf] (fn [args] (when args (mf args))))
                               middleware))]
          (fn [juxtfn-args]
            ;; Runtime:
            (when-let [juxtfn-args (composed-middleware juxtfn-args)]
              (juxtfn juxtfn-args))))
        juxtfn)))

   ;; Pre-middleware stuff
=======
  (assert (or (nil? rate-limit) (vector? rate-limit)))
  (->> ; Wrapping applies per appender, bottom-to-top
    apfn

    ;; :ap-config
    ((fn [apfn]
       ;; Compile-time:
       (if-not appender-config apfn
         (let [merged-config (merge (:shared-appender-config config)
                                    appender-config)]
           (println "DEBUG! `merged-config`:" merged-config) ; TODO
           (fn [apfn-args]
             ;; Runtime:
             (apfn (assoc apfn-args :ap-config merged-config)))))))

    ;; Rate limits
    ((fn [apfn]
       ;; Compile-time:
       (if-not rate-limit apfn
         (let [[ncalls-limit window-ms] rate-limit
               limiter-any      (encore/rate-limiter ncalls-limit window-ms)
               ;; This is a little hand-wavy but it's a decent general
               ;; strategy and helps us from making this overly complex to
               ;; configure.
               limiter-specific (encore/rate-limiter (quot ncalls-limit 4)
                                  window-ms)]
           (fn [{:keys [ns args] :as apfn-args}]
             ;; Runtime:
             (when-not (or (limiter-specific (args-hash-fn apfn-args))
                           (limiter-any)) ; Test smaller limit 1st
               (apfn apfn-args)))))))

    ;; Async (agents)
    ((fn [apfn]
       ;; Compile-time:
       (if-not async? apfn
         (let [agent (agent nil :error-mode :continue)]
           (fn [apfn-args] ; Runtime:
             (send-off agent (fn [_] (apfn apfn-args))))))))))

(def ^:dynamic *context* "Thread-local dynamic logging context." {})
(defn- wrap-appender-juxt [config juxtfn]
  (->> ; Wrapping applies per juxt, bottom-to-top
   juxtfn

   ;; ;; Post-middleware stuff
   ;; ((fn [juxtfn]
   ;;    ;; Compile-time:
   ;;    (fn [juxtfn-args]
   ;;      ;; Runtime:
   ;;      (juxtfn juxtfn-args))))

   ;; Middleware (transforms/filters)
>>>>>>> fe51297... NB Experimental: major refactor (currently breaking, for potential Timbre v4)
   ((fn [juxtfn]
      ;; Compile-time:
      (let [middleware (:middleware config)]
        (if (empty? middleware) juxtfn
          (let [composed-middleware
                (apply comp (map (fn [mf] (fn [args] (when args (mf args))))
                              middleware))]
            (fn [juxtfn-args]
              ;; Runtime:
              (when-let [juxtfn-args (composed-middleware juxtfn-args)]
                (juxtfn juxtfn-args))))))))

   ;; ;; Pre-middleware stuff
   ;; ((fn [juxtfn]
   ;;    ;; Compile-time:
   ;;    (fn [juxtfn-args]
   ;;      ;; Runtime:
   ;;      (juxtfn juxtfn-args))))
   ))

;;;; Config compilation

(defn- relevant-appenders [appenders level]
  (->> appenders
       (filter #(let [{:keys [enabled? min-level]} (val %)]
                  (and enabled? (>= (levels-compare level min-level) 0))))
       (into {})))

(defn- ns-match? [ns match]
  (-> (str "^" (-> (str match) (.replace "." "\\.") (.replace "*" "(.*)")) "$")
      re-pattern (re-find (str ns)) boolean))

(def compile-config ; Used in macros, must be public
  "Implementation detail.
  Returns {:appenders-juxt {<level> <wrapped-juxt or nil>}
           :ns-filter      (fn relevant-ns? [ns])}."
  (memoize
   ;; Careful. The presence of fns means that inline config's won't correctly
   ;; be identified as samey. In practice not a major (?) problem since configs
   ;; will usually be assigned to a var for which we have proper identity.
   (fn [{:keys [appenders] :as config}]
     {:appenders-juxt
      (zipmap levels-ordered
         (->> levels-ordered
              (map (fn [l] (let [rel-aps (relevant-appenders appenders l)]
                             ;; Return nil if no relevant appenders
                             (when-let [ap-ids (keys rel-aps)]
                               (->> ap-ids
                                    (map #(wrap-appender-fn config (rel-aps %)))
                                    (apply juxt)
                                    (wrap-appender-juxt config))))))))
      :ns-filter
      (let [{:keys [ns-whitelist ns-blacklist]} config]
        (if (and (empty? ns-whitelist) (empty? ns-blacklist))
          (fn relevant-ns? [ns] true)
          (memoize
           (fn relevant-ns? [ns]
             (and (or (empty? ns-whitelist)
                      (some (partial ns-match? ns) ns-whitelist))
                  (or (empty? ns-blacklist)
                      (not-any? (partial ns-match? ns) ns-blacklist)))))))})))

(comment (compile-config example-config)
         (compile-config nil))

;;;; Logging macros

(def ^:dynamic *config-dynamic* nil)
(defmacro with-logging-config
  "Allows thread-local logging config override. Useful for dev & testing."
  [config & body] `(binding [*config-dynamic* ~config] ~@body))

(defn get-default-config [] (or *config-dynamic* @config))

(defmacro with-logging-context "Thread-local dynamic logging context."
  [context & body] `(binding [*context* ~context] ~@body))

(defn ns-unfiltered? [config ns] ((:ns-filter (compile-config config)) ns))

(defn logging-enabled? "For 3rd-party utils, etc."
  [level & [compile-time-ns]]
  (let [config' (get-default-config)]
    (and (level-sufficient? level config')
         (or (nil? compile-time-ns)
             (ns-unfiltered? config' compile-time-ns)))))

(defn send-to-appenders! "Implementation detail."
  [{:keys [;; Args provided by both Timbre, tools.logging:
           level base-appender-args log-vargs ns throwable message
           ;; Additional args provided by Timbre only:
           juxt-fn file line]}]
  (when-let [juxt-fn (or juxt-fn (get-in (compile-config (get-default-config))
                                         [:appenders-juxt level]))]
    (let [appender-args
          (conj (or base-appender-args {})
            {;;; Passed through
             :level     level
             :args      log-vargs ; String / 1-vec raw arg for tools.logging impl
             :ns        ns
             :throwable throwable
             :file      file ; Nil for tools.logging
             :line      line ; ''

             ;;; Generated
             :instant   (Date.)
             :error?    (level-error? level)

             ;;; Varies
             :message   message})]
      (juxt-fn appender-args)
      nil)))

(comment ; TODO
  (delay
    (fmt-appender-args ; TODO + maybe merge :ap-config for fmt opts?
      ;; Or just have in :shared-appender-config
      (case msg-type
        :format    format
        :print-str print-str
        :pr-str    pr-str))))

(comment ; TODO
  [fmt-fn ; `(apply <fmt-fn> args)`: format, print-str, pr-str, etc.
   {:as appender-args :keys [instant ns level throwable args]} &
   [{:as fmt-opts :keys [timestamp-pattern timestamp-locale no-fonts? pattern-fn]
     :or {timestamp-pattern default-message-timestamp-pattern
          timestamp-locale  nil
          pattern-fn        default-message-pattern-fn}}]])

(defn send-to-appenders! "Implementation detail."
  ([level base-appender-args log-vargs ns throwable message])


  

  )

(defn send-to-appenders! "Implementation detail."
  [;; Args provided by both Timbre, tools.logging:
   level base-appender-args log-vargs ns throwable message
   ;; Additional args provided by Timbre only:
   & [juxt-fn file line]]
  (when-let [juxt-fn (or juxt-fn (get-in (compile-config (get-default-config))
                                         [:appenders-juxt level]))]
    (juxt-fn
     (conj (or base-appender-args {})
       {;;; Generated
        :instant   (Date.)
        :error?    (level-error? level)

        ;;; Passed through
        :ns        ns
        :level     level
        :throwable throwable
        :message   message

        ;;; Passed through (no/limited tools.logging support)
        :file      file ; Nil for tools.logging impl
        :line      line ; ''
        :args      log-vargs ; String / 1-vec raw arg for tools.logging impl
        }))
    nil))

(defmacro get-compile-time-ns [] (str *ns*)) ; Nb need `str` to be readable
(comment (macroexpand '(get-compile-time-ns)))

(defmacro log* "Implementation detail."
  {:arglists '([base-appender-args fmt-fn level & log-args]
               [base-appender-args fmt-fn config level & log-args])}
  [base-appender-args fmt-fn & [s1 s2 :as sigs]]
  ;; Compile-time:
  (when (or (nil? level-compile-time)
            (let [level (cond (levels-scored s1) s1
                              (levels-scored s2) s2)]
              (or (nil? level) ; Also needs to be compile-time
                  (level-sufficient? level nil))))
    ;; Runtime:
    `(let [;;; Support [level & log-args], [config level & log-args] sigs:
           s1# ~s1
           default-config?# (levels-scored s1#)
           config# (if default-config?# (get-default-config) s1#)
           level#  (if default-config?# s1# ~s2)
           compile-time-ns# (get-compile-time-ns)]
       ;; (println "DEBUG: Runtime level check")
       (when (and (level-sufficient? level# config#)
                  (ns-unfiltered? config# compile-time-ns#))
         (when-let [juxt-fn# (get-in (compile-config config#)
                                     [:appenders-juxt level#])]
           (let [[x1# & xn# :as xs#] (if default-config?#
                                       (vector ~@(next  sigs))
                                       (vector ~@(nnext sigs)))
                 has-throwable?# (instance? Throwable x1#)
                 log-vargs# (vec (if has-throwable?# xn# xs#))]
             (send-to-appenders!
              level#
              ~base-appender-args
              log-vargs#
              compile-time-ns#
              (when has-throwable?# x1#)


              ;; TODO
              nil ; Timbre generates msg only after middleware


              juxt-fn#
              (let [file# ~*file*] (when (not= file# "NO_SOURCE_PATH") file#))
              ;; TODO Waiting on http://dev.clojure.org/jira/browse/CLJ-865:
              ~(:line (meta &form)))))))))

(defmacro log
  "Logs using print-style args. Takes optional logging config (defaults to
  `timbre/@config`.)"
  {:arglists '([level & message] [level throwable & message]
               [config level & message] [config level throwable & message])}
  [& sigs] `(log* {} :print-str ~@sigs))

(defmacro logf
  "Logs using format-style args. Takes optional logging config (defaults to
  `timbre/@config`.)"
  {:arglists '([level fmt & fmt-args] [level throwable fmt & fmt-args]
               [config level fmt & fmt-args] [config level throwable fmt & fmt-args])}
  [& sigs] `(log* {} :format ~@sigs))

(defmacro log-errors [& body] `(try ~@body (catch Throwable t# (error t#))))
(defmacro log-and-rethrow-errors [& body]
  `(try ~@body (catch Throwable t# (error t#) (throw t#))))

(defmacro logged-future [& body] `(future (log-errors ~@body)))

(comment (log-errors (/ 0))
         (log-and-rethrow-errors (/ 0))
         (logged-future (/ 0)))

(defmacro spy
  "Evaluates named expression and logs its result. Always returns the result.
  Defaults to :debug logging level and unevaluated expression as name."
  ([expr] `(spy :debug ~expr))
  ([level expr] `(spy ~level '~expr ~expr))
  ([level name expr]
     `(log-and-rethrow-errors
       (let [result# ~expr] (log ~level ~name result#) result#))))

(defmacro ^:private def-logger [level]
  (let [level-name (name level)]
    `(do
       (defmacro ~(symbol level-name)
         ~(str "Logs at " level " level using print-style args.")
         ~'{:arglists '([& message] [throwable & message])}
         [& sigs#] `(log ~~level ~@sigs#))

       (defmacro ~(symbol (str level-name "f"))
         ~(str "Logs at " level " level using format-style args.")
         ~'{:arglists '([fmt & fmt-args] [throwable fmt & fmt-args])}
         [& sigs#] `(logf ~~level ~@sigs#)))))

(defmacro ^:private def-loggers []
  `(do ~@(map (fn [level] `(def-logger ~level)) levels-ordered)))

(def-loggers) ; Actually define a logger for each logging level

(defn refer-timbre
  "Shorthand for:
  (require
    '[taoensso.timbre :as timbre
      :refer (log  trace  debug  info  warn  error  fatal  report
              logf tracef debugf infof warnf errorf fatalf reportf
              spy logged-future with-log-level with-logging-config
              sometimes)])
  (require
    '[taoensso.timbre.profiling :as profiling
      :refer (pspy pspy* profile defnp p p*)])"
  []
  (require
   '[taoensso.timbre :as timbre
     :refer (log  trace  debug  info  warn  error  fatal  report
             logf tracef debugf infof warnf errorf fatalf reportf
             spy logged-future with-log-level with-logging-config
             sometimes)])
  (require
   '[taoensso.timbre.profiling :as profiling
     :refer (pspy pspy* profile defnp p p*)]))

;;;; Deprecated

(defmacro with-err-as-out "DEPRECATED." [& body] `(binding [*err* *out*] ~@body))
(def stacktrace "DEPREACTED. Use `fmt-stacktrace` instead." fmt-stacktrace)

(defmacro logp "DEPRECATED: Use `log` instead."
  {:arglists '([level & message] [level throwable & message])}
  [& sigs] `(log ~@sigs)) ; Alias

(defmacro s "DEPRECATED: Use `spy` instead."
  {:arglists '([expr] [level expr] [level name expr])}
  [& args] `(spy ~@args))

(def red    "DEPRECATED: Use `color-str` instead." (partial color-str :red))
(def green  "DEPRECATED: Use `color-str` instead." (partial color-str :green))
(def yellow "DEPRECATED: Use `color-str` instead." (partial color-str :yellow))

;;;; Dev/tests

(comment
  (info)
  (info "a")
  (info "a" "b" "c")
  (info "a" (Exception. "b") "c")
  (info (Exception. "a") "b" "c")
  (log (or nil :info) "Booya")

  (info  "a%s" "b")
  (infof "a%s" "b")

  (info {} "a")
  (log {}             :info "a")
  (log example-config :info "a")

  (set-config! [:ns-blacklist] [])
  (set-config! [:ns-blacklist] ["taoensso.timbre*"])

  (info "foo" "bar")
  (trace (Thread/sleep 5000))
  (time (dotimes [n 10000] (trace "This won't log"))) ; Overhead 5ms->15ms
  (time (dotimes [n 10000] (when false)))
  (time (dotimes [n 5] (info "foo" "bar")))
  (spy :info (* 6 5 4 3 2 1))
  (spy :info :factorial6 (* 6 5 4 3 2 1))
  (info (Exception. "noes!") "bar")
  (spy (/ 4 0))

  (with-log-level :trace (trace "foo"))
  (with-log-level :debug (trace "foo"))

  ;; Middleware
  (info {:name "Robert Paulson" :password "Super secret"})
  (set-config! [:middleware] [])
  (set-config! [:middleware]
    [(fn [{:keys [hostname message args] :as ap-args}]
       (if (= hostname "filtered-host") nil ; Filter
         (assoc ap-args :args
           ;; Replace :password vals in any map args:
           (mapv (fn [arg] (if-not (map? arg) arg
                            (if-not (contains? arg :password) arg
                              (assoc arg :password "****"))))
                 args))))])

  ;; fmt-output-opts
  (-> (merge example-config
        {:appenders
         {:fmt-output-opts-test
          {:min-level :error :enabled? true
           :fmt-output-opts {:no-fonts? true}
           :fn (fn [{:keys [output]}] (str-println output))}}})
      (log :report (Exception. "Oh noes") "Hello"))

  ;; compile-time level (enabled log* debug println)
  (def level-compile-time :warn)
  (debug "hello")

  (log :info "hello")      ; Discarded at compile-time
  (log {} :info)           ; Discarded at compile-time
  (log (or :info) "hello") ; Discarded at runtime
  )
