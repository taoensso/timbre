# Timbre, a logging library for Clojure

Logging with Java can be maddeningly, unnecessarily hard. Particularly if all you want is something *simple that works out the box*.

[tools.logging](https://github.com/clojure/tools.logging) helps, but it doesn't save you from the mess of logger dependencies and configuration hell.

Timbre is an attempt to make **simple logging simple** and more **complex logging possible**.

## What's In The Box?
 * Small, uncomplicated **all-Clojure** library.
 * **Map-based config**: no arcane XML or properties files.
 * Decent performance (**low overhead**).
 * Flexible **fn-centric appender model**.
 * Sensible built-in appenders including simple **email appender**.
 * Tunable **flood control**.
 * **Asynchronous** logging support.

## Status

Timbre was built in a day after I finally lost my patience trying to configure Log4j. I tried to keep the design simple and sensible, but I didn't spend much time thinking about it so there may still be room for improvement. In particular, **the configuration and appender formats are still subject to change**.

## Getting Started

### Leiningen

Depend on `[timbre "0.5.0-SNAPSHOT"]` in your `project.clj` and `use` the library:

```clojure
(ns my-app
  (:use [timbre.core :as timbre :only (trace debug info warn error fatal spy)])
```

### Start Logging

TODO: Out-the-box std-out examples

### Configuration

TODO: Config format (assoc) & appenders. Recommend just viewing source for now.

## Contact & Contribution

Reach me (Peter Taoussanis) at *p.taoussanis at gmail.com* for questions/comments/suggestions/whatever. I'm very open to ideas if you have any! Seriously: try me ;)

## License

Distributed under the [Eclipse Public License](http://www.eclipse.org/legal/epl-v10.html), the same as Clojure.