> This project uses [Break Versioning](https://github.com/ptaoussanis/encore/blob/master/BREAK-VERSIONING.md) as of **Aug 16, 2014**.

## v4.0.0-beta3 / 2015 May 28

> This is a **MAJOR** update. Your custom appenders **WILL BREAK**. Your configuration **MIGHT BREAK**. Your call sites should be fine. I've updated all bundled appenders, but **haven't tested** any 3rd-party appenders.

* **New**: full **ClojureScript** support, including a default js/console appender [#51]
* **New**: support for compile-time ns filtering + elision (both Clj+Cljs)
* **New**: support for MDC-like contexts [#42]
* **New**: default :println appender has picked up a :stream opt [#49]
* **New**: create necessary spit appender paths [#93]
* **New**: full-power fn-level `log1-fn` util [#99]
* **New**: added a reference appender example [here](https://github.com/ptaoussanis/timbre/blob/master/src/taoensso/timbre/appenders/core.cljx)
* **Implementation**: modernized + simplified codebase
* **Implementation**: significant performance improvements across the board
* **Implementation**: use delays to avoid unnecessarily producing unused arg msgs [#71]
* **Fix**: auto shutdown agents to prevent slow app shutdown [#61]

```clojure
[com.taoensso/timbre "4.0.0-beta3"]
```

### Migration checklist

* Removed vars: `timbre/config`, `timbre/level-atom`, `default-fmt-output-fn`
* The fn signature for `set-config!` has changed: `[ks val]` -> `[config]`
* Middleware now apply left->right, not right->left
* Renamed a default appender: `:standard-out` -> `:println`
* Renamed config opts: `:timestamp-pattern`, `:timestamp-locale` -> `:timestamp-opts {:pattern _ :locale _ :timezone _}`
* Renamed config opts: `:whitelist` -> `:ns-whitelist`, `:blacklist` -> `:ns-blacklist`
* Appender :rate-limit format has changed: `[ncalls ms]` -> `[[ncalls ms] <...>]`
* Renamed appender args: `:ns`->`:?ns-str`, `:file`->`:?file`, `:line`->`:?line`
* Appender args now wrapped with delays: `:throwable`->`:?err_`, `:message`->`:msg_`, `:timestamp`->`:timestamp_`, `:hostname`->`:hostname_`, `:args`->`:vargs_`
* Appender args removed: `:output`, `:ap-config`
* Appender args added: `:output-fn (fn [data]) -> string`
* `stacktrace` util fn signature changed: `[throwable & [sep fonts]` -> `[err & [opts]]`
* All bundled 3rd-party appenders have moved to a new `3rd-party` ns.
* Bundled 3rd-party appender constructor signatures _may_ have changed, please double check.

Apologies for the hassle in migrating. The changes made here all bring serious benefits (performance, simplicity, future extensibility, cross-platform support) and I'm confident that v4's the last time I'll need to touch the core design. Future work will be focused on polish, stability, and better+more bundled appenders.

/ Peter Taoussanis

## v3.4.0 / 2015 Feb 16

 > This should be a **non-breaking** release that only bumps some old dependencies.


## v3.3.1 / 2014 Sep 7

 * **FIX** https://github.com/ptaoussanis/timbre/issues/79.


## v3.3.0 / 2014 May 8

 * **CHANGE**: Update IRC appender to Timbre v3 style (@crisptrutski).
 * **FIX** [#47]: correctly format nanosecond profiling times.
 * **FIX** [#77]: profile ids now use correct (compile-time rather than runtime) ns prefix.
 * **NEW**: Add zmq appender (@angusiguess).
 * **NEW** [#75]: Make defnp support multi-arity functions (@maurolopes)


## v3.2.1 / 2014 May 7

 * **FIX**: missing tools.reader upstream dependency (@ducky427).


## v3.2.0 / 2014 May 6

 * **FIX** [#60]: `defnp` no longer generates an Eastwood warning (@ducky427).
 * **CHANGE**: Improved profiling memory efficiency (max memory use, was previously unbounded).
 * **CHANGE**: Profiling: make larger call numbers easier to read.
 * [#63]: **NEW**: Add support for thread-local configuration (@jameswarren).


## v3.1.6 / 2014 Mar 16

 * **FIX** [#56]: `defnp`/`p` head retention issue (@kyptin).


## v3.1.5 / 2014 Mar 15

 * **FIX**: `profiling/p*` was defined incorrectly (@kyptin).


## v3.1.4 / 2014 Mar 13

 * **NEW**: Add `profiling/p*` macro.
 * **CHANGE**: Include `p`, `p*` in `refer-timbre` imports.
 * **FIX**: rotor appender not rotating (@iantruslove, @kurtharriger).


## v3.1.3 / 2014 Mar 11

 * FIX: profiling id namespacing.


## v3.1.1 / 2014 Feb 26

 * FIX: project.clj to prevent unnecessary downstream deps.


## v3.1.0 / 2014 Feb 23

### New

 * #47 Added `taoensso.timbre.profiling/pspy*` fn.

### Changes

 * Made Carmine appender resistant to unexpected log entry thaw errors.
 * Moved most utils to external `encore` dependency.

### Fixes

 * #50 Fixed rotor appender so that it respects :fmt-output-opts (kenrestivo).


## v3.0.0 / 2014 Jan 30

> Major update, non-breaking though users with custom appenders are encouraged to view the _Changes_ section below. This version polishes up the codebase and general design. Tightened up a few aspects of how appenders and appender middleware work. Added a serializing Carmine appender (I use something similar in prod most of the time). Also finally added facilities for ad hoc (non-atom) logging configuration.
>
> Overall quite happy with the state of Timbre as of this release. No major anticipated improvements/changes from here (modulo bugs).

### New

 * Android appender, courtesy of AdamClements.
 * Rolling appender, courtesy of megayu.
 * Powerful, high-performance Carmine (Redis) appender: query-able, rotating serialized log entries by log level. See README or appender's docstring for details. (Recommended!)
 * Appender rate limits now specified in a more flexible format: `[ncalls window-msecs]`, e.g. `[1 2000]` for 1 write / 2000 msecs.
 * Appender rate limits now also apply (at 1/4 ncalls) to any _particular_ logging arguments in the same time window. This helps prevent a particular logging call from flooding the limiter and preventing other calls from getting through.
 * `sometimes` macro that executes body with given probability. Useful for sampled logging (e.g. email a report for 0.01% of user logins in production).
 * `log` and `logf` macros now take an optional logging config map as their first argument: `(log :info "hello") => use @timbre/config`, `(log <config> :info "hello") => use <config>`.
 * Appenders can now specify an optional `:fmt-output-opts` that'll get passed to `fmt-output-fn` for any special formatting requirements they may have (e.g. the Postal email appender provides an arg to suppress ANSI colors in stacktrace output).

### Changes

 * **EXPERIMENTAL**: stacktraces now formatted with `io.aviso/pretty` rather than clj-stacktrace. Feedback on this (esp. coloring) welcome!
 * **DEPRECATED**: `red`, `green`, `blue` -> use `color-str` instead.
 * **DEPRECATED**: config `prefix-fn` has been replaced by the more flexible `fmt-output-fn`. Change is backwards compatible.
 * **REMOVED**: Per-appender `:prefix` option dropped - was unnecessary. If an appender wants custom output formatting, it can do so w/o using an in-config formatter.
 * Update `refer-timbre` (add profiling, logf variations, etc.).
 * **DEPRECATED**: atom logging level is now located in `level-atom` rather than `config`. Old in-config levels will be respected (i.e. change is backwards compatible).
 * **DEPRECATED**: appender rate limits are now specified as `:rate-limit [ncalls window-msecs]` rather than `:limit-per-msecs ncalls`. Change is backwards compatible.
 * Built-in appenders have been simplified using the new `default-output` appender arg.
 * Postal appender now generates a more useful subject in most cases.

### Fixes

 * #38 Broken namespace filter (mlb-).
 * Messages are now generated _after_ middleware has been applied, allowing better filtering performance and more intuitive behaviour (e.g. changes to args in middleware will now automatically percolate to message content).
 * `(logf <level> "hello %s")` was throwing due to lack of formatting args.


## v2.6.3 → v2.7.1
  * Core: `getHostName` no longer runs on the main thread for better Android compatibility (AdamClements).
  * Profiling: added `defnp` macro.
  * Profiling: fix compile-time name creation: now runtime (aperiodic).
  * Appenders: added rotating file appender (mopemope).


## v2.5.0 → v2.6.3
  * Perf: add support for a compile-time logging level environment variable (`TIMBRE_LOG_LEVEL`). See `timbre/compile-time-level` docstring for details.
  * Fix: `use-timbre`, `str-println` bugs.
  * Fix: Null Pointer Exception in clj-stacktrace (bitemyapp).


## v2.4.1 → v2.5.0
  * Added `:file` and `:line` appender args.
  * Fixed `make-timestamp-fn` thread safety.


## v2.3.4 → v2.4.1
  * Added `refer-timbre` for conveniently `require`ing standard timbre vars.
  * Postal appender now uses throwable as subject when no other args given.
  * `log-errors`, `log-and-rethrow-errors` now catch Throwable instead of Exception.


## v2.2.0 → v2.3.4
  * Added `with-log-level` for thread-local logging levels: `(with-level :trace (trace "This will log!"))`. Esp. useful for developing & unit tests, etc.


## v2.1.2 → v2.2.0
  * Add socket, MongoDB appenders (thanks to emlyn).


## v2.0.0 → v2.1.2
  * Added appenders: socket, IRC, MongoDB (CongoMongo). See [README](https://github.com/ptaoussanis/timbre#built-in-appenders) for details.
  * Add `ex-data` output to `stacktrace` fn.
  * Fixed a number of small bugs (mostly regressions from 1.x).


## v1.6.0 → v2.0.0
  * Refactor for integration with tools.logging.
  * **BREAKING**: Drop Clojure 1.3 support.
  * **DEPRECATED**: `:max-messages-per-msecs` appender arg -> `:limit-per-msecs`.

  * **BREAKING**: `:more` appender arg has been dropped. `:message` arg is now a string of all arguments as joined by `logp`/`logf`. Appenders that need unjoined logging arguments (i.e. raw arguments as given to `logp`/`logf`) should use the new `:log-args` vector.

  * **BREAKING**: Stacktraces are no longer automatically generated at the `log`-macro level. Stacktraces are now left as an appender implementation detail. A `:throwable` appender argument has been added along with a `stacktrace` fn.
