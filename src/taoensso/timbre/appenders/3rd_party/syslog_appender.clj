(ns taoensso.timbre.appenders.3rd-party.syslog-appender
  "Requires https://github.com/java-native-access/jna"
  {:author "Audrius Molis @audriu"}
  (:require
   [taoensso.timbre :as timbre])
  (:import [com.sun.jna Function]))

(def ^:private timbre->syslog-priority
  "Map timbre log levels to syslog levels"
  {:trace  7 ;debug
   :debug  7 ;debug
   :info   6 ;info
          ;5 ;notice
   :warn   4 ;warning
   :error  3 ;err
          ;2 ;crit
   :report 1 ;alert
   :fatal  0 ;emerg
   })

(defn- <<3 [n] (bit-shift-left n 3))

(def ^:private facility-map
  {:log-kern     (<<3 0)
   :log-user     (<<3 1)
   :log-mail     (<<3 2)
   :log-daemon   (<<3 3)
   :log-auth     (<<3 4)
   :log-syslog   (<<3 5)
   :log-lpr      (<<3 6)
   :log-news     (<<3 7)
   :log-uucp     (<<3 8)
   :log-cron     (<<3 9)
   :log-authpriv (<<3 10)
   :log-ftp      (<<3 11)
   :log-ntp      (<<3 12)
   :log-security (<<3 13)
   :log-local0   (<<3 16)
   :log-local1   (<<3 17)
   :log-local2   (<<3 18)
   :log-local3   (<<3 19)
   :log-local4   (<<3 20)
   :log-local5   (<<3 21)
   :log-local6   (<<3 22)
   :log-local7   (<<3 23)})

(def ^:private options
  {:log-pid    (byte 0x01) ;; log the pid
   :log-cons   (byte 0x02) ;; log to the console if errors in sending
   :log-odelay (byte 0x04) ;; delay open until first call
   :log-ndelay (byte 0x08) ;; don't delay open
   :log-nowait (byte 0x10) ;; don't wait for console forks (deprecated)
   :log-perror (byte 0x20) ;; log to stderr as well
   })

(defn- openlog
  [ident option facility]
  (let [f (com.sun.jna.Function/getFunction "c" "openlog")]
    (.invoke f Integer (to-array [ident option facility]))))

(defn- syslog
  [priority format & args]
  (let [f (com.sun.jna.Function/getFunction "c" "syslog")]
    (.invoke f Integer (to-array (flatten [priority format args])))))

(defn- log-message [ident syslog-options facility data]
  (openlog ident syslog-options facility)
  (let [{:keys [level vargs]} data
        priority (timbre->syslog-priority level)]
    (syslog priority (str vargs))))

(defn syslog-appender
  "Returns a syslog appender. All parameters are optional.
  (journald-appender
    {:ident \"my-app\"
     :syslog-options bytemap of values from 'options' map.
     :facility :log-user}})"
  [config]
  (let [{:keys [ident syslog-options facility]
         :or {facility :log-user}} config
        facility (facility-map facility)]
    {:enabled?   true
     :async?     true
     :min-level  nil
     :output-fn  :inherit
     :fn (fn [data] (log-message ident syslog-options facility data))}))

(comment
  (timbre/merge-config!
   {:appenders
    {:syslog-appender
     (taoensso.timbre.appenders.3rd-party.syslog-appender/syslog-appender
      {:ident "my-app"
       :syslog-options (byte 0x03)
       :facility :log-user})}}))
