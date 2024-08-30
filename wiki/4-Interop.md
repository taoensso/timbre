# tools.logging

[tools.logging](https://github.com/clojure/tools.logging) can use Timbre as its logging implementation (backend). This'll let tools.logging calls trigger Timbre logging calls.

To do this:

1. Ensure that you have the tools.logging [dependency](https://mvnrepository.com/artifact/org.clojure/tools.logging), and
2. Require the `taoensso.timbre.tools.logging` namespace
3. Call [`taoensso.timbre.tools.logging/use-timbre`](https://taoensso.github.io/timbre/taoensso.timbre.tools.logging.html#var-use-timbre)

# Java logging

[SLF4Jv2](https://www.slf4j.org/) can use Timbre as its logging backend. This'll let SLF4J logging calls trigger Timbre logging calls.

To do this:

1. Ensure that you have the SLF4J [dependency](https://mvnrepository.com/artifact/org.slf4j/slf4j-api) ( v2+ **only**), and
2. Ensure that you have the Timbre SLF4J backend [dependency](https://clojars.org/com.taoensso/timbre-slf4j)

When `com.taoensso/timbre-slf4j` (2) is on your classpath AND no other SLF4J backends are, SLF4J will automatically direct all its logging calls to Timbre.

> Timbre needs SLF4J API **version 2 or newer**. If you're seeing `Failed to load class "org.slf4j.impl.StaticLoggerBinder"` it could be that your project is importing the older v1 API, check with `lein deps :tree` or equivalent.

For other (non-SLF4J) logging like [Log4j](https://logging.apache.org/log4j/2.x/), [java.util.logging](https://docs.oracle.com/javase/8/docs/api/java/util/logging/package-summary.html) (JUL), and [Apache Commons Logging](https://commons.apache.org/proper/commons-logging/) (JCL), use an appropriate [SLF4J bridge](https://www.slf4j.org/legacy.html) and the normal SLF4J config as above.

In this case logging will be forwarded:

1. From Log4j/JUL/JCL/etc. to SLF4J, and
2. From SLF4J to Timbre