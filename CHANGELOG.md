This project uses [**Break Versioning**](https://www.taoensso.com/break-versioning).

---

# `v6.6.0` (2024-10-28)

- **Main dependency**: [on Clojars](https://clojars.org/com.taoensso/timbre/versions/6.6.0)
- **SLF4J provider**: [on Clojars](https://clojars.org/com.taoensso/timbre-slf4j/versions/6.6.0)
- **Versioning**: [Break Versioning](https://www.taoensso.com/break-versioning)

Same as `v6.6.0-RC1` (2024-08-30), just updates some dependencies.

This is a significant **feature** release that includes new **built-in support** for [Java logging interop via SLF4J](https://github.com/taoensso/timbre/wiki/4-Interop#java-logging).

Big thanks to @fzakaria and @rufoa for their long-time work on Timbre's [previous optional SLF4J interop](https://github.com/fzakaria/slf4j-timbre) 🙏

As always, feedback and bug reports very welcome! - [Peter Taoussanis](https://www.taoensso.com)

## Changes since `v6.5.0` (2024-02-26)

* \[mod] `default-output-fn`: omit "?" location info [6d7495a7]

## New since `v6.5.0` (2024-02-26)

* \[new] Add SLF4Jv2 backend/provider [6b4873ec]
* \[new] [#389] Capture cause of failing error-fn [95ea032d]
* \[doc] [#386] Add `timbre-json-appender` to wiki (@NoahTheDuke) [0fa226eb]
* Various internal improvements and updated dependencies

## Fixes since `v6.5.0` (2024-02-26)

* None

---

# `v6.6.0-RC1` (2024-08-30)

- **Main dependency**: [on Clojars](https://clojars.org/com.taoensso/timbre/versions/6.6.0-RC1)
- **SLF4J provider**: [on Clojars](https://clojars.org/com.taoensso/timbre-slf4j/versions/6.6.0-RC1)
- **Versioning**: [Break Versioning](https://www.taoensso.com/break-versioning)

This is a significant **feature** release that includes new **built-in support** for [Java logging interop via SLF4J](https://github.com/taoensso/timbre/wiki/4-Interop#java-logging).

Big thanks to @fzakaria and @rufoa for their long-time work on Timbre's [previous optional SLF4J interop](https://github.com/fzakaria/slf4j-timbre) 🙏

As always, feedback and bug reports very welcome! - [Peter Taoussanis](https://www.taoensso.com)

## Changes since `v6.5.0` (2024-02-26)

* \[mod] `default-output-fn`: omit "?" location info [6d7495a7]

## New since `v6.5.0` (2024-02-26)

* \[new] Add SLF4Jv2 backend/provider [6b4873ec]
* \[new] [#389] Capture cause of failing error-fn [95ea032d]
* \[doc] [#386] Add `timbre-json-appender` to wiki (@NoahTheDuke) [0fa226eb]
* Various internal improvements and updated dependencies

## Fixes since `v6.5.0` (2024-02-26)

* None

---

# `v6.5.0` (2024-02-26)

> 📦 [Available on Clojars](https://clojars.org/com.taoensso/timbre/versions/6.5.0), this project uses [Break Versioning](https://www.taoensso.com/break-versioning).

This is a **maintenance release** that should be non-breaking, but **does** change the default `:min-level` when none is specified.

## Changes since `v6.4.0`

* 6b165c61 [mod] Change default top-level `*config*` :min-level (when none specified) from :report -> :trace

## Fixes since `v6.4.0`

* 3d730f9c [fix] [#381] Handle possible invalid stacktrace fonts

## New since `v6.4.0`

* f3ce2b5c [new] [#374] Add OpenTelemetry Protocol (OTLP) community appender (@devurandom)
* Update dependencies, misc internal improvements

---

# `v6.4.0` (2024-02-22)

> 📦 [Available on Clojars](https://clojars.org/com.taoensso/timbre/versions/6.4.0), this project uses [Break Versioning](https://www.taoensso.com/break-versioning).

This is a **maintenance release** that should be non-breaking, but that **may change** (fix) logging output for users of the JS console logger's `:raw-console?` option.

## Fixes since `v6.3.1`

* 9ec4e3c4 [fix] JS console appender unintentionally duplicating raw args
* dbf84818 [fix] Unnecessary boxed math in (community) rotor appender
* fab7b26c [fix] [#380] Fix docstring typo (@alexpetrov)

---

# `v6.3.1` (2023-09-27)

> 📦 [Available on Clojars](https://clojars.org/com.taoensso/timbre/versions/6.3.1), this project uses [Break Versioning](https://www.taoensso.com/break-versioning).

This is a **minor maintenance release** that should be non-breaking.

## New since `v6.2.2`

* 11734272 [new] Add callsite `:?column` to logging data
* 423b1c57 [new] Allow `refer-timbre` to work in Cljs
* Updated documentation, moved to new [wiki](https://github.com/taoensso/timbre/wiki)

## Other improvements since `v6.2.2`

* Some internal refactoring
* Update dependencies

---

# `v6.2.2` (2023-07-18)

> 📦 [Available on Clojars](https://clojars.org/com.taoensso/timbre/versions/6.2.2), this project uses [Break Versioning](https://www.taoensso.com/break-versioning).

Identical to `v6.2.1`, but synchronizes Encore dependency with my recent library releases (Timbre, Tufte, Sente, Carmine, etc.) to prevent confusion caused by dependency conflicts.

This is a safe update for users of `v6.2.1`.

---

# `v6.2.1` (2023-06-30)

> 📦 [Available on Clojars](https://clojars.org/com.taoensso/timbre/versions/6.2.1), this project uses [Break Versioning](https://www.taoensso.com/break-versioning).

This is a **maintenance release** that should be non-breaking.

## Fixes since `v6.1.0`

* cd8f04c1 [fix] [#369] Temporarily switch back to old Pretty release
* 5c189454 [fix] [#370] Remove `println` output on init load (@helins)
* e34629e6 [fix] [#365] Provide protection against faulty error-fn

## New since `v6.1.0`

* 0c5e07e7 [new] [#373] [#372] Cljs console appender: improve controls for raw logging
* a0bc5e04 [new] [#370] Add `:_init-config` map to `*config*`

---

# `v6.1.0` (2023-02-27)

```clojure
[com.taoensso/timbre "6.1.0"]
```

> This is a **maintenance release** that should be non-breaking for most users. 

See [recommended steps](https://github.com/taoensso/encore#recommended-steps-after-any-significant-dependency-update) when updating Clojure/Script dependencies.


## Changes since `v6.0.4`

- edd4ee76 [mod] Remove support for long-deprecated `:?err_`, `:vargs_` delays in log data
- 95bce09c [mod] Postal appender: switch to new output-opts API

## Fixes since `v6.0.4`

- 9455cb09 [fix] 1-arg Cljs `set-ns-min-level!` broken

## New since `v6.0.4`

- 09c64dc0 [new] Postal appender: add `:body-len` opt
- 3a9dd291 [new] [#361] [#362] Add Graal test (@borkdude)

---

# v6.0.4 / 2022 Dec 8

```clojure
[com.taoensso/timbre "6.0.4"]
```

## Fixes since `v6.0.0`

- [fix] [#359] Restore missing community appenders to the Timbre jar
- [fix] [#360] Fix broken compatibility with GraalVM (@borkdude)
- [fix] [#364] Update bundled Encore dependency to fix compilation issue with shadow-cljs

---

# v6.0.0 / 2022 Oct 28

```clojure
[com.taoensso/timbre "6.0.0"]
```

> This is a **major feature release**. Changes may be BREAKING for some users, see relevant commits referenced below for details.  
> Please test before use in production and report any problems, thanks!  
> See [here](https://github.com/taoensso/encore#recommended-steps-after-any-significant-dependency-update) for recommended steps when updating any Clojure/Script dependencies.

## Changes since `v5.2.1`

- 1c9fbb4f [mod] [BREAKING] [#322 #353] Reorganise community appenders
- 12457d9e [mod] [BREAKING] Default (nil) :min-level changed from `:report` -> `:trace`
- 65c3b473 [mod] [DEPRECATED] `:msg_` is now undocumented
- 98deeb73 [mod] [DEPRECATE] `set-level!` -> `set-min-level!`, `with-level` -> `with-min-level`
- 597c7a06 [mod] [#356] Call `pr-str` on non-string arguments
- 844943eb [mod] [#355 #339] Improve formatting of errors in Cljs (@aiba @DerGuteMoritz)
- 18bf001e [nop] Update core dependencies
- e5851f77 [nop] Update community dependencies
- [nop] Misc refactoring, incl. documentation improvements

## New since `v5.2.1`

- 2823c471 [new] [#332] Add ability to load initial Timbre config from edn system value or resource
- 9085a416 [new] [#328] Add new utils: `set-min-level!`, `set-ns-min-level!`, etc.
- 841a064a [new] [#356] Add `:msg-fn` option to `default-output-fn`
- 39a5e5a0 [new] [#317] Add `:output-error-fn` option to `default-output-fn`
- 6af3eda0 [new] [#217] Add alpha `shutdown-appenders!` util and hook
- 1024373b [new] [#354] Make `callsite-id` in `log!` macro deterministic for Clojure (@DerGuteMoritz)
- baaf1387 [new] Add `:output-opts` support to top-level and appender config
- 8d1b3a6e [new] Wrap output and msg fns for better error messages

---

# Earlier releases

See [here](https://github.com/taoensso/timbre/releases) for earlier releases.
