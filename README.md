<a href="https://www.taoensso.com" title="More stuff by @ptaoussanis at www.taoensso.com">
<img src="https://www.taoensso.com/taoensso-open-source.png" alt="Taoensso open-source" width="400"/></a>

**[CHANGELOG]** | [API] | current [Break Version]:

```clojure
[com.taoensso/timbre "5.2.1"] ; See CHANGELOG for details
```

<!-- ![build status](https://github.com/ptaoussanis/timbre/workflows/build/badge.svg?branch=master) -->

> See [here](https://taoensso.com/clojure/backers) if you're interested in helping support my open-source work, thanks! - Peter Taoussanis

# Timbre: a pure Clojure/Script logging library

Java logging can be a Kafkaesque mess of complexity that buys you little. Getting even the simplest logging working can be comically hard, and it often gets worse at scale as your needs become more sophisticated.

Timbre offers an **all Clojure/Script** alternative that's fast, deeply flexible, easy to configure with pure Clojure data, and that **just works out the box**.  No XML.

Supports optional interop with [tools.logging](https://github.com/ptaoussanis/timbre/blob/master/src/taoensso/timbre/tools/logging.clj) and [log4j/logback/slf4j](https://github.com/fzakaria/slf4j-timbre).

Happy hacking!

## Features
 * Full **Clojure** + **ClojureScript** support (v4+).
 * No XML or properties files. **A single, simple config map**, and you're set.
 * Simple, flexible **fn appender model** with **middleware**.
 * **Great performance** at any scale.
 * Easily filter logging calls by **any combination** of: level, namespace, appender.
 * **Zero overhead** compile-time level/ns elision.
 * Useful built-in appenders for **out-the-box** Clj+Cljs logging.
 * Powerful, easy-to-configure **rate limits** and **async logging**.
 * [Logs as Clojure values][] (v3+).
 * Small, simple, cross-platform codebase.

## 3rd-party tools, appenders, etc.

Link                     | Description
------------------------ | -----------------------------------------------------
[@fzakaria/slf4j-timbre] | Route log4j/logback/sfl4j log output to Timbre
[@palletops/log-config]  | Library to help manage Timbre logging config
Your link here?          | **PR's welcome!**

## Getting started

Add the necessary dependency to your project:

```clojure
Leiningen: [com.taoensso/timbre "5.2.1"] ; or
deps.edn:   com.taoensso/timbre {:mvn/version "5.2.1"}
```

And setup your namespace imports:

```clojure
(ns my-clj-ns ; Clojure namespace
  (:require
    [taoensso.timbre :as timbre
      :refer [log  trace  debug  info  warn  error  fatal  report
              logf tracef debugf infof warnf errorf fatalf reportf
              spy get-env]]))

(ns my-cljs-ns ; ; ClojureScript namespace
  (:require
    [taoensso.timbre :as timbre
      :refer-macros [log  trace  debug  info  warn  error  fatal  report
                     logf tracef debugf infof warnf errorf fatalf reportf
                     spy get-env]]))
```

> You can also call `(timbre/refer-timbre)` to configure Clj ns referrals **automatically**.

### Logging

By default, Timbre gives you basic `println` and `js/console` (v4+) output at a `:debug` log level:

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

First-argument exceptions generate a nicely cleaned-up stack trace using [io.aviso.exception][] (Clj only):

```clojure
(info (Exception. "Oh noes") "arg1" "arg2")
%> 15-Jun-13 19:22:55 localhost INFO [my-app.core] - arg1 arg2
java.lang.Exception: Oh noes
<Stacktrace>
```

Other utils include: `log-errors`, `log-and-rethrow-errors`, `logged-future`, and `handle-uncaught-jvm-exceptions!` (please see the [API] for details).

### Data flow

Timbre's inherently a simple design, no magic. It's just Clojure data and functions:

 1. Enabled logging calls generate a **data map**: `{:level _ :?ns-str _ ...}`
 2. The resulting data map passes through any **middleware fns**, `(fn [data]) -> ?data`
 3. The resulting data map is sent to all **appender fns**, `(fn [data]) -> ?effects`

### Configuration

This is the biggest win over Java logging IMO. Timbre's behaviour is entirelly controlled through a single Clojure map fully documented in about 100 lines of docstring:

  - See Timbre's (v5) [config API][] for **full documentation**!
  - See Timbre's (v5) [default config][].

Sophisticated behaviour is easily achieved through regular fn composition and the power of arbitrary Clojure fns: e.g. write to your database, send a message over the network, check some other state (e.g. environment config) before making a choice, etc.

#### Log levels and ns filters: basics

Timbre logging calls will be disabled (noop) when:

  - The call's logging level (e.g. `:info`) is < the active `:min-level` (e.g. `:warn`).
  - The call is within a namespace not allowed by the current `:ns-filter` (e.g. `{:allow #{"*"} :deny #{"taoensso.*"}}`.

#### Log levels and ns filters: advanced

  - `:min-level` can also be a vector **mapping namespaces to minimum levels**, e.g.: `[[#{\"taoensso.*\"} :error] ... [#{\"*\"} :debug]]`.
  - Appenders can optionally have their own `:min-level`.

With all of the above, it's possible to easily enable/disable logging based on **any combination** of: 

  - Logging call level
  - Namespace
  - Appender

#### Log levels and ns filters: compile-time elision

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

### Disabling stacktrace colors

ANSI colors are enabled by default for stacktraces. To turn these off (e.g. for log files or emails), you can add the following entry to your top-level config **or** individual appender map/s:

```clojure
:output-fn (partial timbre/default-output-fn {:stacktrace-fonts {}})
```

And/or you can set the `TAOENSSO_TIMBRE_DEFAULT_STACKTRACE_FONTS_EDN` environment variable (supports edn).

### Built-in appenders

#### Basic file appender

```clojure
;; (:require [taoensso.timbre.appenders.core :as appenders]) ; Add to ns

(timbre/merge-config!
  {:appenders {:spit (appenders/spit-appender {:fname "/path/my-file.log"})}})

;; (timbre/merge-config! {:appenders {:spit {:enabled? false}}} ; To disable
;; (timbre/merge-config! {:appenders {:spit nil}}               ; To remove entirely
```

#### Redis ([Carmine]) appender (v3+)

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

Clojure has a rich selection of built-in and 3rd party tools for querying values like this.

See also `car-appender/query-entries`.

#### Email ([Postal]) appender

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

#### Other included appenders

A number of 3rd-party appenders are included out-the-box [here](https://github.com/ptaoussanis/timbre/tree/master/src/taoensso/timbre/appenders/3rd_party). **Please see the relevant docstring for details**. Thanks goes to the respective authors! 

Just give me a shout if you've got an appender you'd like to have added.

## This project supports the ![ClojureWerkz-logo] goals

[ClojureWerkz] is a growing collection of open-source, **batteries-included Clojure libraries** that emphasise modern targets, great documentation, and thorough testing.

## Contacting me / contributions

Please use the project's [GitHub issues page] for all questions, ideas, etc. **Pull requests welcome**. See the project's [GitHub contributors page] for a list of contributors.

Otherwise, you can reach me at [Taoensso.com]. Happy hacking!

\- [Peter Taoussanis]

## License

Distributed under the [EPL v1.0] \(same as Clojure).  
Copyright &copy; 2015-2020 [Peter Taoussanis].

<!--- Standard links -->
[Taoensso.com]: https://www.taoensso.com
[Peter Taoussanis]: https://www.taoensso.com
[@ptaoussanis]: https://www.taoensso.com
[More by @ptaoussanis]: https://www.taoensso.com
[Break Version]: https://github.com/ptaoussanis/encore/blob/master/BREAK-VERSIONING.md

<!--- Standard links (repo specific) -->
[CHANGELOG]: https://github.com/ptaoussanis/timbre/releases
[API]: http://ptaoussanis.github.io/timbre/
[GitHub issues page]: https://github.com/ptaoussanis/timbre/issues
[GitHub contributors page]: https://github.com/ptaoussanis/timbre/graphs/contributors
[EPL v1.0]: https://raw.githubusercontent.com/ptaoussanis/timbre/master/LICENSE
[Hero]: https://raw.githubusercontent.com/ptaoussanis/timbre/master/hero.png "Title"

<!--- Unique links -->
[logging profiler]: #profiling
[Logs as Clojure values]: #redis-carmine-appender-v3
[@palletops/log-config]: https://github.com/palletops/log-config
[@fzakaria/slf4j-timbre]: https://github.com/fzakaria/slf4j-timbre
[io.aviso.exception]: https://github.com/AvisoNovate/pretty
[Carmine]: https://github.com/ptaoussanis/carmine
[Tufte]: https://github.com/ptaoussanis/tufte
[Postal]: https://github.com/drewr/postal
[ClojureWerkz-logo]: https://raw.github.com/clojurewerkz/clojurewerkz.org/master/assets/images/logos/clojurewerkz_long_h_50.png
[ClojureWerkz]: http://clojurewerkz.org/
[config API]: http://ptaoussanis.github.io/timbre/taoensso.timbre.html#var-*config*
[default config]: http://ptaoussanis.github.io/timbre/taoensso.timbre.html#var-default-config
