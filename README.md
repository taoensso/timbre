<a href="https://www.taoensso.com/clojure" title="More stuff by @ptaoussanis at www.taoensso.com"><img src="https://www.taoensso.com/open-source.png" alt="Taoensso open source" width="340"/></a>  
[**Documentation**](#documentation) | [Latest releases](#latest-releases) | [Get support][GitHub issues]

# Timbre

### Pure Clojure/Script logging library

Getting even the simplest Java logging working can be maddeningly complex, and it often gets worse at scale as your needs become more sophisticated.

Timbre offers an **all Clojure/Script** alternative that's fast, deeply flexible, easy to configure with pure Clojure data, and that **just works out the box**.

Supports optional interop with [tools.logging](https://github.com/taoensso/timbre/blob/master/src/taoensso/timbre/tools/logging.clj) and [log4j/logback/slf4j](https://github.com/fzakaria/slf4j-timbre).

## Latest release/s

- `2024-02-22` `v6.4.0` (stable): [changes](../../releases/tag/v6.4.0)

[![Main tests][Main tests SVG]][Main tests URL]
[![Graal tests][Graal tests SVG]][Graal tests URL]

See [here][GitHub releases] for earlier releases.

## Why Timbre?

- Full **Clojure** & **ClojureScript** support, with built-in appenders for both
- **A single, simple config map**, and you're set. No need for XML or properties files
- Simple `(fn [data]) -> ?effects` appenders, and `(fn [data]) -> ?data` middleware
- Easily save **raw logging arguments** to the DB of your choice
- Easily filter logging calls by **any combination** of: level, namespace, appender
- **Zero overhead** compile-time level/ns elision
- Powerful, easy-to-configure **rate limits** and **async logging**
- **Great performance** and flexibility at any scale
- Small, simple, cross-platform pure-Clojure codebase

## Documentation

- [Wiki][GitHub wiki] (getting started, usage, etc.)
- API reference: [Codox][Codox docs], [clj-doc][clj-doc docs]

## Funding

You can [help support][sponsor] continued work on this project, thank you!! 🙏

## License

Copyright &copy; 2014-2024 [Peter Taoussanis][].  
Licensed under [EPL 1.0](LICENSE.txt) (same as Clojure).

<!-- Common -->

[GitHub releases]: ../../releases
[GitHub issues]:   ../../issues
[GitHub wiki]:     ../../wiki

[Peter Taoussanis]: https://www.taoensso.com
[sponsor]:          https://www.taoensso.com/sponsor

<!-- Project -->

[Codox docs]:   https://taoensso.github.io/timbre/
[clj-doc docs]: https://cljdoc.org/d/com.taoensso/timbre/

[Clojars SVG]: https://img.shields.io/clojars/v/com.taoensso/timbre.svg
[Clojars URL]: https://clojars.org/com.taoensso/timbre

[Main tests SVG]:  https://github.com/taoensso/timbre/actions/workflows/main-tests.yml/badge.svg
[Main tests URL]:  https://github.com/taoensso/timbre/actions/workflows/main-tests.yml
[Graal tests SVG]: https://github.com/taoensso/timbre/actions/workflows/graal-tests.yml/badge.svg
[Graal tests URL]: https://github.com/taoensso/timbre/actions/workflows/graal-tests.yml
