(ns taoensso.timbre.appenders.core
  "Core Timbre appenders without any special dependency requirements.
  These can be aliased into the main Timbre ns for convenience."
  {:author "Peter Taoussanis (@ptaoussanis)"}
  (:require
   [clojure.string  :as str]
   #?(:clj [clojure.java.io :as jio])
   [taoensso.encore :as enc :refer [have have?]]))

;; TODO Add a simple official rolling spit appender?

;;;; Println appender (clj & cljs)

#?(:clj (enc/declare-remote taoensso.timbre/default-out
                            taoensso.timbre/default-err))

#?(:clj (alias 'timbre 'taoensso.timbre))

(defn println-appender
  "Returns a simple `println` appender for Clojure/Script.
  Use with ClojureScript requires that `cljs.core/*print-fn*` be set.

  :stream (clj only) - e/o #{:auto :*out* :*err* :std-err :std-out <io-stream>}."

  ;; Unfortunately no easy way to check if *print-fn* is set. Metadata on the
  ;; default throwing fn would be nice...

  [& #?(:clj [{:keys [stream] :or {stream :auto}}] :cljs [_opts])]
  (let #?(:cljs []
          :clj  [stream
                 (case stream
                   :std-err timbre/default-err
                   :std-out timbre/default-out
                   stream)])

    {:enabled? true
     :fn
     (fn [data]
       (let [{:keys [output_]} data]
         #?(:cljs (println (force output_))
            :clj
            (let [stream
                  (case stream
                    :auto  (if (:error-level? data) *err* *out*)
                    :*out* *out*
                    :*err* *err*
                    stream)]

              (binding [*out* stream]
                (enc/println-atomic (force output_)))))))}))

(comment (println-appender))

;;;; Spit appender (clj only)

#?(:clj
   (defn- write-to-file [data fname append? output self]
     (try
       (with-open [^java.io.BufferedWriter w (jio/writer fname :append append?)]
         (.write   w ^String output)
         (.newLine w))

       (catch java.io.IOException e
         (if (:spit-appender/retry? data)
           (throw e) ; Unexpected error
           (do
             (jio/make-parents fname)
             (self (assoc data :spit-appender/retry? true))))))))

#?(:clj
   (defn spit-appender
     "Returns a simple `spit` file appender for Clojure."
     [& [{:keys [fname append? locking?]
          :or   {fname "./timbre-spit.log"
                 append?  true
                 locking? true}}]]

     (have? enc/nblank-str? fname)

     {:enabled? true
      :fn
      (let [lock (Object.)]
        (fn self [{:keys [output_] :as data}]
          (let [output (force output_)] ; Must deref outside lock, Ref. #330
            (if locking? ; For thread safety, Ref. #251
              (locking lock (write-to-file data fname append? output self))
              (do           (write-to-file data fname append? output self))))))}))

(comment
  (spit-appender)
  (let [f (:fn (spit-appender))]
    (enc/qb 1e3 (f {:output_ "boo"}))))

;;;; js/console appender (cljs only)

#?(:cljs
   (defn console-appender
     "Returns a simple js/console appender for ClojureScript.

     Raw logging

       There's 2 ways that Timbre can log to a web browser console:
         1. As a prepared output string (default)
         2. As a list of raw argument objects

       The benefit of #2 is that it allows the browser to offer type-specific
       object printing and inspection (e.g. for maps, etc.).

       Raw logging can be enabled or disabled as follows:

         1. On a per-call basis via a special 1st argument to your logging call:
              (info ^:meta {:raw-console? true} arg1 ...)

         2. Via middleware, by adding an option to your log data:
              (fn my-middleware [data] (assoc data :raw-console? true))

         3. Via an option provided to this appender constructor:
              (console-appender {:raw-console? <bool>})

     Ignoring library / \"blackbox\" code for accurate line numbers, etc.

       Most web browsers offer a feature to ignore library or \"blackbox\" code
       in their debugger.

       You'll probably want to ignore at least the following:
         `/taoensso/timbre/appenders/core\\.js$` ; Timbre console appender
         `/taoensso/timbre\\.js$`                ; Timbre core
         `/cljs/core\\.js$`                      ; ClojureScript core

       Depending on the browser, you can usually set up these exclusions through
       right-click popups and/or through a configurable list in a settings menu.

       For example:
         https://developer.chrome.com/docs/devtools/settings/ignore-list/
         https://webkit.org/web-inspector/web-inspector-settings/
         https://firefox-source-docs.mozilla.org/devtools-user/debugger/how_to/ignoring_sources/index.html
         etc."

     ;; TODO [#132] Any way of using something like `Function.prototype.bind`
     ;; (Ref. https://goo.gl/IZzkQB) to get accurate line numbers in all
     ;; browsers w/o the need for blackboxing?

     [& [{:keys [raw-console?]}]]
     {:enabled? true
      :fn
      (if-not (exists? js/console)
        (fn [data] nil)

        (let [;; Don't cache this; some libs dynamically replace js/console
              level->logger
              (fn [level]
                (or
                  (case level
                    :trace  js/console.trace
                    :debug  js/console.debug
                    :info   js/console.info
                    :warn   js/console.warn
                    :error  js/console.error
                    :fatal  js/console.error
                    :report js/console.info)
                  js/console.log))]

          (fn [data]
            (when-let [logger (level->logger (:level data))]

              (if-let [raw-console?
                       (enc/cond
                         :let [?meta (get data :?meta)]

                         ;; Useful for control via individual calls
                         (contains? ?meta :raw-console?) (get ?meta :raw-console?)

                         ;; Useful for control via middleware, etc.
                         (contains? data  :raw-console?) (get data  :raw-console?)

                         ;; Appender-level default
                         :else raw-console?)]

                (let [output
                      ((:output-fn data)
                       (assoc data
                         :msg-type nil
                         :?err     nil))

                      args ; (<output> ?<raw-error> <raw-arg1> <raw-arg2> ...)
                      (let [vargs (:vargs data)]
                        (if-let [err (:?err data)]
                          (cons output (cons err vargs))
                          (cons output           vargs)))]

                  (.apply logger js/console (into-array args)))
                (.call    logger js/console (force (:output_ data))))))))}))

(comment (console-appender))

;;;; Deprecated

(enc/deprecated
  #?(:cljs (def ^:no-doc ^:deprecated console-?appender console-appender)))
