<a href="https://www.taoensso.com" title="More stuff by @ptaoussanis at www.taoensso.com">
<img src="https://www.taoensso.com/taoensso-open-source.png" alt="Taoensso open-source" width="350"/></a>

**[CHANGELOG][]** | [API][] | current [Break Version][]:

```clojure
[com.taoensso/timbre "6.0.2"] ; May incl. breaking changes, see CHANGELOG for details
[com.taoensso/timbre "5.2.1"] ; Stable
```
> See [here][backers] if to help support my open-source work, thanks! - [Peter Taoussanis][Taoensso.com]

# Timbre: a pure Clojure/Script logging library

Getting even the simplest Java logging working can be maddeningly complex, and it often gets worse at scale as your needs become more sophisticated.

Timbre offers an **all Clojure/Script** alternative that's fast, deeply flexible, easy to configure with pure Clojure data, and that **just works out the box**.

Supports optional interop with [tools.logging](https://github.com/ptaoussanis/timbre/blob/master/src/taoensso/timbre/tools/logging.clj) and [log4j/logback/slf4j](https://github.com/fzakaria/slf4j-timbre).

Happy hacking!

## Features
 * Full **Clojure** & **ClojureScript** support, with built-in appenders for both.
 * **A single, simple config map**, and you're set. No need for XML or properties files.
 * Simple `(fn [data]) -> ?effects` appenders, and `(fn [data]) -> ?data` middleware.
 * Easily save **raw logging arguments** to the DB of your choice.
 * Easily filter logging calls by **any combination** of: level, namespace, appender.
 * **Zero overhead** compile-time level/ns elision.
 * Powerful, easy-to-configure **rate limits** and **async logging**.
 * **Great performance** and flexibility at any scale.
 * Small, simple, cross-platform pure-Clojure codebase.

## Quickstart

Add the necessary dependency to your project:

```clojure
Leiningen: [com.taoensso/timbre "6.0.2"] ; or
deps.edn:   com.taoensso/timbre {:mvn/version "6.0.2"}
```

And setup your namespace imports:

```clojure
(ns my-ns
  (:require
    [taoensso.timbre :as timbre
      ;; Optional, just refer what you like:
      :refer [log  trace  debug  info  warn  error  fatal  report
              logf tracef debugf infof warnf errorf fatalf reportf
              spy]]))
```

> You can also call `(timbre/refer-timbre)` (Clj only) to refer everything above automatically.

### Basic logging

By default, Timbre gives you basic `println` and `js/console` output for all logging calls of at least `:debug` log level:

```clojure
(info "This will print") => nil
%> 15-Jun-13 19:18:33 localhost INFO [my-app.core] - This will print

(spy :info (* 5 4 3 2 1)) => 120
%> 15-Jun-13 19:19:13 localhost INFO [my-app.core] - (* 5 4 3 2 1) => 120

(defn my-mult [x y] (info "Lexical env:" (get-env)) (* x y)) => #'my-mult
(my-mult 4 7) => 28
%> 15-Jun-13 19:21:53 localhost INFO [my-app.core] - Lexical env: {x 4, y 7}

(trace "This won't print due to insufficient log level") => nil
```

First-argument exceptions will also generate a stack trace:

```clojure
(info (Exception. "Oh no") "arg1" "arg2")
%> 15-Jun-13 19:22:55 localhost INFO [my-app.core] - arg1 arg2
java.lang.Exception: Oh no
<Stacktrace>
```

### Set the minimum logging level

A Timbre logging call will be disabled (noop) when the call's level (e.g. `(info ...)` is less than the active minimum level (e.g. `:warn`).

> Levels: `:trace` < `:debug` < `:info` < `:warn` < `:error` < `:fatal` < `:report`

- Call `(set-min-level! <min-level>)` to set the minimum level for **all** namespaces.
- Call `(set-ns-min-level! <min-level>)` to set the minimum level for the **current namespace** only.

> See the [config API][] for more.

## Architecture

Timbre's inherently a simple design, no magic. It's just **Clojure data and functions**.

Here's the flow for an `(info ...)` logging call:

1. Dynamic `*config*` is used as the current active [config][config API].
2. Is `:info` < the active minimum level? If so, end here and noop.
3. Is the current namespace filtered? If so, end here and noop.
4. Prepare a **log data map** of interesting [info][config API] incl. all logging arguments.
5. Pass the data map through any **middleware fns**: `(fn [data]) -> ?data`. These may transform the data. If returned data is nil, end here and noop.
6. Pass the data map to all **appender fns**: `(fn [data]) -> ?effects`. These may print output, save the data to a DB, trigger admin alerts, etc.

## Configuration

Timbre's behaviour is controlled by the single dynamic `*config*` map, fully documented [here][config API].

Its [default value][default config] can be easily overridden by:

- An [edn file][config API] on your resource path.
- A symbol defined by an [an environment variable][config API] or [JVM property][config API].
- A variety of [provided utils][config API].
- Standard Clojure utils (`binding`, `alter-var-root!`/`set!`).

Sophisticated behaviour is achieved through normal fn composition, and the power of arbitrary Clojure fns: e.g. write to your database, send a message over the network, check some other state (e.g. environment config) before making a choice, etc.

## Advanced minimum levels and namespace filtering

The `*config*` `:min-level` and `:ns-filter` values both support sophisticated pattern matching, e.g.:

- `:min-level`: `[[#{\"taoensso.*\"} :error] ... [#{\"*\"} :debug]]`.
- `:ns-filter`: `{:allow #{"*"} :deny #{"taoensso.*"}}`.

As usual, the full functionality is described by the [config API][].

Note that both `:min-level` and `:ns-filter` may also be easily overridden on a **per-appender** basis.

### Compile-time elision

By setting the [relevant][config API] JVM properties or environment variables, Timbre can actually entirely exclude the code for disabled logging calls **at compile-time**, e.g.:

```bash
#!/bin/bash

# Elide all lower-level logging calls:
export TAOENSSO_TIMBRE_MIN_LEVEL_EDN=':warn'

# Elide all other ns logging calls:
export TAOENSSO_TIMBRE_NS_PATTERN_EDN='{:allow #{"my-app.*"} :deny #{"my-app.foo" "my-app.bar.*"}}'

lein cljsbuild once # Compile js with appropriate logging calls excluded
lein uberjar        # Compile jar ''
```

## Disable stacktrace colors

ANSI colors are enabled by default for Clojure stacktraces. To turn these off (e.g. for log files or emails), you can add the following entry to your top-level config or individual appender map/s:

```clojure
:output-opts {:stacktrace-fonts {}}
```

And/or you can set the:

- `taoensso.timbre.default-stacktrace-fonts.edn` JVM property, or
- `TAOENSSO_TIMBRE_DEFAULT_STACKTRACE_FONTS_EDN` environment variable.

## Included appenders

### Basic file appender

```clojure
;; (:require [taoensso.timbre.appenders.core :as appenders]) ; Add to ns

(timbre/merge-config!
  {:appenders {:spit (appenders/spit-appender {:fname "/path/my-file.log"})}})

;; (timbre/merge-config! {:appenders {:spit {:enabled? false}}} ; To disable
;; (timbre/merge-config! {:appenders {:spit nil}}               ; To remove entirely
```

### [Carmine][] (Redis) appender

```clojure
;; [com.taoensso/carmine <latest-version>] ; Add to project.clj deps
;; (:require [taoensso.timbre.appenders (carmine :as car-appender)]) ; Add to ns

(timbre/merge-config! {:appenders {:carmine (car-appender/carmine-appender)}})
```

This gives us a high-performance Redis appender:

 * **All raw logging args are preserved** in serialized form (even errors).
 * Configurable number of entries to keep per log level.
 * Only the most recent instance of each **unique entry** is kept.
 * Resulting **log is just a Clojure value**: a vector of log entries (maps).

Clojure has a rich selection of built-in and community tools for querying values like this. 

See also `car-appender/query-entries`.

### [Postal][] (email) appender

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

### Community appenders

A number of community appenders are included out-the-box [here](https://github.com/ptaoussanis/timbre/tree/master/src/taoensso/timbre/appenders/community). These include appenders for Android, Logstash, Slack, Sentry, NodeJS, Syslog, PostgreSQL, etc.

Thanks goes to the respective authors!  
**Please see the relevant namespace docstring for details**.

GitHub PRs for new appenders and for appender maintenance very welcome!

## More community tools, appenders, etc.

Some externally-hosted items are listed here:

Link                       | Description
-------------------------- | -----------------------------------------------------
[@fzakaria/slf4j-timbre][] | Route log4j/logback/sfl4j log output to Timbre
[@palletops/log-config][]  | Library to help manage Timbre logging config
Your link here?            | **PR's welcome!**

## This project supports the ![ClojureWerkz-logo][] goals

[ClojureWerkz][] is a growing collection of open-source, **batteries-included Clojure libraries** that emphasise modern targets, great documentation, and thorough testing.

## Contacting me / contributions

Please use the project's [GitHub issues page][] for all questions, ideas, etc. **Pull requests welcome**. See the project's [GitHub contributors page][] for a list of contributors.

Otherwise, you can reach me at [Taoensso.com][]. Happy hacking!

\- [Peter Taoussanis][Taoensso.com]

## License

Distributed under the [EPL v1.0][] \(same as Clojure).  
Copyright &copy; 2015-2022 [Peter Taoussanis][Taoensso.com].

<!--- Standard links -->
[Taoensso.com]: https://www.taoensso.com
[Break Version]: https://github.com/ptaoussanis/encore/blob/master/BREAK-VERSIONING.md
[backers]: https://taoensso.com/clojure/backers

<!--- Standard links (repo specific) -->
[CHANGELOG]: https://github.com/ptaoussanis/timbre/releases
[API]: http://ptaoussanis.github.io/timbre/
[GitHub issues page]: https://github.com/ptaoussanis/timbre/issues
[GitHub contributors page]: https://github.com/ptaoussanis/timbre/graphs/contributors
[EPL v1.0]: https://raw.githubusercontent.com/ptaoussanis/timbre/master/LICENSE
[Hero]: https://raw.githubusercontent.com/ptaoussanis/timbre/master/hero.png "Title"

<!--- Unique links -->
[logging profiler]: #profiling
[@palletops/log-config]: https://github.com/palletops/log-config
[@fzakaria/slf4j-timbre]: https://github.com/fzakaria/slf4j-timbre
[Carmine]: https://github.com/ptaoussanis/carmine
[Tufte]: https://github.com/ptaoussanis/tufte
[Postal]: https://github.com/drewr/postal
[ClojureWerkz-logo]: https://raw.github.com/clojurewerkz/clojurewerkz.org/master/assets/images/logos/clojurewerkz_long_h_50.png
[ClojureWerkz]: http://clojurewerkz.org/
[config API]: http://ptaoussanis.github.io/timbre/taoensso.timbre.html#var-*config*
[default config]: http://ptaoussanis.github.io/timbre/taoensso.timbre.html#var-default-config
