# Setup

Add the [relevant dependency](../#latest-releases) to your project:

```clojure
Leiningen: [com.taoensso/timbre               "x-y-z"] ; or
deps.edn:   com.taoensso/timbre {:mvn/version "x-y-z"}
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

> Or call `(timbre/refer-timbre)` to refer everything above automatically.

# Basic logging

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

# Architecture

Timbre's inherently a simple design, no magic. It's just **Clojure data and functions**.

Here's the flow for an `(info ...)` logging call:

1. Dynamic `*config*` is used as the current active config.
2. Is `:info` < the active minimum level? If so, end here and noop.
3. Is the current namespace filtered? If so, end here and noop.
4. Prepare a **log data map** of interesting [info](https://taoensso.github.io/timbre/taoensso.timbre.html#var-*config*) incl. all logging arguments.
5. Pass the data map through any **middleware fns**: `(fn [data]) -> ?data`. These may transform the data. If returned data is nil, end here and noop.
6. Pass the data map to all **appender fns**: `(fn [data]) -> ?effects`. These may print output, save the data to a DB, trigger admin alerts, etc.

# Configuration

Timbre's behaviour is controlled by the single dynamic [`*config*`](https://taoensso.github.io/timbre/taoensso.timbre.html#var-*config*) map. See its docstring for up-to-date usage info!

Its [default value](https://taoensso.github.io/timbre/taoensso.timbre.html#var-default-config) can be easily overridden by:

- A variety of provided utils ([`set-config!`](https://taoensso.github.io/timbre/taoensso.timbre.html#var-set-config.21), [`merge-config!`](https://taoensso.github.io/timbre/taoensso.timbre.html#var-merge-config.21), [`set-min-level!`](https://taoensso.github.io/timbre/taoensso.timbre.html#var-set-min-level.21), etc.)
- A JVM property, environment variable, or edn file on your resource path (see [`*config*` docstring](https://taoensso.github.io/timbre/taoensso.timbre.html#var-*config*) for details).

Sophisticated behaviour is achieved through normal fn composition, and the power of arbitrary Clojure fns: e.g. write to your database, send a message over the network, check some other state (e.g. environment config) before making a choice, etc.

## Minimum  level

A Timbre logging call will be disabled (noop) when the call's level (e.g. `(info ...)` is less than the active minimum level (e.g. `:warn`).

> Levels: `:trace` < `:debug` < `:info` < `:warn` < `:error` < `:fatal` < `:report`

- Call `(set-min-level! <min-level>)` to set the minimum level for **all** namespaces.
- Call `(set-ns-min-level! <min-level>)` to set the minimum level for the **current namespace** only.

## Namespace filtering

The `*config*` `:min-level` and `:ns-filter` values both support sophisticated pattern matching, e.g.:

- `:min-level`: `[[#{\"taoensso.*\"} :error] ... [#{\"*\"} :debug]]`.
- `:ns-filter`: `{:allow #{"*"} :deny #{"taoensso.*"}}`.

I.e. minimum levels can be specified _by namespace_ (pattern), and entire namespaces can be filtered.

Note that both `:min-level` and `:ns-filter` can also be easily overridden on a **per-appender** basis.

## Compile-time elision

By setting the [relevant](https://taoensso.github.io/timbre/taoensso.timbre.html#var-*config*) JVM properties or environment variables, Timbre can actually entirely exclude the code for disabled logging calls **at compile-time**, e.g.:

```bash
#!/bin/bash

# Elide all lower-level logging calls:
export TAOENSSO_TIMBRE_MIN_LEVEL_EDN=':warn'

# Elide all other ns logging calls:
export TAOENSSO_TIMBRE_NS_PATTERN_EDN='{:allow #{"my-app.*"} :deny #{"my-app.foo" "my-app.bar.*"}}'

lein cljsbuild once # Compile js with appropriate logging calls excluded
lein uberjar        # Compile jar ''
```

## Stacktrace fonts

ANSI fonts are enabled by default for Clojure stacktraces being printed to a console. To disable these, add the following entry to your top-level config or individual appender map/s:

```clojure
:output-opts {:stacktrace-fonts {}}
```

And/or you can set the:

- `taoensso.timbre.default-stacktrace-fonts.edn` JVM property, or
- `TAOENSSO_TIMBRE_DEFAULT_STACKTRACE_FONTS_EDN` environment variable.