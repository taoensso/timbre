(ns taoensso.timbre
  "Simple, flexible logging for Clojure/Script. No XML."
  {:author "Peter Taoussanis (@ptaoussanis)"}

  (:require
   [clojure.string  :as str]
   [taoensso.encore :as enc :refer [have have?]]
   [taoensso.timbre.appenders.core :as core-appenders]

   #?(:clj  [clj-commons.format.exceptions :as fmt-ex])
   #?(:cljs [goog.i18n.DateTimeFormat      :as dtf]))

  #?(:cljs (:require-macros [taoensso.timbre])))

(enc/assert-min-encore-version [3 85 0])

(comment
  (remove-ns 'taoensso.timbre)
  (test/run-tests))

;;;; Dynamic config

(declare ^:dynamic *config*)

(defn swap-config! [f & args]
  #?(:clj  (apply alter-var-root #'*config* f args)
     :cljs (set!                   *config* (apply f *config* args))))

(defn   set-config! [config] (swap-config! (fn [_old] config)))
(defn merge-config! [config] (swap-config! (fn [ old] (enc/nested-merge old config))))

#?(:clj (defmacro with-config        [config & body] `(binding [*config*                            ~config ] ~@body)))
#?(:clj (defmacro with-merged-config [config & body] `(binding [*config* (enc/nested-merge *config* ~config)] ~@body)))

;;;; Level filtering
;; Terminology note: we loosely distinguish between call/form and min levels,
;; though there's no motivation for a semantic (domain) difference between the
;; two as in Tufte.

(let [err "Invalid Timbre logging level: should be e/o #{:trace :debug :info :warn :error :fatal :report}"
      level->int
      #(case %
         :trace  0
         :debug  1
         :info   2
         :warn   3
         :error  4
         :fatal  5
         :report 6 ; High-level non-error type
         nil)]

  (defn- valid-level?     [x] (if (level->int x) true false))
  (defn- valid-level      [x] (if (level->int x) x (throw (ex-info err {:given x :type (type x)}))))
  (defn- valid-level->int [x] (or (level->int x)   (throw (ex-info err {:given x :type (type x)})))))

(let [valid-level->int valid-level->int]
  (defn #?(:clj level>= :cljs ^:boolean level>=)
    "Implementation detail."
    [x y] (>= ^long (valid-level->int x) ^long (valid-level->int y))))

(comment (enc/qb 1e6 (level>= :info :trace))) ; 50.0

;;;; Namespace filtering
;; Terminology note: we distinguish loosely between `ns-filter` (which may be a
;; fn or `ns-pattern`) and `ns-pattern` (subtype of `ns-filter`).

(let [fn?         fn?
      compile     (enc/fmemoize (fn [x] (enc/name-filter x)))
      conform?*   (enc/fmemoize (fn [x ns] ((compile x) ns)))
      ;; conform? (enc/fmemoize (fn [x ns] (if (fn? x) (x ns) ((compile x) ns))))
      conform?
      (fn [ns-filter ns]
        (if (fn? ns-filter)
          (ns-filter           ns) ; Intentionally uncached, can be handy
          (conform?* ns-filter ns)))]

  (defn- #?(:clj may-log-ns? :cljs ^boolean may-log-ns?)
    "Implementation detail."
    [ns-filter ns] (if (conform? ns-filter ns) true false))

  (def ^:private ns->?min-level
    "[[<ns-pattern> <min-level>] ... [\"*\" <default-min-level>]], ns -> ?min-level"
    (enc/fmemoize
      (fn [specs ns]
        (enc/rsome
          (fn [[ns-pattern min-level]]
            (when (conform?* ns-pattern ns)
              (valid-level min-level)))
          specs)))))

(comment
  (enc/qb 1e6 ; [75.68 171.17 102.96]
    (may-log-ns? "*" "taoensso.timbre")
    (ns->?min-level [[#{"taoensso.*" "foo.bar"} :info] ["*" :debug]] "foo.bar")
    (ns->?min-level [["ns.1" :info] ["ns.2" :debug]] "ns.2")))

;;;; Combo filtering

(let [valid-level    valid-level
      ns->?min-level ns->?min-level]

  (defn- get-min-level [default x ns]
    (valid-level
      (or
        (if (vector? x) (ns->?min-level x ns) x)
        default))))

(comment
  (get-min-level :report [["foo" :info]] *ns*)
  (let [ns *ns*]
    (enc/qb 1e6 ; [53.48 99.33]
      (get-min-level :report       :info   ns)
      (get-min-level :report [["*" :info]] ns))))

(let [;; Legacy API unfortunately treated empty colls as allow-all
      leglist (fn [x] (when x (if (#{[] #{}} x) nil x)))]
  (defn- legacy-ns-filter [ns-whitelist ns-blacklist]
    (let [ns-whitelist (leglist ns-whitelist)
          ns-blacklist (leglist ns-blacklist)]
      (when (or ns-whitelist ns-blacklist)
        {:allow ns-whitelist :deny ns-blacklist}))))

(comment (legacy-ns-filter [] ["foo"]))

(let [level>=          level>=
      may-log-ns?      may-log-ns?
      get-min-level    get-min-level
      legacy-ns-filter legacy-ns-filter]

  (defn #?(:clj may-log? :cljs ^:boolean may-log?)
    "Implementation detail.
    Returns true iff level and ns are runtime unfiltered."
    ([                  level                ] (may-log? :trace level nil     nil))
    ([                  level ?ns-str        ] (may-log? :trace level ?ns-str nil))
    ([                  level ?ns-str ?config] (may-log? :trace level ?ns-str ?config))
    ([default-min-level level ?ns-str ?config]
     (let [config (or ?config *config*) ; NB may also be appender map
           min-level
           (get-min-level default-min-level
             (or
               (get config :min-level)
               (get config :level) ; Legacy
               )
             ?ns-str)]

       (if (level>= level min-level)
         (if-let [ns-filter
                  (or
                    (get config :ns-filter)
                    (legacy-ns-filter ; Legacy
                      (get config :ns-whitelist)
                      (get config :ns-blacklist)))]

           (if (may-log-ns? ns-filter ?ns-str) true false)
           true)
         false)))))

(comment (enc/qb 1e6 (may-log? :info))) ; 120.4

;;;; Compile-time filtering

#?(:clj (defonce ^:private compile-time-config_ (atom {})))
#?(:clj
   (defonce ^:private compile-time-min-level
     (let [level
           (enc/get-env {:as :edn}
             [:taoensso.timbre.min-level<.edn>
              :timbre-level<.edn>     ; Legacy
              :timbre-log-level<.edn> ; Legacy
              ])

           level (if (string? level) (keyword level) level) ; Legacy
           present? (some?        level)
           valid?   (valid-level? level)]

       (swap! compile-time-config_ assoc :min-level
         (when present?
           (if valid?
             level
             [:timbre/invalid-min-level
              {:value level :type (type level)}])))

       (when valid?
         ;; (println (str "Compile-time (elision) Timbre min-level: " level))
         level))))

#?(:clj
   (defonce ^:private compile-time-ns-filter
     (let [ns-pattern
           (or
             (enc/get-env {:as :edn}
               [:taoensso.timbre.ns-pattern<.edn>
                :timbre-ns-pattern<.edn> ; Legacy
                ])

             (legacy-ns-filter ; Legacy
               (enc/get-env {:as :edn} :timbre-ns-whitelist<.edn>)
               (enc/get-env {:as :edn} :timbre-ns-blacklist<.edn>)))

           ns-pattern ; Support legacy :whitelist, :blacklist
           (if (map? ns-pattern)
             {:allow (or (:allow ns-pattern) (:whitelist ns-pattern))
              :deny  (or (:deny  ns-pattern) (:blacklist ns-pattern))}
             ns-pattern)

           present?   (some? ns-pattern)
           ns-pattern (or    ns-pattern "*")]

       (swap! compile-time-config_ assoc :ns-pattern ns-pattern)
       ;; (when present?
       ;;   (println (str "Compile-time (elision) Timbre ns-pattern: " ns-pattern)))

       ns-pattern)))

#?(:clj
   (defn -elide?
     "Returns true iff level or ns are compile-time filtered.
     Called only at macro-expansiom time."
     [level-form ns-str-form]
     (not
       (and
         (or ; Level okay
           (nil? compile-time-min-level)
           (not (valid-level? level-form)) ; Not a compile-time level const
           (level>= level-form compile-time-min-level))

         (or ; Namespace okay
           (not (string? ns-str-form)) ; Not a compile-time ns-str const
           (may-log-ns? compile-time-ns-filter ns-str-form))))))

;;;; Namespace min-level utils

(defn set-min-level [config min-level] (assoc config :min-level (valid-level min-level)))
(defn set-min-level!       [min-level] (swap-config! (fn [old] (set-min-level old min-level))))
#?(:clj
   (defmacro with-min-level [min-level & body]
     `(binding [*config* (set-min-level *config* ~min-level)] ~@body)))

(defn set-ns-min-level
  "Returns given Timbre `config` with its `:min-level` modified so that
  the given namespace has the specified minimum logging level.

  When no namespace is provided, `*ns*` will be used.
  When `?min-level` is nil, any minimum level specifications for the
  *exact* given namespace will be removed.

  See `*config*` docstring for more about `:min-level`.
  See also `set-min-level!` for a util to directly modify `*config*`."

  #?(:clj ([config    ?min-level] (set-ns-min-level config *ns* ?min-level))) ; No *ns* at Cljs runtime
  (        [config ns ?min-level]
   (let [ns (str (have ns))
         min-level* ; [[<ns-pattern> <min-level>] ...]
         (let [x (get config :min-level)]
           (if (vector? x)
             x
             [["*" (valid-level x)]])) ; :info -> [["*" :info]]

         min-level*
         (reduce ; Remove any pre-existing [<ns> _] or [#{<ns>} _] entries
           (fn [acc [ns-pattern _pattern-min-level :as entry]]
             (if-let [exact-match? (or (= ns-pattern ns) (= ns-pattern #{ns}))]
               (do   acc)       ; Remove entry
               (conj acc entry) ; Retain entry
               ))

           (if-let [new-min-level ?min-level]
             [[ns (valid-level new-min-level)]] ; Insert new entry at head
             [])

           min-level*)

         min-level*
         (if-let [simplified ; [["*" :info]] -> :info
                  (when (= (count min-level*) 1)
                    (let [[[ns-pattern level]] min-level*]
                      (when (= ns-pattern "*") level)))]
           simplified
           (not-empty min-level*))]

     (assoc config :min-level min-level*))))

(comment :see-tests)

(defmacro set-ns-min-level!
  "Like `set-ns-min-level` but directly modifies `*config*`.

  Can conveniently set the minimum log level for the current ns:
    (set-ns-min-level! :info) => Sets min-level for current *ns*

  See `set-ns-min-level` for details."

  ;; Macro to support compile-time Cljs *ns*
  ([   ?min-level] `(set-ns-min-level! ~(str *ns*) ~?min-level))
  ([ns ?min-level] `(swap-config! (fn [config#] (set-ns-min-level config# ~(str ns) ~?min-level)))))

;;;; Utils

#?(:clj (defmacro get-env [] `(enc/get-locals)))
(comment ((fn foo [x y] (get-env)) 5 10))

#?(:clj (defonce ^:private get-agent        (enc/fmemoize (fn [appender-id      ] (agent nil :error-mode :continue)))))
(do     (defonce ^:private get-rate-limiter (enc/fmemoize (fn [appender-id specs] (enc/rate-limiter specs)))))

(comment
  (get-agent :my-appender)
  (def rf (get-rate-limiter :my-appender [[10 5000]])))

(defn- get-timestamp [timestamp-opts instant]
  #?(:clj
     (let [{:keys [pattern locale timezone]} timestamp-opts]
       ;; iso8601 example: 2020-09-14T08:31:17.040Z (UTC)
       (.format ^java.text.SimpleDateFormat
         (enc/simple-date-format* pattern locale timezone)
         instant))

     :cljs
     (let [{:keys [pattern]} timestamp-opts]
       (if (enc/identical-kw? pattern :iso8601)
         (.toISOString (js/Date. instant)) ; e.g. 2020-09-14T08:29:49.711Z (UTC)
         ;; Pattern can also be be `goog.i18n.DateTimeFormat.Format`, etc.
         (.format
           (goog.i18n.DateTimeFormat. pattern)
           instant)))))

(comment (get-timestamp default-timestamp-opts (enc/now-udt)))

#?(:clj (defn ^:no-doc get-?hostname "Returns uncached local hostname string, or nil." [] (enc/get-hostname nil)))
#?(:clj (defn          get-hostname  "Returns cached local hostname string."   ^String [] (enc/get-hostname (enc/msecs :mins 1) 5000 "UnknownHost")))

#?(:clj
   (defn ansi-color [color]
     (str "\u001b["
       (case color
         :reset  "0"  :black  "30" :red   "31"
         :green  "32" :yellow "33" :blue  "34"
         :purple "35" :cyan   "36" :white "37"
         "0")
       "m")))

#?(:clj
   (let [ansi-reset (ansi-color :reset)]
     (defn color-str
       ([color           ] (str (ansi-color color)                      ansi-reset)) ; Back compatibility
       ([color x         ] (str (ansi-color color) x                    ansi-reset))
       ([color x y       ] (str (ansi-color color) x y                  ansi-reset))
       ([color x y & more] (str (ansi-color color) x y (apply str more) ansi-reset)))))

#?(:clj (def default-out (java.io.OutputStreamWriter. System/out)))
#?(:clj (def default-err (java.io.PrintWriter.        System/err)))
#?(:clj
   (defmacro with-default-outs [& body]
     `(binding [*out* default-out, *err* default-err] ~@body)))

#?(:clj
   (defmacro sometimes
     "Handy for sampled logging, etc."
     [probability & body]
     (if (enc/const-form? probability)
       `(when (< (Math/random) ~(enc/as-pnum! probability)) ~@body)
       `(when (< (Math/random) (double       ~probability)) ~@body))))

;;;; Context

(def ^:dynamic *context* "General-purpose dynamic logging context" nil)

#?(:clj
   (defmacro with-context
     "Executes body so that given arbitrary data will be passed (as `:context`)
     to appenders for any enclosed logging calls.

     (with-context
       {:user-name \"Stu\"} ; Will be incl. in data dispatched to appenders
       (info \"User request\"))

     See also `with-context+`."

     [context & body] `(binding [*context* ~context] ~@body)))

#?(:clj
   (defmacro with-context+
     "Like `with-context`, but merges given context into current context."
     [context & body]
     `(binding [*context* (conj (or *context* {}) ~context)]
        ~@body)))

(comment (with-context+ {:foo1 :bar1} (with-context+ {:foo2 :bar2} *context*)))

;;;; Logging core

(defn- parse-vargs
  "vargs -> [?err ?meta ?msg-fmt api-vargs]"
  [?err msg-type vargs]
  (let [auto-error? (enc/identical-kw? ?err :auto)
        fmt-msg?    (enc/identical-kw? msg-type :f)
        [v0] vargs]

    (if (and auto-error? (enc/error? v0))
      (let [?err     v0
            ?meta    nil
            vargs    (enc/vrest vargs)
            ?msg-fmt (if fmt-msg? (let [[v0] vargs] v0) nil)
            vargs    (if fmt-msg? (enc/vrest vargs) vargs)]

        [?err ?meta ?msg-fmt vargs])

      (let [?meta    (if (and (map? v0) (:meta (meta v0))) v0 nil)
            ?err     (or (:err ?meta) (if auto-error? nil ?err))
            ?meta    (dissoc ?meta :err)
            vargs    (if ?meta    (enc/vrest vargs) vargs)
            ?msg-fmt (if fmt-msg? (let [[v0] vargs] v0) nil)
            vargs    (if fmt-msg? (enc/vrest vargs) vargs)]

        [?err ?meta ?msg-fmt vargs]))))

(comment
  (let [ex (Exception. "ex")]
    (enc/qb 1e4
      (parse-vargs :auto :f ["fmt" :a :b :c])
      (parse-vargs :auto :p [ex    :a :b :c])
      (parse-vargs :auto :p [^:meta {:foo :bar} :a :b :c])
      (parse-vargs :auto :p [       {:foo :bar} :a :b :c])
      (parse-vargs :auto :p [ex])
      (parse-vargs :auto :p [^:meta {:err ex}   :a :b :c])))
  ;; [1.49 1.34 2.99 0.83 0.89 2.98]
  (infof                                 "Hi %s" "steve")
  (infof ^:meta {:hash :bar}             "Hi %s" "steve")
  (infof ^:meta {:err (Exception. "ex")} "Hi %s" "steve"))

(defn- protected-fn [error-msg f]
  (fn [data]
    (enc/catching (f data) t
      (let [{:keys [level ?ns-str ?file ?line ?column]} data]
        (throw
          (ex-info error-msg
            {:output-fn f
             :level level
             :data  data
             :loc
             {:ns     ?ns-str
              :file   ?file
              :line   ?line
              ;; :column ?column
              }}
            t))))))

(comment ((protected-fn "Whoops" (fn [data] (/ 1 0))) {}))

(declare
  default-timestamp-opts
  default-output-fn
  default-output-msg-fn
  default-output-error-fn)

(defn -log! "Core low-level log fn. Implementation detail!"

  ;; Back compatible arities for convenience of AOT tools,
  ;; Ref. https://github.com/fzakaria/slf4j-timbre/issues/20
  ([config level ?ns-str ?file ?line msg-type ?err vargs_ ?base-data                    ] (-log! config level ?ns-str ?file ?line nil msg-type ?err vargs_ ?base-data nil         false))
  ([config level ?ns-str ?file ?line msg-type ?err vargs_ ?base-data callsite-id        ] (-log! config level ?ns-str ?file ?line nil msg-type ?err vargs_ ?base-data callsite-id false))
  ([config level ?ns-str ?file ?line msg-type ?err vargs_ ?base-data callsite-id spying?] (-log! config level ?ns-str ?file ?line nil msg-type ?err vargs_ ?base-data callsite-id spying?))

  ([config level ?ns-str ?file ?line ?column msg-type ?err vargs_ ?base-data callsite-id spying?]
   (when (may-log? :trace level ?ns-str config)
     (let [instant (enc/now-dt*)
           context *context*
           vargs   @vargs_

           [?err ?meta ?msg-fmt vargs]
           (parse-vargs ?err msg-type vargs)

           data ; Pre-middleware
           (conj
             (or ?base-data {})
             {:instant instant
              :level   level
              :context context
              :config  config  ; Entire config!
              :?ns-str ?ns-str
              :?file   ?file
              :?line   ?line
              :?column ?column
              #?(:clj :hostname_) #?(:clj (delay (get-hostname)))
              :error-level? (contains? #{:error :fatal} level)
              :?err     ?err
              :msg-type msg-type ; Undocumented
              :?msg-fmt ?msg-fmt ; Undocumented
              :?meta    ?meta
              :vargs    vargs
              :spying?  spying?})

           ?data ; Post middleware
           (reduce ; Apply middleware: data->?data
             (fn [acc mf]
               (let [result (mf acc)]
                 (if (nil? result)
                   (reduced nil)
                   result)))
             data
             (get config :middleware))]

       (when-let [data ?data] ; Not filtered by middleware
         (let [{:keys [vargs]} data
               data
               (enc/assoc-nx data
                 :msg_ (delay ((protected-fn "Timbre error when calling (msg-fn <data>)"
                                 default-output-msg-fn) data)) ; Deprecated

                 :hash_ ; Identify unique logging "calls" for rate limiting, etc.
                 (delay
                   (hash
                     (enc/cond
                       ;; Partial control, callsite-id only useful for direct macro calls
                       :if-let [id (get ?meta :id)] [id callsite-id level]
                       :if-let [id (get ?meta :id!)] id ; Complete control, undocumented

                       ;; Deprecated, was never officially documented
                       :if-let [h (get ?meta :hash)] [h     callsite-id ?msg-fmt level]
                       :else                         [vargs callsite-id ?msg-fmt level] ; Auto/default
                       ))))

               ;;; Optimization: try share timestamp & output between appenders
               get-timestamp-delay
               (let [get-shared-delay (enc/fmemoize (fn [opts] (delay (get-timestamp opts (get data :instant)))))
                     base-opts_ (delay (conj default-timestamp-opts (get config :timestamp-opts)))]

                 (fn [?appender-opts] ; Return timestamp_ delay
                   (if (or
                         (nil? ?appender-opts)
                         (enc/identical-kw? ?appender-opts :inherit) ; Back compatibility
                         )

                     (get-shared-delay       @base-opts_)
                     (get-shared-delay (conj @base-opts_ ?appender-opts)))))

               get-output-fn
               (let [base-fn (enc/fmemoize (get config :output-fn default-output-fn))]
                 (fn [?appender-fn] ; Return output-fn
                   (protected-fn "Timbre error when calling (output-fn <data>)"
                     (if (or
                           (nil? ?appender-fn)
                           (enc/identical-kw? ?appender-fn :inherit) ; Back compatibility
                           )

                       base-fn
                       ?appender-fn))))

               base-output-opts (get config :output-opts)]

           (reduce-kv
             (fn [_ id appender]
               (when (and (get appender :enabled?) (may-log? :trace level ?ns-str appender))

                 (let [rate-limit-specs (get appender :rate-limit)
                       rate-limit-okay?
                       (or
                         (empty? rate-limit-specs)
                         (let [rl-fn (get-rate-limiter id rate-limit-specs)]
                           (not (rl-fn (force (get data :hash_))))))]

                   (when rate-limit-okay?
                     (let [{:keys [async?] apfn :fn} appender

                           timestamp_  (get-timestamp-delay (get appender :timestamp-opts))
                           output-fn   (get-output-fn       (get appender :output-fn))
                           output-opts (or (get appender :output-opts) base-output-opts)
                           output_
                           (delay
                             (output-fn
                               (assoc data
                                 :timestamp_  timestamp_
                                 :output-opts output-opts)))

                           data
                           (conj data
                             {:appender-id id
                              :appender    appender
                              :output-opts output-opts
                              :output-fn   output-fn
                              :output_     output_
                              :timestamp_  timestamp_})

                           ?data ; Final data prep before going to appender
                           (if-let [mfn (get appender :middleware-fn)]
                             (mfn data) ; Deprecated, undocumented
                             data)]

                       (when-let [data ?data] ; Not filtered by middleware

                         ;; NB Unless `async?`, we currently allow appenders
                         ;; to throw since it's not particularly obvious
                         ;; how/where we should report problems. Throwing
                         ;; early seems preferable to just silently dropping
                         ;; errors. In effect, we currently require appenders
                         ;;  to take responsibility over appropriate trapping.

                         #?(:cljs (apfn data)
                            :clj
                            (if async?
                              (send-off (get-agent id) (fn [_] (apfn data) nil))
                              (apfn data)))))))))
             nil
             (get config :appenders))))))
   nil))

(comment
  (-log! *config* :info nil nil nil nil :p :auto
    (delay [(do (println "hi") :x) :y]) nil "callsite-id" false))

(enc/defonce ^:private callsite-counter
  "Simple counter, used to uniquely identify each log macro expansion."
  (enc/counter))

#?(:clj (enc/defalias enc/keep-callsite))
#?(:clj
   (defmacro log! ; Public wrapper around `-log!`
     "Core low-level log macro. Useful for tooling/library authors, etc.

       * `level`    - must eval to a valid logging level
       * `msg-type` - must eval to e/o #{:p :f nil}
       * `args`     - arguments seq (ideally vec) for logging call
       * `opts`     - ks e/o #{:config ?err ?base-data spying?
                               :?ns-str :?file :?line :?column}

     Supports compile-time elision when compile-time const vals
     provided for `level` and/or `?ns-str`.

     Logging wrapper examples:

       (defn     log-wrapper-fn    [& args]                        (timbre/log! :info :p  args))
       (defmacro log-wrapper-macro [& args] (timbre/keep-callsite `(timbre/log! :info :p ~args)))"

     ([{:as   opts
        :keys [loc level msg-type args vargs
               config ?err ?base-data spying?]
        :or
        {config `*config*
         ?err   :auto}}]

      (have [:or nil? sequential? symbol?] args)
      (let [callsite-id (callsite-counter)
            loc (or loc (enc/get-source &form &env))
            {:keys [ns file line column]} loc

            ns     (or (get opts :?ns-str) ns)
            file   (or (get opts :?file)   file)
            line   (or (get opts :?line)   line)
            column (or (get opts :?column) column)

            elide? (and (enc/const-forms? level ns) (-elide? level ns))]

        (when-not elide?
          (let [vargs-form
                (or vargs
                  (if (symbol? args)
                    `(enc/ensure-vec ~args)
                    `[              ~@args]))]

            ;; Note pre-resolved expansion
            `(taoensso.timbre/-log! ~config ~level ~ns ~file ~line ~column ~msg-type ~?err
               (delay ~vargs-form) ~?base-data ~callsite-id ~spying?)))))

     ([level msg-type args & [opts]]
      (let [loc  (enc/get-source &form &env)
            opts (assoc (conj {:loc loc} opts)
                   :level level, :msg-type msg-type, :args args)]
        `(log! ~opts)))))

(macroexpand '(log! :info :p ["foo"]   {:loc {:ns "foo"}}))

(comment
  (do           (log! :info :p ["foo"]))
  (macroexpand '(log! :info :p ["foo" x]))
  (macroexpand '(log! :info :p ["foo" x] {:?line 42}))
  (macroexpand '(log! :info :p args      {:?line 42}))

  (defn wrapper-fn [& args] (log! :info :p args))
  (wrapper-fn "a" "b"))

;;;; Benchmarking

(comment
  (set-min-level! :debug)
  (may-log? :trace)
  (with-min-level :trace (log? :trace))
  (enc/qb 1e4
    (may-log? :trace)
    (may-log? :trace "foo")
    (tracef "foo")
    (when false "foo"))
  ;; [0.64 0.66 1.52 0.26]

  (defmacro with-sole-appender [appender & body]
    `(with-config (assoc *config* :appenders {:appender ~appender}) ~@body))

  (with-sole-appender {:enabled? true :fn (fn [data] nil)}
    (enc/qb 1e4 (info "foo"))) ; ~54.86 ; Time to delays ready

  (with-sole-appender {:enabled? true :fn (fn [data] (force (:output_ data)))}
    (enc/qb 1e4 (info "foo"))) ; ~157.95 ; Time to output ready
  )

