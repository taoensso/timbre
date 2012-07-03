Current version:

```clojure
[com.taoensso/timbre "0.6.0"]
```

# Timbre, a (sane) logging library for Clojure

Logging with Java can be maddeningly, unnecessarily hard. Particularly if all you want is something *simple that works out the box*.

[tools.logging](https://github.com/clojure/tools.logging) helps, but it doesn't save you from the mess of logger dependencies and configuration hell.

Timbre is an attempt to make **simple logging simple** and more **complex logging possible**.

## What's In The Box?
 * Small, uncomplicated **all-Clojure** library.
 * **Super-simple map-based config**: no arcane XML or properties files!
 * Decent performance (**low overhead**).
 * Flexible **fn-centric appender model**.
 * Sensible built-in appenders including simple **email appender**.
 * Tunable **flood control** and **asynchronous** logging support.
 * Robust **namespace filtering**.
 * Dead-simple, logging-level-aware **logging profiler**.

## Status [![Build Status](https://secure.travis-ci.org/ptaoussanis/timbre.png?branch=master)](http://travis-ci.org/ptaoussanis/timbre)

Tower is still currently *experimental*. It **has not yet been thoroughly tested in production** and its API is subject to change. To run tests against all supported Clojure versions, use:

```bash
lein2 all test
```

## Getting Started

### Leiningen

Depend on Timbre in your `project.clj`:

```clojure
[com.taoensso/timbre "0.6.0"]
```

and `use` the library:

```clojure
(ns my-app
  (:use [taoensso.timbre :as timbre :only (trace debug info warn error fatal spy)]))
```

### Start Logging

By default, Timbre gives you basic print output to `*out*`/`*err*` at a `debug` logging level:

```clojure
(info "This will print")
=> nil
%> 2012-May-28 17:26:11:444 +0700 INFO [my-app] - This will print

(spy :info (* 5 4 3 2 1))
=> 120
%> 2012-May-28 17:26:14:138 +0700 INFO [my-app] - (* 5 4 3 2 1) 120

(trace "This won't print due to insufficient logging level")
=> nil
```

There's little overhead for checking logging levels:

```clojure
(time (trace (Thread/sleep 5000)))
%> "Elapsed time: 0.054 msecs"

(time (when true))
%> "Elapsed time: 0.051 msecs"
```

First-argument exceptions generate a stack trace:

```clojure
(info (Exception. "Oh noes") "arg1" "arg2")
%> 2012-May-28 17:35:16:132 +0700 INFO [my-app] - arg1 arg2
java.lang.Exception: Oh noes
            NO_SOURCE_FILE:1 my-app/eval6409
          Compiler.java:6511 clojure.lang.Compiler.eval
          <...>
```

### Configuration

Configuring Timbre couldn't be simpler. Let's check out (some of) the defaults:

```clojure
@timbre/config
=>
{:current-level :debug

 :ns-whitelist []
 :ns-blacklist []

 :appenders
 {:standard-out        { <...> }
  :postal              { <...> }}

 :shared-appender-config
 {:timestamp-pattern "yyyy-MMM-dd HH:mm:ss ZZ"
  :locale nil
  :postal nil}}
```

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

Filter logging output by namespaces:
```clojure
(timbre/set-config! [:ns-whitelist] ["some.library.core" "my-app.*"])
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

Writing a custom appender is dead-easy:

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
        (when-not (:my-production-mode? ap-config)
          (apply println timestamp "Hello world!" message more)))
```

And because appender fns are just regular Clojure fns, you have *unlimited power*: write to your database, send a message over the network, check some other state (e.g. environment config) before making a choice, etc.

See the `timbre/config` docstring for more information on appenders.

## Profiling

The usual recommendation for Clojure profiling is: use a good **JVM profiler** like [YourKit](http://www.yourkit.com/), [JProfiler](http://www.ej-technologies.com/products/jprofiler/overview.html), or [VisualVM](http://docs.oracle.com/javase/6/docs/technotes/guides/visualvm/index.html).

And these certaily do the job. But as with many Java tools, they can be a little hairy and often heavy-handed - especially when applied to Clojure. Timbre includes an alternative. 

Let's add it to our app's `ns` declaration:

```clojure
(ns my-app
  (:use [taoensso.timbre :as timbre :only (trace debug info warn error fatal spy)]
        [taoensso.timbre.profiling :as profiling :only (p profile)]))
```

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

(my-fn)
=> 42
```

The `profile` macro can now be used to log times for any wrapped forms:

```clojure
(profile :info :Arithmetic (dotimes [n 100] (my-fn)))
=> "Done!"
%> 2012-Jul-03 20:46:17 +0700 INFO [my-app] - Profiling: my-app/Arithmetic
              Name  Count       Min        Max      Mean  Total% Total
 my-app/slow-sleep    100       2ms        2ms       2ms      58 232ms
 my-app/fast-sleep    100       1ms        1ms       1ms      30 120ms
        my-app/div    100      65μs      204μs      90μs       2 9ms
        my-app/add    100      32μs      116μs      59μs       1 5ms
        my-app/sub    100      30μs      145μs      47μs       1 4ms
       my-app/mult    100      33μs      117μs      45μs       1 4ms
       Unaccounted                                             6 25ms
             Total                                           100 403ms
```

It's important to note that Timbre profiling is fully **log-level aware**: if the logging level is insufficient, you won't pay for profiling. Likewise, normal namespace filtering applies. (Performance characteristics for both checks are inherited from Timbre itself).

And since `p` and `profile` **always return their body's result** regardless of whether profiling actually happens or not, it becomes feasible to use profiling more often as part of your normal workflow: just *leave profiling code in production as you do for logging code*.

A **sampling profiler** is also available: `taoensso.timbre.profiling/sampling-profile`.

## Timbre Supports the ClojureWerkz Project Goals

ClojureWerkz is a growing collection of open-source, batteries-included [Clojure libraries](http://clojurewerkz.org/) that emphasise modern targets, great documentation, and thorough testing.

## Contact & Contribution

Reach me (Peter Taoussanis) at *ptaoussanis at gmail.com* for questions/comments/suggestions/whatever. I'm very open to ideas if you have any!

I'm also on Twitter: [@ptaoussanis](https://twitter.com/#!/ptaoussanis).

## License

Copyright &copy; 2012 Peter Taoussanis

Distributed under the [Eclipse Public License](http://www.eclipse.org/legal/epl-v10.html), the same as Clojure.