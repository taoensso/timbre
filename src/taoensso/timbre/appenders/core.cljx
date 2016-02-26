(ns taoensso.timbre.appenders.core
  "Core Timbre appenders without any special dependency requirements.
  These can be aliased into the main Timbre ns for convenience."
  {:author "Peter Taoussanis"}
  #+clj
  (:require
   [clojure.string  :as str]
   [taoensso.encore :as enc :refer (have have? qb)])

  #+cljs
  (:require
   [clojure.string  :as str]
   [taoensso.encore :as enc :refer-macros (have have?)]))

;; TODO Add a simple official rolling spit appender?

;;;; Example appender ---> (COPY THIS TO MAKE YOUR OWN APPENDER) <---

#_
(defn example-appender
  "Docstring to explain any special opts to influence appender construction,
  etc. Returns the appender map."
  [& [{:keys [] :as opts}]]

  {:enabled?   true  ; Please enable by default
   :async?     false ; Use agent for appender dispatch? Useful for slow dispatch.
   :min-level  nil   ; nil (no min level), or min logging level keyword
   ;; :rate-limit nil
   :rate-limit [[5   (enc/ms :mins  1)] ; 5 calls/min
                [100 (enc/ms :hours 1)] ; 100 calls/hour
                ]

   :output-fn :inherit ; or a custom (fn [data]) -> string
   :fn
   (fn [data]
     (let [;; See `timbre/example-config` for info on all available args:
           {:keys [instant level ?err_ vargs_ output-fn
                   config   ; Entire Timbre config map in effect
                   appender ; Entire appender map in effect
                   ]}
           data

           ?err  @?err_  ; ?err non-nil iff first given arg was an error
           vargs @vargs_ ; Vector of raw args (excl. possible first error)

           ;; You'll often want an output string with ns, timestamp, vargs, etc.
           ;; A (fn [data]) -> string formatter is provided under the :output-fn
           ;; key, defined as:
           ;; `(or (:output-fn <this appender's map>)
           ;;      (:output-fn <user's config map)
           ;;      timbre/default-output-fn)`
           ;;
           ;; Users therefore get a standardized way to control appender ouput
           ;; formatting for all participating appenders. See
           ;; `taoensso.timbre/default-output-fn` source for details.
           ;;
           output-str (output-fn data)]
       (println output-str)))})

(comment (merge (example-appender) {:min-level :debug}))

;;;; Println appender (clj & cljs)

#+clj (enc/declare-remote taoensso.timbre/default-out
                          taoensso.timbre/default-err)
#+clj (alias 'timbre 'taoensso.timbre)

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
       (let [{:keys [output-fn]} data]
         #+cljs (println (output-fn data))
         #+clj
         (let [stream (case stream
                        :auto  (if (:error? data) *err* *out*)
                        :*out* *out*
                        :*err* *err*
                        stream)]
           (binding [*out* stream] (println (output-fn data))))))}))

(comment (println-appender))

;;;; Spit appender (clj only)

#+clj
(def ^:private ensure-spit-dir-exists!
  (enc/memoize* (enc/ms :mins 1)
    (fn [fname]
      (when-not (str/blank? fname)
        (let [file (java.io.File. ^String fname)
              dir  (.getParentFile (.getCanonicalFile file))]
          (when-not (.exists dir) (.mkdirs dir)))))))

#+clj
(defn spit-appender
  "Returns a simple `spit` file appender for Clojure."
  [& [{:keys [fname] :or {fname "./timbre-spit.log"}}]]
  {:enabled?   true
   :async?     false
   :min-level  nil
   :rate-limit nil
   :output-fn  :inherit
   :fn
   (fn [data]
     (let [{:keys [output-fn]} data]
       (try ; To allow TTL-memoization of dir creator
         (ensure-spit-dir-exists! fname)
         (spit fname (str (output-fn data) "\n") :append true)
         (catch java.io.IOException _))))})

(comment (spit-appender))

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

  [& [{:keys [raw-output?]} ; Undocumented (experimental)
      ]]
  {:enabled?   true
   :async?     false
   :min-level  nil
   :rate-limit nil
   :output-fn  :inherit
   :fn
   (if (and (exists? js/console) js/console.log)
     (let [level->logger
           {:trace  (or js/console.trace js/console.log)
            :debug  (or js/console.debug js/console.log)
            :info   (or js/console.info  js/console.log)
            :warn   (or js/console.warn  js/console.log)
            :error  (or js/console.error js/console.log)
            :fatal  (or js/console.error js/console.log)
            :report (or js/console.info  js/console.log)}]

       (fn [data]
         (let [{:keys [level output-fn vargs_]} data
               vargs      @vargs_
               [v1 vnext] (enc/vsplit-first vargs)
               logger     (level->logger level js/console.log)]

           (if (or raw-output? (= v1 :timbre/raw)) ; Undocumented
             (let [output (output-fn (merge data {:msg_  (delay "")
                                                  :?err_ (delay nil)}))
                   ;; [<output> <raw-error> <raw-arg1> <raw-arg2> ...]:
                   args (->> vnext (cons @(:?err_ data)) (cons output))]

               (.apply logger js/console (into-array args)))
             (.call    logger js/console (output-fn data))))))

     (fn [data] nil))})

(comment (console-appender))

;;;; Deprecated

#+cljs (def console-?appender "DEPRECATED" console-appender)
