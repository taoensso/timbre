# Included appenders

## Basic file appender

```clojure
;; (:require [taoensso.timbre.appenders.core :as appenders]) ; Add to ns

(timbre/merge-config!
  {:appenders {:spit (appenders/spit-appender {:fname "/path/my-file.log"})}})

;; (timbre/merge-config! {:appenders {:spit {:enabled? false}}} ; To disable
;; (timbre/merge-config! {:appenders {:spit nil}}               ; To remove entirely
```

## [Carmine](https://github.com/taoensso/carmine) Redis appender

```clojure
;; [com.taoensso/carmine <latest-version>] ; Add to project.clj deps
;; (:require [taoensso.timbre.appenders [carmine :as car-appender]]) ; Add to ns

(timbre/merge-config! {:appenders {:carmine (car-appender/carmine-appender)}})
```

This gives us a high-performance Redis appender:

 * **All raw logging args are preserved** in serialized form (even errors).
 * Configurable number of entries to keep per log level.
 * Only the most recent instance of each **unique entry** is kept.
 * Resulting **log is just a Clojure value**: a vector of log entries (maps).

Clojure has a rich selection of built-in and community tools for querying values like this. 

See also [`car-appender/query-entries`](https://taoensso.github.io/timbre/taoensso.timbre.appenders.carmine.html#var-query-entries).

## [Postal](https://github.com/drewr/postal) email appender

```clojure
;; [com.draines/postal <latest-version>] ; Add to project.clj deps
;; (:require [taoensso.timbre.appenders (postal :as postal-appender)]) ; Add to ns

(timbre/merge-config!
  {:appenders
   {:postal
    (postal-appender/postal-appender
      ^{:host "mail.isp.net" :user "jsmith" :pass "sekrat!!1"}
      {:from "me@draines.com" :to "foo@example.com"})}})
```

## Community appenders

See [community resources](./3-Community-resources) section.