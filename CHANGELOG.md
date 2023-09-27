This project uses [**Break Versioning**](https://www.taoensso.com/break-versioning).

---

# `v6.3.0` (2023-09-27)

> 📦 [Available on Clojars](https://clojars.org/com.taoensso/timbre/versions/6.3.0), this project uses [Break Versioning](https://www.taoensso.com/break-versioning).

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

- 9455cb09 [fix] 1-arg Cljs `set-min-ns-level!` broken

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