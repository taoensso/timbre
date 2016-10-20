(try
  (require 'franzy.clients.producer.client)
  (catch Excpetion e
    (println "please add dependency `[ymilky/franzy "0.0.1"]'")
    (throw e)))

(ns taoensso.timbre.appenders.3rd-party.kafka-appender
  "franzy (kafka) appender.
  Requires [franzy](https://github.com/ymilky/franzy)"
  (:require
   [franzy.serialization.serializers :as serializers]
   [franzy.clients.producer.client :as producer]
   [franzy.clients.producer.defaults :as pd]
   [franzy.clients.producer.protocols :refer :all]))

(def ^:private -partition       0)
(def ^:private -default-options (pd/make-default-producer-options))
(def ^:private -producer        (atom nil))

(defn naive-key-strategy [_]
  "")

(defn build-producer
  "normally need `:bootstrap.servers` in `kafka-config`"
  [{:keys [kafka-config key-serializer value-serializer]
    :or {key-serializer serializers/string-serializer
         value-serializer serializers/edn-serializer}}]
  (let [pc (merge {:bootstrap.servers ["127.0.0.1:9092"]
                   :acks              "all"
                   :retries           0
                   :batch.size        16384
                   :linger.ms         10
                   :buffer.memory     33554432}
                  kafka-config)]
    (producer/make-producer pc
                            (key-serializer)
                            (value-serializer)
                            -default-options)))

(defn- -log-message [{:keys [topic] :as config}
                     entry-fn
                     data]
  (swap! -producer #(or % (build-producer config)))
  (send-async! @-producer
               topic
               -partition
               (naive-key-strategy data)
               (entry-fn data)
               -default-options))

(defn- -default-entry-fn
  [{:keys [instant level hostname_
           context ?err ?ns-str ?file ?line msg_]
    :as data}]
  {:instant  instant    ;; java.util.Date
   :level    level
   :hostname (force hostname_)
   :context  context
   :?err     (when-let [err ?err] (str err))
   :?ns-str  ?ns-str
   :?file    ?file
   :?line    ?line
   :msg      (force msg_)})

(defn kafka-appender
  "Returns a kafka appender.
  (franzy-appender
    {:topic \"test-topic\"
     :kafka-config {:bootstrap.servers [\"127.0.0.1:9093\"]}
  })"
  [{:keys [topic entry-fn]
    :or   {entry-fn -default-entry-fn}
    :as config}]
  {:enabled?   true
   :async?     true
   :min-level  nil
   :rate-limit nil
   :output-fn  :inherit
   :fn (fn [data]
         (-log-message config entry-fn data))})
