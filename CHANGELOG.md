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


## For older versions please see the [commit history][]

[commit history]: https://github.com/ptaoussanis/timbre/commits/master
[API docs]: http://ptaoussanis.github.io/timbre
[Taoensso libs]: https://www.taoensso.com/clojure-libraries
[Nippy GitHub]: https://github.com/ptaoussanis/nippy
[Nippy CHANGELOG]: https://github.com/ptaoussanis/carmine/blob/master/CHANGELOG.md
[Nippy API docs]: http://ptaoussanis.github.io/nippy
