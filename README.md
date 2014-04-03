**[API docs][]** | **[CHANGELOG][]** | [other Clojure libs][] | [Twitter][] | [contact/contributing](#contact--contributing) | current ([semantic][]) version:

```clojure
[com.taoensso/timbre "3.1.6"] ; Stable
```

v3 is a **major, backwards-compatible release**. Please see the [CHANGELOG][] for details. Appender authors: please see [here](https://github.com/ptaoussanis/timbre/issues/41) about migrating Timbre 2.x appenders to 3.x's recommended style.

# Timbre, a (sane) Clojure logging & profiling library

Logging with Java can be maddeningly, unnecessarily hard. Particularly if all you want is something *simple that works out-the-box*. Timbre brings functional, Clojure-y goodness to all your logging needs. **No XML!**

## What's in the box™?
 * [Logs as Clojure values](https://github.com/ptaoussanis/timbre/tree/dev#redis-carmine-appender-v3) (v3+).
 * Small, uncomplicated **all-Clojure** library.
 * **Super-simple map-based config**: no arcane XML or properties files!
 * **Low overhead** with dynamic logging level.
 * **No overhead** with compile-time logging level. (v2.6+)
 * Flexible **fn-centric appender model** with **middleware**.
 * Sensible built-in appenders including simple **email appender**.
 * Tunable **rate limit** and **asynchronous** logging support.
 * Robust **namespace filtering**.
 * [tools.logging](https://github.com/clojure/tools.logging) support (optional, useful when integrating with legacy logging systems).
 * Dead-simple, logging-level-aware **logging profiler**.

## 3rd-party tools, appenders, etc.
 * [log-config](https://github.com/palletops/log-config) by [Hugo Duncan](https://github.com/hugoduncan) - library to help manage Timbre logging config.
 * Suggestions welcome!

## Getting started

### Dependencies

Add the necessary dependency to your [Leiningen][] `project.clj` and use the supplied ns-import helper:

```clojure
[com.taoensso/timbre "3.1.6"] ; project.clj

(ns my-app (:require [taoensso.timbre :as timbre])) ; Your ns
(timbre/refer-timbre) ; Provides useful Timbre aliases in this ns
```

The `refer-timbre` call is a convenience fn that executes:
```clojure
(require '[taoensso.timbre :as timbre
           :refer (log  trace  debug  info  warn  error  fatal  report
                   logf tracef debugf infof warnf errorf fatalf reportf
                   spy logged-future with-log-level sometimes)])
(require '[taoensso.timbre.profiling :as profiling
           :refer (pspy pspy* profile defnp p p*)])
```

### Logging

By default, Timbre gives you basic print output to `*out*`/`*err*` at a `debug` logging level:

```clojure
(info "This will print") => nil
%> 2012-May-28 17:26:11:444 +0700 localhost INFO [my-app] - This will print

(spy :info (* 5 4 3 2 1)) => 120
%> 2012-May-28 17:26:14:138 +0700 localhost INFO [my-app] - (* 5 4 3 2 1) 120

(trace "This won't print due to insufficient logging level") => nil
```

First-argument exceptions generate a nicely cleaned-up stack trace using [io.aviso.exception](https://github.com/AvisoNovate/pretty):

```clojure
(info (Exception. "Oh noes") "arg1" "arg2")
%> 2012-May-28 17:35:16:132 +0700 localhost INFO [my-app] - arg1 arg2
java.lang.Exception: Oh noes
            NO_SOURCE_FILE:1 my-app/eval6409
          Compiler.java:6511 clojure.lang.Compiler.eval
          <...>
```

### Configuration

This is the biggest win over Java logging utilities IMO. Here's `timbre/example-config` (also Timbre's default config):

```clojure
(def example-config
  "APPENDERS
     An appender is a map with keys:
      :doc             ; (Optional) string.
      :min-level       ; (Optional) keyword, or nil (no minimum level).
      :enabled?        ; (Optional).
      :async?          ; (Optional) dispatch using agent (good for slow appenders).
      :rate-limit      ; (Optional) [ncalls-limit window-ms].
      :fmt-output-opts ; (Optional) extra opts passed to `fmt-output-fn`.
      :fn              ; (fn [appender-args-map]), with keys described below.

     An appender's fn takes a single map with keys:
      :level         ; Keyword.
      :error?        ; Is level an 'error' level?.
      :throwable     ; java.lang.Throwable.
      :args          ; Raw logging macro args (as given to `info`, etc.).
      :message       ; Stringified logging macro args, or nil.
      :output        ; Output of `fmt-output-fn`, used by built-in appenders
                     ; as final, formatted appender output. Appenders may (but
                     ; are not obligated to) use this as their output.
      :ap-config     ; Contents of config's :shared-appender-config key.
      :profile-stats ; From `profile` macro.
      :instant       ; java.util.Date.
      :timestamp     ; String generated from :timestamp-pattern, :timestamp-locale.
      :hostname      ; String.
      :ns            ; String.
      ;; Waiting on http://dev.clojure.org/jira/browse/CLJ-865:
      :file          ; String.
      :line          ; Integer.

   MIDDLEWARE
     Middleware are fns (applied right-to-left) that transform the map
     dispatched to appender fns. If any middleware returns nil, no dispatching
     will occur (i.e. the event will be filtered).

  The `example-config` code contains further settings and details.
  See also `set-config!`, `merge-config!`, `set-level!`."

  {;;; Control log filtering by namespace patterns (e.g. ["my-app.*"]).
   ;;; Useful for turning off logging in noisy libraries, etc.
   :ns-whitelist []
   :ns-blacklist []

   ;; Fns (applied right-to-left) to transform/filter appender fn args.
   ;; Useful for obfuscating credentials, pattern filtering, etc.
   :middleware []

   ;;; Control :timestamp format
   :timestamp-pattern "yyyy-MMM-dd HH:mm:ss ZZ" ; SimpleDateFormat pattern
   :timestamp-locale  nil ; A Locale object, or nil

   ;; Output formatter used by built-in appenders. Custom appenders may (but are
   ;; not required to use) its output (:output). Extra per-appender opts can be
   ;; supplied as an optional second (map) arg.
   :fmt-output-fn
   (fn [{:keys [level throwable message timestamp hostname ns]}
       ;; Any extra appender-specific opts:
       & [{:keys [nofonts?] :as appender-fmt-output-opts}]]
     ;; <timestamp> <hostname> <LEVEL> [<ns>] - <message> <throwable>
     (format "%s %s %s [%s] - %s%s"
       timestamp hostname (-> level name str/upper-case) ns (or message "")
       (or (stacktrace throwable "\n" (when nofonts? {})) "")))

   :shared-appender-config {} ; Provided to all appenders via :ap-config key
   :appenders
   {:standard-out
    {:doc "Prints to *out*/*err*. Enabled by default."
     :min-level nil :enabled? true :async? false :rate-limit nil
     :fn (fn [{:keys [error? output]}] ; Use any appender args
           (binding [*out* (if error? *err* *out*)]
             (str-println output)))}

    :spit
    {:doc "Spits to `(:spit-filename :shared-appender-config)` file."
     :min-level nil :enabled? false :async? false :rate-limit nil
     :fn (fn [{:keys [ap-config output]}] ; Use any appender args
           (when-let [filename (:spit-filename ap-config)]ar
             (try (spit filename output :append true)
                  (catch java.io.IOException _))))}}})
```

A few things to note:

 * Appenders are trivial to write & configure - **they're just fns**. It's Timbre's job to dispatch useful args to appenders when appropriate, it's their job to do something interesting with them.
 * Being 'just fns', appenders have basically limitless potential: write to your database, send a message over the network, check some other state (e.g. environment config) before making a choice, etc.

The **logging level** may be set:
 * At compile-time: (`TIMBRE_LOG_LEVEL` environment variable).
 * Via an atom: `(timbre/set-level! <level>)`. (Usual method).
 * Via dynamic thread-level binding: `(timbre/with-log-level <level> ...)`.

A compile-time level offers _zero-overhead_ performance since it'll cause insufficient logging calls to disappear completely at compile-time. Usually you won't need/want to bother: Timbre offers very decent performance with runtime level checks (~15msecs/10k checks on my Macbook Air).

For common-case ease-of-use, **all logging utils use a global atom for their config**. This is configurable with `timbre/set-config!`, `timbre/merge-config!`. The lower-level `log` and `logf` macros also take an optional first-arg config map for greater flexibility (e.g. **during testing**).

### Built-in appenders

#### Redis ([Carmine](https://github.com/ptaoussanis/carmine)) appender (v3+)

```clojure
;; [com.taoensso/carmine "2.4.0"] ; Add to project.clj deps
;; (:require [taoensso.timbre.appenders (:carmine :as car-appender)]) ; Add to ns

(timbre/set-config! [:appenders :carmine] (postal-appenders/make-carmine-appender))
```

This gives us a high-performance Redis appender:
 * **All raw logging args are preserved** in serialized form (**even Throwables!**).
 * Only the most recent instance of each **unique entry** is kept (hash fn used to determine uniqueness is configurable).
 * Configurable number of entries to keep per logging level.
 * **Log is just a value**: a vector of Clojure maps: **query+manipulate with standard seq fns**: group-by hostname, sort/filter by ns & severity, explore exception stacktraces, filter by raw arguments, etc.  **Datomic and `core.logic`** also offer interesting opportunities here.

A simple query utility is provided: `car-appender/query-entries`.

#### Email ([Postal](https://github.com/drewr/postal)) appender

```clojure
;; [com.draines/postal "1.9.2"] ; Add to project.clj deps
;; (:require [taoensso.timbre.appenders (postal :as postal-appender)]) ; Add to ns

(timbre/set-config! [:appenders :postal]
  (postal-appender/make-postal-appender
   {:enabled?   true
    :rate-limit [1 60000] ; 1 msg / 60,000 msecs (1 min)
    :async?     true ; Don't block waiting for email to send
   }
   {:postal-config
    ^{:host "mail.isp.net" :user "jsmith" :pass "sekrat!!1"}
    {:from "me@draines.com" :to "foo@example.com"}}))
```

#### File appender

```clojure
(timbre/set-config! [:appenders :spit :enabled?] true)
(timbre/set-config! [:shared-appender-config :spit-filename] "/path/my-file.log")
```

#### Other included appenders

A number of 3rd-party appenders are included out-the-box for: Android, IRC, sockets, MongoDB, and rotating files. These are all located in the `taoensso.timbre.appenders.x` namespaces - **please see the relevant docstrings for details**.

Thanks to their respective authors! Just give me a shout if you've got an appender you'd like to have added.

## Profiling

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

It's important to note that Timbre profiling is fully **logging-level aware**: if the  level is insufficient, you *won't pay for profiling* (there is a minimal dynamic-var deref cost). Likewise, normal namespace filtering applies. (Performance characteristics for both checks are inherited from Timbre itself).

And since `p` and `profile` **always return their body's result** regardless of whether profiling actually happens or not, it becomes feasible to use profiling more often as part of your normal workflow: just *leave profiling code in production as you do for logging code*.

A simple **sampling profiler** is also available: `taoensso.timbre.profiling/sampling-profile`.

## This project supports the CDS and ![ClojureWerkz](https://raw.github.com/clojurewerkz/clojurewerkz.org/master/assets/images/logos/clojurewerkz_long_h_50.png) goals

  * [CDS][], the **Clojure Documentation Site**, is a **contributer-friendly** community project aimed at producing top-notch, **beginner-friendly** Clojure tutorials and documentation. Awesome resource.

  * [ClojureWerkz][] is a growing collection of open-source, **batteries-included Clojure libraries** that emphasise modern targets, great documentation, and thorough testing. They've got a ton of great stuff, check 'em out!

## Contact & contributing

`lein start-dev` to get a (headless) development repl that you can connect to with [Cider][] (emacs) or your IDE.

Please use the project's GitHub [issues page][] for project questions/comments/suggestions/whatever **(pull requests welcome!)**. Am very open to ideas if you have any!

Otherwise reach me (Peter Taoussanis) at [taoensso.com][] or on [Twitter][]. Cheers!

## License

Copyright &copy; 2012-2014 Peter Taoussanis. Distributed under the [Eclipse Public License][], the same as Clojure.


[API docs]: <http://ptaoussanis.github.io/timbre/>
[CHANGELOG_]: <https://github.com/ptaoussanis/timbre/blob/master/CHANGELOG.md>
[CHANGELOG]: <https://github.com/ptaoussanis/timbre/releases>
[other Clojure libs]: <https://www.taoensso.com/clojure-libraries>
[Twitter]: <https://twitter.com/ptaoussanis>
[semantic]: <http://semver.org/>
[Leiningen]: <http://leiningen.org/>
[CDS]: <http://clojure-doc.org/>
[ClojureWerkz]: <http://clojurewerkz.org/>
[issues page]: <https://github.com/ptaoussanis/timbre/issues>
[Cider]: <https://github.com/clojure-emacs/cider>
[commit history]: <https://github.com/ptaoussanis/timbre/commits/master>
[taoensso.com]: <https://www.taoensso.com>
[Eclipse Public License]: <https://raw2.github.com/ptaoussanis/timbre/master/LICENSE>