;;;; Common public API
;; Note extra complications due to CLJ-865

#?(:clj
   (do
     ;;; Log using print-style args
     (defmacro log*  [config level & args] `(log! ~level  :p ~args ~{:loc (enc/get-source &form &env), :config config}))
     (defmacro log          [level & args] `(log! ~level  :p ~args ~{:loc (enc/get-source &form &env)}))
     (defmacro trace              [& args] `(log! :trace  :p ~args ~{:loc (enc/get-source &form &env)}))
     (defmacro debug              [& args] `(log! :debug  :p ~args ~{:loc (enc/get-source &form &env)}))
     (defmacro info               [& args] `(log! :info   :p ~args ~{:loc (enc/get-source &form &env)}))
     (defmacro warn               [& args] `(log! :warn   :p ~args ~{:loc (enc/get-source &form &env)}))
     (defmacro error              [& args] `(log! :error  :p ~args ~{:loc (enc/get-source &form &env)}))
     (defmacro fatal              [& args] `(log! :fatal  :p ~args ~{:loc (enc/get-source &form &env)}))
     (defmacro report             [& args] `(log! :report :p ~args ~{:loc (enc/get-source &form &env)}))

     ;;; Log using format-style args
     (defmacro logf* [config level & args] `(log! ~level  :f ~args ~{:loc (enc/get-source &form &env), :config config}))
     (defmacro logf         [level & args] `(log! ~level  :f ~args ~{:loc (enc/get-source &form &env)}))
     (defmacro tracef             [& args] `(log! :trace  :f ~args ~{:loc (enc/get-source &form &env)}))
     (defmacro debugf             [& args] `(log! :debug  :f ~args ~{:loc (enc/get-source &form &env)}))
     (defmacro infof              [& args] `(log! :info   :f ~args ~{:loc (enc/get-source &form &env)}))
     (defmacro warnf              [& args] `(log! :warn   :f ~args ~{:loc (enc/get-source &form &env)}))
     (defmacro errorf             [& args] `(log! :error  :f ~args ~{:loc (enc/get-source &form &env)}))
     (defmacro fatalf             [& args] `(log! :fatal  :f ~args ~{:loc (enc/get-source &form &env)}))
     (defmacro reportf            [& args] `(log! :report :f ~args ~{:loc (enc/get-source &form &env)}))))

