## v3.1.4 / 2014 Mar 13

 * NEW: Add `profiling/p*` macro.
 * CHANGE: Include `p`, `p*` in `refer-timbre` imports.
 * FIX: rotor appender not rotating (iantruslove, kurtharriger).


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
