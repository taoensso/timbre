(ns taoensso.timbre.appenders.core
  "Core Timbre appenders without any special dependency requirements.
  These can be aliased into the main Timbre ns for convenience."
  {:author "Peter Taoussanis (@ptaoussanis)"}
  #+clj
  (:require
   [clojure.string  :as str]
   [taoensso.encore :as enc :refer (have have? qb)])

  #+cljs
  (:require
   [clojure.string  :as str]
   [taoensso.encore :as enc :refer-macros (have have?)]))

;; TODO Add a simple official rolling spit appender?

;;;; Println appender (clj & cljs)

#+clj (enc/declare-remote taoensso.timbre/default-out
                          taoensso.timbre/default-err)
#+clj (alias 'timbre 'taoensso.timbre)

#+clj
(def ^:private ^:const system-newline
  (System/getProperty "line.separator"))

#+clj (defn- atomic-println [x] (print (str x system-newline)) (flush))

(defn println-appender
  "Returns a simple `println` appender for Clojure/Script.
  Use with ClojureScript requires that `cljs.core/*print-fn*` be set.

  :stream (clj only) - e/o #{:auto :*out* :*err* :std-err :std-out <io-stream>}."

  ;; Unfortunately no easy way to check if *print-fn* is set. Metadata on the
  ;; default throwing fn would be nice...

  [& #+clj [{:keys [stream] :or {stream :auto}}] #+cljs [_opts]]
  (let [#+clj stream
        #+clj (case stream
                :std-err timbre/default-err
                :std-out timbre/default-out
                stream)]

    {:enabled?   true
     :async?     false
     :min-level  nil
     :rate-limit nil
     :output-fn  :inherit
     :fn
     (fn [data]
       (let [{:keys [output_]} data]
         #+cljs (println (force output_))
         #+clj
         (let [stream
               (case stream
                 :auto  (if (:error? data) *err* *out*)
                 :*out* *out*
                 :*err* *err*
                 stream)]

           (binding [*out* stream]
             #+clj  (atomic-println (force output_))
             #+cljs (println        (force output_))))))}))

(comment (println-appender))

;;;; Spit appender (clj only)

#+clj
(defn spit-appender
  "Returns a simple `spit` file appender for Clojure."
  [& [{:keys [fname append?]
       :or   {fname "./timbre-spit.log"
              append? true}}]]
  {:enabled?   true
   :async?     false
   :min-level  nil
   :rate-limit nil
   :output-fn  :inherit
   :fn
   (fn self [data]
     (let [{:keys [output_]} data]
       (try
         (spit fname (str (force output_) "\n") :append append?)
         (catch java.io.IOException e
           (if (:__spit-appender/retry? data)
             (throw e) ; Unexpected error
             (let [_    (have? enc/nblank-str? fname)
                   file (java.io.File. ^String fname)
                   dir  (.getParentFile (.getCanonicalFile file))]

               (when-not (.exists dir) (.mkdirs dir))
               (self (assoc data :__spit-appender/retry? true))))))))})

(comment
  (spit-appender)
  (let [f (:fn (spit-appender))]
    (enc/qb 1000 (f {:output_ "boo"}))))

;;;; js/console appender (cljs only)

#+cljs
(defn console-appender
  "Returns a simple js/console appender for ClojureScript.

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
   (if (exists? js/console)
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

           (if (or (:raw-console? data)
                   (get-in data [:?meta :raw-console?])) ; Undocumented
             (let [output
                   ((:output-fn data)
                    (assoc data
                      :msg_  ""
                      :?err nil))
                   ;; (<output> <raw-error> <raw-arg1> <raw-arg2> ...):
                   args (->> (:vargs data) (cons (:?err data)) (cons output))]

               (.apply logger js/console (into-array args)))
             (.call    logger js/console (force (:output_ data)))))))

     (fn [data] nil))})

(comment (console-appender))

;;;; Deprecated

#+cljs (def console-?appender "DEPRECATED" console-appender)
