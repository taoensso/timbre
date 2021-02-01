(ns taoensso.timbre.appenders.core
  "Core Timbre appenders without any special dependency requirements.
  These can be aliased into the main Timbre ns for convenience."
  {:author "Peter Taoussanis (@ptaoussanis)"}
  #?(:clj
     (:require
      [clojure.string  :as str]
      [clojure.java.io :as jio]
      [taoensso.encore :as enc :refer [have have? qb deprecated]])

     :cljs
     (:require
      [clojure.string  :as str]
      [taoensso.encore :as enc :refer-macros [have have?]]))

  #?(:cljs
     (:require-macros
      [taoensso.encore :as enc-macros :refer [deprecated]])))

;; TODO Add a simple official rolling spit appender?

;;;; Println appender (clj & cljs)

#?(:clj (enc/declare-remote taoensso.timbre/default-out
                            taoensso.timbre/default-err))

#?(:clj (alias 'timbre 'taoensso.timbre))

#?(:clj
   (let [system-newline enc/system-newline]
     (defn- atomic-println [x] (print (str x system-newline)) (flush))))

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

    {:enabled?   true
     :async?     false
     :min-level  nil
     :rate-limit nil
     :output-fn  :inherit
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
                #?(:clj  (atomic-println (force output_))
                   :cljs (println        (force output_))))))))}))

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

     (let [lock (Object.)]
       {:enabled? true
        :fn
        (fn self [{:keys [output_] :as data}]
          (let [output (force output_)]
            (if locking?
              (locking lock
                (write-to-file data fname append? output self))
              (write-to-file data fname append? output self))))})))

(comment
  (spit-appender)
  (let [f (:fn (spit-appender))]
    (enc/qb 1000 (f {:output_ "boo"}))))

;;;; js/console appender (cljs only)

#?(:cljs
   (defn console-appender
     "Returns a simple js/console appender for ClojureScript.

     Use ^:meta {:raw-console? true} as first argument to logging call if
     you want args sent to console in a raw format enabling console-based
     pretty-printing of JS objects, etc. E.g.:

       (info                             my-js-obj) ; Send string   to console
       (info ^:meta {:raw-console? true} my-js-obj) ; Send raw args to console

     For accurate line numbers in Chrome, add these Blackbox[1] patterns:
       `/taoensso/timbre/appenders/core\\.js$`
       `/taoensso/timbre\\.js$`
       `/cljs/core\\.js$`

     [1] Ref. https://goo.gl/ZejSvR"

     ;; TODO Any way of using something like `Function.prototype.bind`
     ;; (Ref. https://goo.gl/IZzkQB) to get accurate line numbers in all
     ;; browsers w/o the need for Blackboxing?

     [& [opts]]
     {:enabled?   true
      :async?     false
      :min-level  nil
      :rate-limit nil
      :output-fn  :inherit
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

              (if (or (get    data :raw-console?) ; Undocumented
                      (get-in data [:?meta :raw-console?]))

                (let [output
                      ((:output-fn data)
                       (assoc data
                         :msg_  ""
                         :?err nil))

                      args ; (<output> ?<raw-error> <raw-arg1> <raw-arg2> ...)
                      (let [vargs (:vargs data)]
                        (if-let [err (:?err data)]
                          (cons output (cons err vargs))
                          (cons output           vargs)))]

                  (.apply logger js/console (into-array args)))
                (.call    logger js/console (force (:output_ data))))))))}))

(comment (console-appender))

;;;; Deprecated

(deprecated
  #?(:cljs (def console-?appender "DEPRECATED" console-appender)))
