# Timbre, a (sane) logging library for Clojure

Logging with Java can be maddeningly, unnecessarily hard. Particularly if all you want is something *simple that works out the box*.

[tools.logging](https://github.com/clojure/tools.logging) helps, but it doesn't save you from the mess of logger dependencies and configuration hell.

Timbre is an attempt to make **simple logging simple** and more **complex logging possible**.

## What's In The Box?
 * Small, uncomplicated **all-Clojure** library.
 * **Super-simple map-based config**: no arcane XML or properties files.
 * Decent performance (**low overhead**).
 * Flexible **fn-centric appender model**.
 * Sensible built-in appenders including simple **email appender**.
 * Tunable **flood control**.
 * **Asynchronous** logging support.

## Status [![Build Status](https://secure.travis-ci.org/ptaoussanis/timbre.png)](http://travis-ci.org/ptaoussanis/timbre)

Timbre was built in a day after I finally lost my patience trying to configure Log4j. I tried to keep the design simple and sensible but I didn't spend much time thinking about it so there may still be room for improvement. In particular **the configuration and appender formats are still subject to change**.

## Getting Started

### Leiningen

Depend on `[timbre "0.5.1-SNAPSHOT"]` in your `project.clj` and `use` the library:

```clojure
(ns my-app
  (:use [timbre.core :as timbre :only (trace debug info warn error fatal spy)])
```

### Start Logging

By default, Timbre gives you basic print output to `*out*`/`*err*` at a `debug` logging level:

```clojure
(info "This will print")
=> 2012-May-28 17:26:11:444 +0700 INFO [timbre.tests] - This will print

(trace "This won't print due to insufficient logging level")
=> nil
```

There's little overhead for checking logging levels:

```clojure
(time (trace (Thread/sleep 5000)))
=> "Elapsed time: 0.054 msecs"

(time (when true))
=> "Elapsed time: 0.051 msecs"
```

First-argument exceptions generate a stack trace:

```clojure
(info (Exception. "Oh noes") "arg1" "arg2")
=> 2012-May-28 17:35:16:132 +0700 INFO [timbre.tests] - arg1 arg2
java.lang.Exception: Oh noes
            NO_SOURCE_FILE:1 timbre.tests/eval6409
          Compiler.java:6511 clojure.lang.Compiler.eval
          [...]
```

### Configuration

Easily adjust the current logging level:

```clojure
(timbre/set-level! :warn)
```

And the default timestamp formatting for log messages:

```clojure
(timbre/set-config! [:shared-appender-config :timestamp-pattern]
                    "yyyy-MMM-dd HH:mm:ss ZZ")
(timbre/set-config! [:shared-appender-config :locale]
                    (java.util.Locale/GERMAN))
```

Enable the standard [Postal](https://github.com/drewr/postal)-based email appender:

```clojure
(timbre/set-config! [:shared-appender-config :postal]
                    ^{:host "mail.isp.net" :user "jsmith" :pass "sekrat!!1"}
                    {:from "me@draines.com" :to "foo@example.com"})

(timbre/set-config! [:appenders :postal :enabled?] true)
```

Rate-limit to one email per message per minute:

```clojure
(timbre/set-config! [:appenders :postal :max-message-per-msecs] 60000)
```

And make sure emails are sent asynchronously:

```clojure
(timbre/set-config! [:appenders :postal :async?] true)
```

### Custom Appenders

Writing a custom appender is easy:

```clojure
(timbre/set-config!
 [:appenders :my-appender]
 {:doc       "Hello-world appender"
  :min-level :debug
  :enabled?  true
  :async?    false
  :max-message-per-msecs nil ; No rate limiting
  :fn (fn [{:keys [ap-config level error? instant timestamp
                  ns message more] :as args}]
        (when-not (:production-mode? ap-config)
          (apply println timestamp "Hello world!" message more)))
```

And because appender fns are just regular Clojure fns, you have *unlimited power*: write to your database, send a message over the network, check some other state (e.g. environment config) before making a choice, etc.

See `(doc timbre/config)` for more information on appenders.

## Timbre Supports the ClojureWerkz Project Goals

[ClojureWerkz](http://clojurewerkz.org/) is a growing collection of open-source, batteries-included libraries for Clojure that emphasise modern targets, good documentation, and thorough testing.

## Contact & Contribution

Reach me (Peter Taoussanis) at *ptaoussanis at gmail.com* for questions/comments/suggestions/whatever. I'm very open to ideas if you have any!

I'm also on Twitter: [@ptaoussanis](https://twitter.com/#!/ptaoussanis).

## License

Copyright &copy; 2012 Peter Taoussanis

Distributed under the [Eclipse Public License](http://www.eclipse.org/legal/epl-v10.html), the same as Clojure.