(comment
  (macroexpand '(infof "hello %s" "world"))
  (macroexpand '(infof (Exception.) "hello %s" "world"))
  (macroexpand '(infof (Exception.))))

;;;;

#?(:clj
   (do
     (defmacro log-errors             [& body]         `(enc/catching (do ~@body) t# (log! :error nil nil {:loc ~(enc/get-source &form &env), :?err t#})))
     (defmacro logged-future          [& body] `(future (enc/catching (do ~@body) t# (log! :error nil nil {:loc ~(enc/get-source &form &env), :?err t#}))))
     (defmacro log-and-rethrow-errors [& body]
       `(enc/catching (do ~@body) t#
          (do
            (log! :error nil nil {:loc ~(enc/get-source &form &env), :?err t#})
            (throw t#))))))

(comment
  (macroexpand '(log-errors             (/ 0)))
  (macroexpand '(logged-future          (/ 0)))
  (macroexpand '(log-and-rethrow-errors (/ 0))))

#?(:clj
   (defmacro -spy [loc config level name form]
     `(enc/catching
        (let [result# ~form]
          (log! ~level :p [~name "=>" result#] ~{:loc loc, :config config, :spying? true})
          result#)
        t#
        (do
          (log! :error nil nil {:loc ~loc, :?err t#, :spying? true})
          (throw t#)))))

#?(:clj
   (defmacro spy
     "Evaluates named form and logs its result. Always returns the result.
     Defaults to `:debug` logging level, and unevaluated form as name:
       (spy (+ 3 2)) => (timbre/debug '(+ 3 2) \"=>\" 5)"
     ([                  form] `(-spy ~(enc/get-source &form &env) *config* :debug '~form ~form))
     ([       level      form] `(-spy ~(enc/get-source &form &env) *config* ~level '~form ~form))
     ([       level name form] `(-spy ~(enc/get-source &form &env) *config* ~level  ~name ~form))
     ([config level name form] `(-spy ~(enc/get-source &form &env) ~config  ~level  ~name ~form))))

(comment
  (macroexpand '(spy (+ 3 2)))
  (do           (spy (+ 3 2)))
  (with-config
    (assoc example-config :appenders {:default {:enabled? true :fn (fn [m] (println (:spying? m)))}})
    (info (+ 3 2))
    (spy  (+ 3 2))))

#?(:clj
   (defn handle-uncaught-jvm-exceptions!
     "Sets JVM-global DefaultUncaughtExceptionHandler."
     [& [handler]]
     (let [handler
           (or handler
             (fn [throwable ^Thread thread]
               (error throwable "Uncaught exception on thread:"
                 (.getName thread))))]

       (Thread/setDefaultUncaughtExceptionHandler
         (reify Thread$UncaughtExceptionHandler
           (uncaughtException [this thread throwable] (handler throwable thread)))))))

(comment (handle-uncaught-jvm-exceptions!))

;;;; Default fns

(defn default-output-fn
  "Default (fn [data]) -> final output string, used to produce
  final formatted output_ string from final log data.

  Options (included as `:output-opts` in data sent to fns below):

    :error-fn ; When present and (:?err data) present,
              ; (error-fn data) will be called to generate output
              ; (e.g. a stacktrace) for the error.
              ;
              ; Default value: `default-output-error-fn`.
              ; Use `nil` value to exclude error output.

    :msg-fn   ; When present, (msg-fn data) will be called to
              ; generate a message from `vargs` (vector of raw
              ; logging arguments).
              ;
              ; Default value: `default-output-msg-fn`.
              ; Use `nil` value to exclude message output."

  ([base-output-opts data] ; Back compatibility (before :output-opts)
   (let [data
         (if (empty? base-output-opts)
           data
           (assoc data :output-opts
             (conj
               base-output-opts ; Opts from partial
               (get data :output-opts) ; Opts from data override
               )))]

     (default-output-fn data)))

  ([data]
   (let [{:keys [level ?err #_vargs #_msg_ ?ns-str ?file hostname_
                 timestamp_ ?line #_?column output-opts]}
         data]

     (str
       (when-let [ts (force timestamp_)] (str ts " "))
       #?(:clj (force hostname_))  #?(:clj " ")
       (str/upper-case (name level))  " "
       "[" (or ?ns-str ?file "?") ":" (or ?line "?") "] - "

       (when-let [msg-fn (get output-opts :msg-fn default-output-msg-fn)]
         (msg-fn data))

       (when-let [err ?err]
         (when-let [ef (get output-opts :error-fn default-output-error-fn)]
           (when-not   (get output-opts :no-stacktrace?) ; Back compatibility
             (enc/catching
               (str enc/newline (ef data)) _
               (str
                 enc/newline
                 "[TIMBRE WARNING]: `error-fn` failed, falling back to `pr-str`:"
                 enc/newline
                 (enc/catching (pr-str err) _ "<pr-str failed>"))))))))))

(defn- default-arg->str-fn [x]
  (enc/cond
    (nil?    x) "nil"
    (string? x) x
    :else
    #?(:clj (with-out-str (pr x))
       :cljs          (pr-str x))))

(defn- legacy-arg->str-fn [x]
  (enc/cond
    (nil?    x) "nil"
    (record? x) (pr-str x)
    :else               x))

(defn- str-join
  ([            xs] (str-join default-arg->str-fn       xs))
  ([arg->str-fn xs] (enc/str-join " " (map arg->str-fn) xs)))

(comment
  (defrecord MyRec [x])
  (str-join ["foo" (MyRec. "foo")]))

(defn default-output-msg-fn
  "(fn [data]) -> string, used by `default-output-fn` to generate output
  for `:vargs` value (vector of raw logging arguments) in log data."
  [{:keys [msg-type ?msg-fmt vargs output-opts] :as data}]
  (let [{:keys [arg->str-fn] ; Undocumented
         :or   {arg->str-fn default-arg->str-fn}}
        output-opts]

    (case msg-type
      nil ""
      :p  (str-join arg->str-fn vargs)
      :f
      (if (string?   ?msg-fmt)
        (enc/format* ?msg-fmt vargs) ; Don't use arg->str-fn, would prevent custom formatting
        (throw
          (ex-info "Timbre format-style logging call without a format pattern string"
            {:?msg-fmt ?msg-fmt :type (type ?msg-fmt) :vargs vargs}))))))

(comment
  (default-output-msg-fn
    {:msg-type :p :vargs ["a" "b"]
     :output-opts {:arg->str-fn (fn [_] "x")}}))

#?(:clj
   (do
     ;; Pretty v1 used string fonts, v2 uses unrelated keywords
     (def ^:private invalid-stacktrace-fonts-msg "Invalid Timbre stacktrace fonts (see `org.clj-commons/pretty` docs)")
     (def ^:private   valid-stacktrace-fonts? (fn [st-fonts] (not (enc/rsome-kv (fn [k v] (string? v)) st-fonts))))
     (def ^:private default-stacktrace-fonts
       (let [st-fonts
             (enc/get-env
               {:as :edn :default fmt-ex/default-fonts}
               [:taoensso.timbre.default-stacktrace-fonts<.edn> ; Undocumented
                :timbre-defaut-stacktrace-fonts<.edn>           ; Legacy
                ])]

         (if (valid-stacktrace-fonts? st-fonts) ; May be nil or {}
           (do                        st-fonts)
           (throw
             (ex-info invalid-stacktrace-fonts-msg
               {:given-fonts               st-fonts
                :default-fonts fmt-ex/default-fonts})))))

     (def ^:private valid-stacktrace-fonts!
       (enc/fmemoize
         (fn [st-fonts]
           (if (valid-stacktrace-fonts? st-fonts)
             (do                        st-fonts)
             (do
               ;; Log error on first encounter
               (error invalid-stacktrace-fonts-msg st-fonts)
               default-stacktrace-fonts)))))))

(defn default-output-error-fn
  "Default (fn [data]) -> string, used by `default-output-fn` to
  generate output for `:?err` value in log data.

  For Clj:
     Uses `org.clj-commons/pretty` to return an attractive stacktrace.
     Options:
       :stacktrace-fonts ; See `clj-commons.format.exceptions/*fonts*`

  For Cljs:
     Returns simple stacktrace string."

  [{:keys [?err output-opts] :as data}]
  (let [err (have ?err)]

    #?(:cljs
       (let [nl enc/newline]
         (str
           (.-stack err) ; Includes `ex-message`
           (when-let [d (ex-data err)]
             (str nl "ex-data:" nl "    " (pr-str d)))

           (when-let [c (ex-cause err)]
             (str nl nl "Caused by:" nl
               (default-output-error-fn
                 (assoc data :?err c))))))

       :clj
       (let [st-fonts
             (if-let [e (find output-opts :stacktrace-fonts)]
               (valid-stacktrace-fonts! (val e))
               default-stacktrace-fonts)]

         (binding [fmt-ex/*fonts* st-fonts]
          (fmt-ex/format-exception err))))))

(comment
  (default-output-error-fn
    {:?err (Exception. "Boo")
     :output-opts {:stacktrace-fonts {}}}))

;;;; Ns imports

#?(:clj
   (defmacro refer-timbre
     "(require
        '[taoensso.timbre :as timbre
          :refer [log  trace  debug  info  warn  error  fatal  report
                  logf tracef debugf infof warnf errorf fatalf reportf
                  spy]])"
     []
     `(require
        '~'[taoensso.timbre :as timbre
            :refer [log  trace  debug  info  warn  error  fatal  report
                    logf tracef debugf infof warnf errorf fatalf reportf
                    spy]])))

;;;; Appender shutdown

(defn shutdown-appenders!
  "Alpha, subject to change.

  Iterates through all appenders in config (enabled or not), and
  calls (:shutdown-fn appender) whenever that fn exists.

  This signals to these appenders that they should immediately
  close/release any resources that they may have open/acquired,
  and permanently noop on future logging requests.

  Returns the set of appender-ids that had a shutdown-fn called.

  This fn is called automatically on JVM shutdown, but can also
  be called manually."

  ([      ] (shutdown-appenders! *config*))
  ([config]
   (reduce-kv
     (fn [acc appender-id appender]
       (if-let [sfn (:shutdown-fn appender)]
         (do (sfn) (conj acc appender-id))
         acc))
     #{}
     (:appenders config))))

(comment (shutdown-appenders! {:appenders {:a {:shutdown-fn (fn [])} :b {}}}))

#?(:clj
   (defonce ^:private shutdown-hook
     (.addShutdownHook (Runtime/getRuntime)
       (Thread. ^Runnable shutdown-appenders!))))

;;;; Config

;;; Alias core appenders here for user convenience
#?(:clj  (enc/defalias         core-appenders/println-appender))
#?(:clj  (enc/defalias         core-appenders/spit-appender))
#?(:cljs (def println-appender core-appenders/println-appender))
#?(:cljs (def console-appender core-appenders/console-appender))

(def default-timestamp-opts
  "Controls (:timestamp_ data)"
  #?(:cljs {:pattern  :iso8601 #_"yy-MM-dd HH:mm:ss"}
     :clj
     {:pattern  :iso8601     #_"yyyy-MM-dd'T'HH:mm:ss.SSSX" #_"yy-MM-dd HH:mm:ss"
      :locale   :jvm-default #_(java.util.Locale. "en")
      :timezone :utc         #_(java.util.TimeZone/getTimeZone "Europe/Amsterdam")}))

(def default-config
  "Default/example Timbre `*config*` value:

    {:min-level :debug #_[[\"taoensso.*\" :error] [\"*\" :debug]]
     :ns-filter #{\"*\"} #_{:deny #{\"taoensso.*\"} :allow #{\"*\"}}

     :middleware [] ; (fns [data]) -> ?data, applied left->right

     :timestamp-opts default-timestamp-opts ; {:pattern _ :locale _ :timezone _}
     :output-fn default-output-fn ; (fn [data]) -> final output for use by appenders

     :appenders
     #?(:clj
        {:println (println-appender {:stream :auto})
         ;; :spit (spit-appender    {:fname \"./timbre-spit.log\"})
         }

        :cljs
        (if (exists? js/window)
          {:console (console-appender {})}
          {:println (println-appender {})}))}

    See `*config*` for more info."

  {:min-level :debug #_[["taoensso.*" :error] ["*" :debug]]
   :ns-filter #{"*"} #_{:deny #{"taoensso.*"} :allow #{"*"}}

   :middleware [] ; (fns [data]) -> ?data, applied left->right

   :timestamp-opts default-timestamp-opts ; {:pattern _ :locale _ :timezone _}
   :output-fn      default-output-fn ; (fn [data]) -> final output

   :appenders
   #?(:clj
      {:println (println-appender {:stream :auto})
       ;; :spit (spit-appender    {:fname "./timbre-spit.log"})
       }

      :cljs
      (if (exists? js/window)
        {:console (console-appender {})}
        {:println (println-appender {})}))})

(comment
  (set-config! default-config)
  (infof "Hello %s" "world :-)"))

(enc/defonce ^:dynamic *config*
  "This config map controls all Timbre behaviour including:
    - When to log (via min-level and namespace filtering)
    - How  to log (which appenders to use, etc.)
    - What to log (how log data will be transformed to final
                   output for use by appenders)

  Initial config value will be (in descending order of preference):

    1. `taoensso.timbre.config.edn`   JVM property  (read as EDN)
    2. `TAOENSSO_TIMBRE_CONFIG_EDN`   Env var       (read as EDN)
    3. `./taoensso.timbre.config.edn` resource file (read as EDN)
    4. Value of `default-config`

  For all EDN cases (1-3): the EDN can represent either a Clojure map
  to merge into `default-config`, or a qualified symbol that'll
  resolve to a Clojure map to merge into `default-config`.

  See `default-config` for more info on the base/default config.

  You can modify the config value with standard `alter-var-root`,
  or `binding`.

  For convenience, there's also some dedicated helper utils:

    - `set-config!`, `merge-config!`        ; Mutate *config*
    - `set-min-level!`, `set-ns-min-level!` ; Mutate *config* :min-level
    - `with-config`, `with-merged-config`   ; Bind *config*
    - `with-min-level`                      ; Bind *config* :min-level

  MAIN CONFIG OPTIONS
    :min-level
      Logging will occur only if a logging call's level is >= this
      min-level. Possible values, in order:

        :trace  = level 0
        :debug  = level 1 ; Default min-level
        :info   = level 2
        :warn   = level 3
        :error  = level 4 ; Error type
        :fatal  = level 5 ; Error type
        :report = level 6 ; High general-purpose (non-error) type

      It's also possible to set a namespace-specific min-level by
      providing a vector that maps `ns-pattern`s to min-levels, e.g.:
      `[[#{\"taoensso.*\"} :error] ... [#{\"*\"} :debug]]`.

      Example `ns-pattern`s:
        #{}, \"*\", \"foo.bar\", \"foo.bar.*\", #{\"foo\" \"bar.*\"},
        {:allow #{\"foo\" \"bar.*\"} :deny #{\"foo.*.bar.*\"}}.

      See also `set-ns-min-level!` for a helper tool.

    :ns-filter
      Logging will occur only if a logging call's namespace is permitted
      by this ns-filter. Possible values:

        - Arbitrary (fn may-log-ns? [ns]) predicate fn.
        - An `ns-pattern` (see :min-level docs above).

      Useful for turning off logging in noisy libraries, etc.

    :middleware
      Vector of simple (fn [data]) -> ?new-data fns (applied left->right)
      that transform the data map dispatched to appender fns. If any middleware
      returns nil, NO dispatch will occur (i.e. the event will be filtered).

      Useful for layering advanced functionality. Similar to Ring middleware.

    :timestamp-opts ; Config map, see `default-timestamp-opts`
    :output-fn      ; (fn [data]) -> final output for use by appenders,
                    ; see `default-output-fn` for example
    :output-opts    ; Optional map added to data sent to output-fn

    :appenders ; {<appender-id> <appender-map>}

      Where each appender-map has keys:
        :enabled?        ; Must be truthy to log
        :min-level       ; Optional *additional* appender-specific min-level
        :ns-filter       ; Optional *additional* appender-specific ns-filter

        :async?          ; Dispatch using agent? Useful for slow appenders (clj only)
                         ; Tip: consider calling (shutdown-agents) as part of your
                         ; application shutdown if you have this enabled for any
                         ; appenders.

        :rate-limit      ; [[<ncalls-limit> <window-msecs>] ...], or nil
                         ; Appender will noop a call after exceeding given number
                         ; of the \"same\" calls within given rolling window/s.
                         ;
                         ; Example:
                         ;   [[100  (encore/ms :mins  1)]
                         ;    [1000 (encore/ms :hours 1)]] will noop a call after:
                         ;
                         ;   - >100  \"same\" calls in 1 rolling minute, or
                         ;   - >1000 \"same\" calls in 1 rolling hour
                         ;
                         ; \"Same\" calls are identified by default as the
                         ; combined hash of:
                         ;   - Callsite (i.e. each individual Timbre macro form)
                         ;   - Logging level
                         ;   - All arguments provided for logging
                         ;
                         ; You can manually override call identification:
                         ;   (timbre/infof ^:meta {:id \"my-limiter-call-id\"} ...)
                         ;

        :timestamp-opts  ; Optional appender-specific override for top-level option
        :output-fn       ; Optional appender-specific override for top-level option
        :output-opts     ; Optional appender-specific override for top-level option

        :fn              ; (fn [data]) -> side-effects, with keys described below

  LOG DATA
    A single map with keys:
      :config          ; Entire active config map
      :context         ; `*context*` value at log time (see `with-context`)
      :appender-id     ; Id of appender currently dispatching
      :appender        ; Entire map of appender currently dispatching
      :instant         ; Platform date (java.util.Date or js/Date)
      :level           ; Call's level keyword (e.g. :info) (>= active min-level)
      :error-level?    ; Is level e/o #{:error :fatal}?
      :spying?         ; Is call occuring via the `spy` macro?
      :?ns-str         ; String,  or nil
      :?file           ; String,  or nil
      :?line           ; Integer, or nil
      :?column         ; Integer, or nil
      :?err            ; First-arg platform error, or nil
      :?meta           ; First-arg map when it has ^:meta metadata, used as a
                         way of passing advanced per-call options to appenders
      :vargs           ; Vector of raw args provided to logging call
      :timestamp_      ; Forceable - string
      :hostname_       ; Forceable - string (clj only)
      :output-fn       ; (fn [data]) -> final output for use by appenders
      :output_         ; Forceable result of calling (output-fn <this-data-map>)

      **NB** - any keys not specifically documented here should be
      considered private / subject to change without notice.

  COMPILE-TIME LEVEL/NS ELISION
    To control :min-level and :ns-filter at compile-time, use:

      - `taoensso.timbre.min-level.edn`  JVM property (read as EDN)
      - `taoensso.timbre.ns-pattern.edn` JVM property (read as EDN)

      - `TAOENSSO_TIMBRE_MIN_LEVEL_EDN`  env var      (read as EDN)
      - `TAOENSSO_TIMBRE_NS_PATTERN_EDN` env var      (read as EDN)

    Note that compile-time options will OVERRIDE options in `*config*`.

  DEBUGGING INITIAL CONFIG
    See `:_init-config` for information re: Timbre's config on initial load."

  #?(:cljs default-config
     :clj
     (let [{:keys [source value]}
           (enc/get-env {:as :edn, :return :map}
             :taoensso.timbre.config<.edn>)

           value (enc/nested-merge default-config value)]

       (assoc value :_init-config
         {:loaded-from-source  (or source [:default])
          :compile-time-config @compile-time-config_}))))

;;;; Deprecated

(enc/deprecated
  #?(:cljs (def ^:no-doc ^:deprecated console-?appender core-appenders/console-appender))
  (def  ^:no-doc ^:deprecated ordered-levels [:trace :debug :info :warn :error :fatal :report])
  (def  ^:no-doc ^:deprecated log? may-log?)
  (def  ^:no-doc ^:deprecated example-config "Prefer `default-config`." default-config)
  (defn ^:no-doc ^:deprecated logging-enabled? [level compile-time-ns] (may-log? level (str compile-time-ns)))
  (defn ^:no-doc ^:deprecated str-println      [& xs] (str-join xs))
  #?(:clj (defmacro ^:no-doc ^:deprecated with-log-level      [level  & body] `(with-min-level ~level ~@body)))
  #?(:clj (defmacro ^:no-doc ^:deprecated with-logging-config [config & body] `(with-config ~config ~@body)))
  #?(:clj (defmacro ^:no-doc ^:deprecated logp                [& args]        `(log ~@args)))
  #?(:clj
     (defmacro ^:no-doc ^:deprecated log-env
       ([                 ] `(log-env :debug))
       ([       level     ] `(log-env ~level "&env"))
       ([       level name] `(log-env *config* ~level ~name))
       ([config level name] `(log* ~config ~level ~name "=>" (get-env)))))

  (defn ^:no-doc ^:deprecated set-level! "Prefer `set-min-level!.`"
    [level] (swap-config! (fn [m] (assoc m :min-level level))))

  #?(:clj
     (defmacro ^:no-doc ^:deprecated with-level "Prefer `with-min-level`."
       [level & body] `(binding [*config* (assoc *config* :min-level ~level)] ~@body)))

  (defn ^:no-doc ^:deprected stacktrace
    "Prefer `default-output-error-fn`."
    ([err     ] (stacktrace err nil))
    ([err opts] (default-output-error-fn {:?err err :output-opts opts}))))
