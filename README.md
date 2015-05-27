**[API docs][]** | **[CHANGELOG][]** | [other Clojure libs][] | [Twitter][] | [contact/contrib](#contact--contributing) | current [Break Version][]:

```clojure
[com.taoensso/timbre "3.4.0"]       ; Stable
[com.taoensso/timbre "4.0.0-beta1"] ; BREAKING, please see CHANGELOG for details
```

# Timbre, a (sane) Clojure/Script logging & profiling library

Java logging is a tragic comedy of crazy, unnecessary complexity that buys you _nothing_. It can be maddeningly, unnecessarily hard to get even the simplest logging working. We can do **so** much better with Clojure/Script.

Timbre brings functional, Clojure-y goodness to all your logging needs. It's fast, deeply flexible, and easy to configure. **No XML**!

## What's in the box™?
  * Full **Clojure** + **ClojureScript** support (v4+)
  * No XML or properties files. **One config map**, and you're set
  * Deeply flexible **fn appender model** with **middleware**
  * **Fantastic performance** at any scale
  * Filter logging by levels and **namespace whitelist/blacklist patterns**
  * **Zero overhead** with **complete Clj+Cljs elision** for compile-time level/ns filters
  * Useful built-in appenders for **out-the-box** Clj+Cljs logging
  * Powerful, easy-to-configure per-appender **rate limits** and **async logging**
  * [Logs as Clojure values](#redis-carmine-appender-v3) (v3+)
  * [tools.logging](https://github.com/clojure/tools.logging) support (optional, useful when integrating with legacy logging systems)
  * Level and ns-filter aware **logging profiler**
  * Tiny, **simple**, cross-platform codebase

## 3rd-party tools, appenders, etc.
  * [log-config](https://github.com/palletops/log-config) by [Hugo Duncan](https://github.com/hugoduncan) - library to help manage Timbre logging config.
  * Other suggestions welcome!

## Getting started

### Dependencies

Add the necessary dependency to your [Leiningen][] `project.clj` and use the supplied ns-import helper:

```clojure
[com.taoensso/timbre "4.0.0-beta1"] ; Add to your project.clj :dependencies

(ns my-app ; Your ns
  (:require [taoensso.timbre :as timbre
             :refer (log  trace  debug  info  warn  error  fatal  report
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy)]

            ;; Clj only:
            [taoensso.timbre.profiling :as profiling
             :refer (pspy pspy* profile defnp p p*)]))
```

You can also use `timbre/refer-timbre` to setup these ns refers automatically (Clj only).

### Logging

By default, Timbre gives you basic print stream or `js/console` (v4+) output at a `debug` log level:

```clojure
(info "This will print") => nil
%> 2012-May-28 17:26:11:444 +0700 localhost INFO [my-app] - This will print

(spy :info (* 5 4 3 2 1)) => 120
%> 2012-May-28 17:26:14:138 +0700 localhost INFO [my-app] - (* 5 4 3 2 1) 120

(trace "This won't print due to insufficient log level") => nil
```

First-argument exceptions generate a nicely cleaned-up stack trace using [io.aviso.exception](https://github.com/AvisoNovate/pretty) (Clj only):

```clojure
(info (Exception. "Oh noes") "arg1" "arg2")
%> 2012-May-28 17:35:16:132 +0700 localhost INFO [my-app] - arg1 arg2
java.lang.Exception: Oh noes
            NO_SOURCE_FILE:1 my-app/eval6409
          Compiler.java:6511 clojure.lang.Compiler.eval
          <...>
```

### Configuration

This is the biggest win over Java logging IMO. Here's `timbre/example-config` (also Timbre's default config):

> The example below shows config for **Timbre v4**. See [here](https://github.com/ptaoussanis/timbre/tree/v3.4.0#configuration) for an example of **Timbre v3** config.

```clojure
(def example-config
  "Example (+default) Timbre v4 config map.

  APPENDERS
    An appender is a map with keys:
      :min-level       ; Level keyword, or nil (=> no minimum level)
      :enabled?        ;
      :async?          ; Dispatch using agent? Useful for slow appenders
      :rate-limit      ; [[ncalls-limit window-ms] <...>], or nil
      :output-fn       ; Optional override for inherited (fn [data]) -> string
      :fn              ; (fn [data]) -> side effects, with keys described below

    An appender's fn takes a single data map with keys:
      :config          ; Entire config map (this map, etc.)
      :appender-id     ; Id of appender currently dispatching
      :appender        ; Entire map of appender currently dispatching

      :instant         ; Platform date (java.util.Date or js/Date)
      :level           ; Keyword
      :error-level?    ; Is level e/o #{:error :fatal}?
      :?ns-str         ; String, or nil
      :?file           ; String, or nil  ; Waiting on CLJ-865
      :?line           ; Integer, or nil ; Waiting on CLJ-865

      :?err_           ; Delay - first-arg platform error, or nil
      :vargs_          ; Delay - raw args vector
      :hostname_       ; Delay - string (clj only)
      :msg_            ; Delay - args string
      :timestamp_      ; Delay - string
      :output-fn       ; (fn [data]) -> formatted output string

      :profile-stats   ; From `profile` macro

      <Also incl. any *context* keys, which get merged into data map>

  MIDDLEWARE
    Middleware are simple (fn [data]) -> ?data fns (applied left->right) that
    transform the data map dispatched to appender fns. If any middleware returns
    nil, NO dispatching will occur (i.e. the event will be filtered).

  The `example-config` source code contains further settings and details.
  See also `set-config!`, `merge-config!`, `set-level!`."

  {:level :debug  ; e/o #{:trace :debug :info :warn :error :fatal :report}

   ;; Control log filtering by namespaces/patterns. Useful for turning off
   ;; logging in noisy libraries, etc.:
   :ns-whitelist  [] #_["my-app.foo-ns"]
   :ns-blacklist  [] #_["taoensso.*"]

   :middleware [] ; (fns [data]) -> ?data, applied left->right

   ;; Clj only:
   :timestamp-opts default-timestamp-opts ; {:pattern _ :locale _ :timezone _}

   :output-fn default-output-fn ; (fn [data]) -> string

   :appenders
   {:example-println-appender ; Appender id
     ;; Appender definition (just a map):
     {:enabled?   true
      :async?     false
      :min-level  nil
      :rate-limit [[1 250] [10 5000]] ; 1/250ms, 10/5s
      :output-fn  :inherit
      :fn ; Appender's fn
      (fn [data]
        (let [{:keys [output-fn]} data
              formatted-output-str (output-fn data)]
          (println formatted-output-str)))}}})
```

A few things to note:
  * Appenders are _trivial_ to write & configure - **they're just fns**. It's Timbre's job to dispatch useful args to appenders when appropriate, it's their job to do something interesting with them.
  * Being 'just fns', appenders have basically limitless potential: write to your database, send a message over the network, check some other state (e.g. environment config) before making a choice, etc.

The **log level** may be set:
  * At compile-time: (`TIMBRE_LEVEL` environment variable).
  * Statically using: `timbre/set-level!`/`timbre/merge-level!`.
  * Dynamically using: `timbre/with-level`.

There are also variants of the logging utils that take explicit config args.

### Built-in appenders

#### Redis ([Carmine](https://github.com/ptaoussanis/carmine)) appender (v3+)

```clojure
;; [com.taoensso/carmine "2.10.0"] ; Add to project.clj deps
;; (:require [taoensso.timbre.appenders (carmine :as car-appender)]) ; Add to ns

(timbre/merge-config! {:appenders {:carmine (car-appender/make-appender)}})
```

This gives us a high-performance Redis appender:
  * **All raw logging args are preserved** in serialized form (**even errors!**).
  * Only the most recent instance of each **unique entry** is kept (hash fn used to determine uniqueness is configurable).
  * Configurable number of entries to keep per log level.
  * **Log is just a value**: a vector of Clojure maps: **query+manipulate with standard seq fns**: group-by hostname, sort/filter by ns & severity, explore exception stacktraces, filter by raw arguments, stick into or query with **Datomic**, etc.

A simple query utility is provided: `car-appender/query-entries`.

#### Email ([Postal](https://github.com/drewr/postal)) appender

```clojure
;; [com.draines/postal "1.11.3"] ; Add to project.clj deps
;; (:require [taoensso.timbre.appenders (postal :as postal-appender)]) ; Add to ns

(timbre/merge-config!
 {:appenders {:postal
   (postal-appender/make-appender {}
   {:postal-config
    ^{:host "mail.isp.net" :user "jsmith" :pass "sekrat!!1"}
    {:from "me@draines.com" :to "foo@example.com"}})}})
```

#### File appender

```clojure
(timbre/merge-config!
  {:appenders {:spit {:enabled? true :opts {:spit-finame "/path/my-file.log"}}}})
```

#### Other included appenders

A number of 3rd-party appenders are included out-the-box [here](https://github.com/ptaoussanis/timbre/tree/master/src/taoensso/timbre/appenders/3rd_party). **Please see the relevant docstring for details**. Thank you to the respective authors! Just give me a shout if you've got an appender you'd like to have added.

## Profiling (currently Clj only)

The usual recommendation for Clojure profiling is: use a good **JVM profiler** like [YourKit](http://www.yourkit.com/), [JProfiler](http://www.ej-technologies.com/products/jprofiler/overview.html), or [VisualVM](http://docs.oracle.com/javase/6/docs/technotes/guides/visualvm/index.html).

And these certainly do the job. But as with many Java tools, they can be a little hairy and often heavy-handed - especially when applied to Clojure. Timbre includes an alternative.

Wrap forms that you'd like to profile with the `p` macro and give them a name:

```clojure
(defn my-fn
  []
  (let [nums (vec (range 1000))]
    (+ (p :fast-sleep (Thread/sleep 1) 10)
       (p :slow-sleep (Thread/sleep 2) 32)
       (p :add  (reduce + nums))
       (p :sub  (reduce - nums))
       (p :mult (reduce * nums))
       (p :div  (reduce / nums)))))

(my-fn) => 42
```

The `profile` macro can now be used to log times for any wrapped forms:

```clojure
(profile :info :Arithmetic (dotimes [n 100] (my-fn))) => "Done!"
%> 2012-Jul-03 20:46:17 +0700 localhost INFO [my-app] - Profiling my-app/Arithmetic
              Name  Calls       Min        Max       MAD      Mean  Total% Total
 my-app/slow-sleep    100       2ms        2ms      31μs       2ms      57 231ms
 my-app/fast-sleep    100       1ms        1ms      27μs       1ms      29 118ms
        my-app/add    100      44μs        2ms      46μs     100μs       2 10ms
        my-app/sub    100      42μs      564μs      26μs      72μs       2 7ms
        my-app/div    100      54μs      191μs      17μs      71μs       2 7ms
       my-app/mult    100      31μs      165μs      11μs      44μs       1 4ms
       Unaccounted                                                       6 26ms
             Total                                                     100 405ms
```

You can also use the `defnp` macro to conveniently wrap whole fns.

Timbre profiling is fully **log level & ns filter aware**: if the level is insufficient or ns filtered, you **won't pay for profiling**.

And since `p` and `profile` **always return their body's result**, it becomes feasible to use profiling more often as part of your normal workflow: just *leave profiling code in production as you do logging code*.

A simple sampling profiler is also included.

## This project supports the CDS and ![ClojureWerkz](https://raw.github.com/clojurewerkz/clojurewerkz.org/master/assets/images/logos/clojurewerkz_long_h_50.png) goals

  * [CDS][], the **Clojure Documentation Site**, is a **contributer-friendly** community project aimed at producing top-notch, **beginner-friendly** Clojure tutorials and documentation. Awesome resource.

  * [ClojureWerkz][] is a growing collection of open-source, **batteries-included Clojure libraries** that emphasise modern targets, great documentation, and thorough testing. They've got a ton of great stuff, check 'em out!

## Contact & contributing

`lein start-dev` to get a (headless) development repl that you can connect to with [Cider][] (Emacs) or your IDE.

Please use the project's GitHub [issues page][] for project questions/comments/suggestions/whatever **(pull requests welcome!)**. Am very open to ideas if you have any!

Otherwise reach me (Peter Taoussanis) at [taoensso.com][] or on [Twitter][]. Cheers!

## License

Copyright &copy; 2012-2015 Peter Taoussanis. Distributed under the [Eclipse Public License][], the same as Clojure.


[API docs]: http://ptaoussanis.github.io/timbre/
[CHANGELOG]: https://github.com/ptaoussanis/timbre/releases
[other Clojure libs]: https://www.taoensso.com/clojure
[taoensso.com]: https://www.taoensso.com
[Twitter]: https://twitter.com/ptaoussanis
[issues page]: https://github.com/ptaoussanis/timbre/issues
[commit history]: https://github.com/ptaoussanis/timbre/commits/master
[Break Version]: https://github.com/ptaoussanis/encore/blob/master/BREAK-VERSIONING.md
[Leiningen]: http://leiningen.org/
[Cider]: https://github.com/clojure-emacs/cider
[CDS]: http://clojure-doc.org/
[ClojureWerkz]: http://clojurewerkz.org/
[Eclipse Public License]: https://raw2.github.com/ptaoussanis/timbre/master/LICENSE