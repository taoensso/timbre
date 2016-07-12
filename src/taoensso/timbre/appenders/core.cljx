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

           (binding [*out* stream] (println (force output_))))))}))

(comment (println-appender))

;;;; Spit appender (clj only)

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
   (fn self [data]
     (let [{:keys [output_]} data]
       (try
         (spit fname (str (force output_) "\n") :append true)
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
   (if (and (exists? js/console) js/console.log)
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
         (let [logger (level->logger (:level data))]

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

;;;; TODO Temp, to demonstrate an auto-closing writer impl.

#+clj
(defprotocol IPersistentWriter
  (get-writer ^java.io.Writer [_])
  (close-writer [_])
  (write [_ s]))

#+clj
(defrecord PersistentWriter [fname writer-opts flush? writer__ on-write-fn]
  IPersistentWriter
  (get-writer [_]
    @(or
       @writer__
       (let [new-writer_
             (delay
               ;; (have? enc/nblank-str? fname)
               (try
                 (println (str "Opening: " fname))
                 (clojure.java.io/make-writer fname writer-opts)
                 (catch java.io.IOException _
                   (println (str "Creating dirs for: " fname))
                   (let [file (java.io.File. ^String fname)
                         dir  (.getParentFile (.getCanonicalFile file))]
                     (when-not (.exists dir) (.mkdirs dir))
                     (clojure.java.io/make-writer fname writer-opts)))))]

         (swap! writer__ (fn [?w_] (or ?w_ new-writer_))))))

  (close-writer [_]
    (when-let [writer_
               (loop []
                 (let [writer_ @writer__]
                   (if (compare-and-set! writer__ writer_ nil)
                     writer_
                     (recur))))]
      (println (str "Closing: " fname))
      (.close ^java.io.Closeable @writer_)
      true))

  (write [self s]
    (let [^String s s
          writer (get-writer self)]
      (try
        (.write writer s)
        (when flush? (.flush writer))
        (when on-write-fn (on-write-fn))
        (catch java.io.IOException _
          (reset! writer__ nil)
          (let [writer (get-writer self)]
            (.write writer s)
            (when flush? (.flush writer))
            (when on-write-fn (on-write-fn))))))))

#+clj
(defn persistent-writer
  [fname
   {:keys [writer-opts flush? timeout-ms]
    :or   {writer-opts {:append true}
           flush? true
           timeout-ms 5000}}]

  (have? enc/nblank-str? fname)
  (let [open?_   (atom false)
        used?_   (atom false) ; Used w/in last timeout window?
        writer__ (atom nil)
        on-write-fn
        (fn []
          (reset! used?_ true)
          (reset! open?_ true))

        pw (PersistentWriter. fname writer-opts flush?
             writer__ on-write-fn)]

    (when-let [^long timeout-ms timeout-ms]
      (let [timer-name (str "Timbre persistent writer" fname)
            timer (java.util.Timer. timer-name true)
            timer-fn
            (proxy [java.util.TimerTask] []
              (run []
                (enc/catch-errors*
                  (let [used? @used?_]
                    (reset! used?_ false)
                    (when-not used?
                      (when (compare-and-set! open?_ true false)
                        (close-writer pw)))))))]
        (.schedule timer timer-fn 0 timeout-ms)))
    pw))

(comment
  (def pw (persistent-writer "foo" {}))
  (qb 100 (write pw ".")) ; 0.18
  )
