<a href="https://www.taoensso.com/clojure" title="More stuff by @ptaoussanis at www.taoensso.com"><img src="https://www.taoensso.com/open-source.png" alt="Taoensso open source" width="340"/></a>  
[**API**][codox] | [**Wiki**][GitHub wiki] | [Latest releases](#latest-releases) | [Slack channel][]

# Timbre

### Pure Clojure/Script logging library

Getting even the simplest Java logging working can be maddeningly complex, and it often gets worse at scale as your needs become more sophisticated.

Timbre offers an **all Clojure/Script** alternative that's fast, deeply flexible, easy to configure with pure Clojure data, and that **just works out the box**.

Supports optional interop with [tools.logging](../../wiki/4-Interop#toolslogging) and [Java logging via SLF4Jv2](../../wiki/4-Interop#java-logging).

## Library status

While I will continue to support Timbre as always, I'd recommend new users see [Telemere](https://www.taoensso.com/telemere) instead - which is essentially a **modern rewrite of Timbre**.

There's **zero pressure** for existing users of Timbre to migrate, though there are significant benefits - and migration is often [quick and easy](https://github.com/taoensso/telemere/wiki/5-Migrating#from-timbre). See [here](https://github.com/taoensso/telemere/wiki/6-FAQ#why-not-just-update-timbre) for why I made the decision to release a new library.

\- Peter Taoussanis

## Latest release/s

- `2025-04-15` `v6.7.0`: [release info](../../releases/tag/v6.6.2)

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
- API reference: [Codox][]

## Funding

You can [help support][sponsor] continued work on this project, thank you!! 🙏

## License

Copyright &copy; 2014-2025 [Peter Taoussanis][].  
Licensed under [EPL 1.0](LICENSE.txt) (same as Clojure).

<!-- Common -->

[GitHub releases]: ../../releases
[GitHub issues]:   ../../issues
[GitHub wiki]:     ../../wiki
[Slack channel]: https://www.taoensso.com/timbre/slack

[Peter Taoussanis]: https://www.taoensso.com
[sponsor]:          https://www.taoensso.com/sponsor

<!-- Project -->

[Codox]:  https://taoensso.github.io/timbre/
[cljdoc]: https://cljdoc.org/d/com.taoensso/timbre/CURRENT/api/taoensso.timbre

[Clojars SVG]: https://img.shields.io/clojars/v/com.taoensso/timbre.svg
[Clojars URL]: https://clojars.org/com.taoensso/timbre

[Main tests SVG]:  https://github.com/taoensso/timbre/actions/workflows/main-tests.yml/badge.svg
[Main tests URL]:  https://github.com/taoensso/timbre/actions/workflows/main-tests.yml
[Graal tests SVG]: https://github.com/taoensso/timbre/actions/workflows/graal-tests.yml/badge.svg
[Graal tests URL]: https://github.com/taoensso/timbre/actions/workflows/graal-tests.yml
