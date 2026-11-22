---
sidebar_position: 4
id: source-location
title: "Source Location"
---
import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';

## Source Location

:::info
The source location is available since logger-f `2.15.0`.
:::

logger-f adds the source location of the log call to the log message. The compiler gets the location where you call the log message constructor (`debug`, `info`, `warn`, `error` and the other ones). So the location is the one in your code, not one in logger-f.

e.g.)

```scala
// in com.example.Foo.bar at line 42 of Foo.scala
log(fa)(a => info(s"Hello $a"))
```

logs

```
Hello 1 at com.example.Foo.bar(Foo.scala:42)
```

The location has this shape.

```
<enclosing class>.<enclosing method>(<file name>:<line>)
```

It is the same shape as a frame in a JVM stack trace. logger-f keeps only the file name, not the path. So the compiled class files do not change when you compile the same source in a different directory.

You do not have to change your code to get the source location. All the existing `CanLog` factory methods add it with the default format.


## What Gets the Source Location

These message constructors get the source location.

* `debug`, `info`, `warn` and `error`
* `debugA`, `infoA`, `warnA` and `errorA`
* The ones with a `Throwable`, e.g. `error(throwable)` and `errorA(throwable)`

`log`, `log_`, `logS` and `logS_` send the source location to `CanLog`. This is true for `F[A]`, `F[Option[A]]`, `F[Either[A, B]]`, `OptionT[F, A]` and `EitherT[F, A, B]`.

`ignore` and `ignoreA` log nothing. So they have no source location.


### Example

`Greeter.scala`

```scala
package com.example

import cats._
import cats.syntax.all._
import effectie.core._
import effectie.syntax.all._
import loggerf.core._
import loggerf.syntax.all._

object Greeter {

  def greet[F[_]: Fx: Log: Monad](name: String): F[String] =
    for {
      _        <- logS_(s"The name is $name")(info)
      greeting <- pureOf(s"Hello $name").log(message => debug(s"Greeting: $message"))
      _        <- log_(pureOf(greeting.length))(length => warn(s"Length: ${length.toString}"))
      _        <- pureOf(greeting).log(infoA)
    } yield greeting

}
```

`MyApp.scala`

```scala
package com.example

import cats.effect._
import effectie.instances.ce2.fx._
import loggerf.logger._

object MyApp extends IOApp.Simple {

  implicit val canLog: CanLog = Slf4JLogger.slf4JCanLog("MyApp")

  def run: IO[Unit] =
    Greeter.greet[IO]("Kevin").void

}
```

It logs

```
01:14:27.449 [ioapp-compute-0] INFO MyApp - The name is Kevin at com.example.Greeter.greet(Greeter.scala:14)
01:14:27.452 [ioapp-compute-0] DEBUG MyApp - Greeting: Hello Kevin at com.example.Greeter.greet(Greeter.scala:15)
01:14:27.452 [ioapp-compute-0] WARN MyApp - Length: 11 at com.example.Greeter.greet(Greeter.scala:16)
01:14:27.453 [ioapp-compute-0] INFO MyApp - Hello Kevin at com.example.Greeter.greet(Greeter.scala:17)
```

The other examples on this page use the same `Greeter`.


### `F[Option[A]]`, `F[Either[A, B]]` and `Throwable`

Each branch gets the location of its own message constructor.

`Calculator.scala`

```scala
package com.example

import cats.syntax.all._
import effectie.core._
import effectie.syntax.all._
import loggerf.core._
import loggerf.syntax.all._

object Calculator {

  def parse[F[_]: Fx: Log](input: String): F[Option[Int]] =
    effectOf(scala.util.Try(input.toInt).toOption).log(
      warn(s"Not a number: $input"),
      n => info(s"Parsed: ${n.toString}"),
    )

  def divide[F[_]: Fx: Log](a: Int, b: Int): F[Either[Throwable, Int]] =
    effectOf(Either.catchNonFatal(a / b)).log(
      err => error(err)(s"Failed to divide ${a.toString} by ${b.toString}"),
      result => info(s"Result: ${result.toString}"),
    )

}
```

```scala
implicit val canLog: CanLog = Slf4JLogger.slf4JCanLog("MyApp")

Calculator.parse[IO]("123").unsafeRunSync() // Some(123)
Calculator.parse[IO]("abc").unsafeRunSync() // None
Calculator.divide[IO](10, 2).unsafeRunSync() // Right(5)
Calculator.divide[IO](1, 0).unsafeRunSync() // Left(java.lang.ArithmeticException: / by zero)
```

It logs

```
01:14:34.875 [main] INFO MyApp - Parsed: 123 at com.example.Calculator.parse(Calculator.scala:14)
01:14:34.879 [main] WARN MyApp - Not a number: abc at com.example.Calculator.parse(Calculator.scala:13)
01:14:34.881 [main] INFO MyApp - Result: 5 at com.example.Calculator.divide(Calculator.scala:20)
01:14:34.884 [main] ERROR MyApp - Failed to divide 1 by 0 at com.example.Calculator.divide(Calculator.scala:19)
java.lang.ArithmeticException: / by zero
	at com.example.Calculator$.$anonfun$divide$2(Calculator.scala:18)
	...
```

With a `Throwable`, the backend logs the `Throwable` as it did before. logger-f adds the source location only to the message.


### Enclosing Class and Method

The enclosing class is the class, trait or object which has the log call. The enclosing method is the method which has the log call. A lambda or an anonymous class does not count. If the log call is in the body of a class or an object and not in a method, the method is `<init>`.

```scala
package com.example

import loggerf.SourceLocation

class Where {
  val inClassBody: SourceLocation = implicitly[SourceLocation]
  // com.example.Where.<init>(Where.scala:6)

  def inMethod: SourceLocation = implicitly[SourceLocation]
  // com.example.Where.inMethod(Where.scala:9)

  def inLambda: List[SourceLocation] = List(1).map(_ => implicitly[SourceLocation])
  // List(com.example.Where.inLambda(Where.scala:12))

  object Inner {
    def inNestedObject: SourceLocation = implicitly[SourceLocation]
    // com.example.Where.Inner.inNestedObject(Where.scala:16)
  }
}
```


## `SourceLocationFormat`

`loggerf.logger.SourceLocationFormat` tells where the source location goes and what text goes around it.

The rendered location is `prefix + location + suffix`. It goes before or after the message. logger-f adds no other text. So an empty `prefix` or `suffix` adds nothing.

| Format                                                   | Log message                                   |
|----------------------------------------------------------|-----------------------------------------------|
| `SourceLocationFormat.default`                           | `Hello at com.example.Foo.bar(Foo.scala:42)`  |
| `SourceLocationFormat.afterMessage(" at ", "")`          | `Hello at com.example.Foo.bar(Foo.scala:42)`  |
| `SourceLocationFormat.beforeMessage("[", "] ")`          | `[com.example.Foo.bar(Foo.scala:42)] Hello`   |
| `SourceLocationFormat.afterMessage(" (", ")")`           | `Hello (com.example.Foo.bar(Foo.scala:42))`   |
| `SourceLocationFormat.afterMessage(" \| ", "")`          | `Hello \| com.example.Foo.bar(Foo.scala:42)`  |
| `SourceLocationFormat.beforeMessage("", ": ")`           | `com.example.Foo.bar(Foo.scala:42): Hello`    |
| `SourceLocationFormat.beforeMessage("", "")`             | `com.example.Foo.bar(Foo.scala:42)Hello`      |
| `SourceLocationFormat.afterMessage("", "")`              | `Hellocom.example.Foo.bar(Foo.scala:42)`      |

If you do not set a format, logger-f uses `SourceLocationFormat.default`. It is the same as `SourceLocationFormat.afterMessage(" at ", "")`.

```scala mdoc:reset-object
import loggerf.SourceLocation
import loggerf.logger.SourceLocationFormat

val location = SourceLocation("com.example.Foo", "bar", "Foo.scala", 42)

location.render

SourceLocationFormat.default.render("Hello", location)

SourceLocationFormat.beforeMessage("[", "] ").render("Hello", location)

SourceLocationFormat.afterMessage(" (", ")").render("Hello", location)

SourceLocationFormat.beforeMessage("", "").render("Hello", location)

SourceLocationFormat.afterMessage("", "").render("Hello", location)
```

You can also use the case class constructor.

```scala
SourceLocationFormat(SourceLocationFormat.Position.BeforeMessage, "<", "> ")
// <com.example.Foo.bar(Foo.scala:42)> Hello
```


### `beforeMessage`

```scala
implicit val canLog: CanLog =
  Slf4JLogger.slf4JCanLogWithSourceLocationFormat("MyApp", SourceLocationFormat.beforeMessage("[", "] "))

Greeter.greet[IO]("Kevin").unsafeRunSync()
```

```
01:14:28.487 [main] INFO MyApp - [com.example.Greeter.greet(Greeter.scala:14)] The name is Kevin
01:14:28.488 [main] DEBUG MyApp - [com.example.Greeter.greet(Greeter.scala:15)] Greeting: Hello Kevin
01:14:28.488 [main] WARN MyApp - [com.example.Greeter.greet(Greeter.scala:16)] Length: 11
01:14:28.488 [main] INFO MyApp - [com.example.Greeter.greet(Greeter.scala:17)] Hello Kevin
```

With no prefix and `": "` as the suffix,

```scala
implicit val canLog: CanLog =
  Slf4JLogger.slf4JCanLogWithSourceLocationFormat("MyApp", SourceLocationFormat.beforeMessage("", ": "))
```

```
01:14:29.265 [main] INFO MyApp - com.example.Greeter.greet(Greeter.scala:14): The name is Kevin
01:14:29.265 [main] DEBUG MyApp - com.example.Greeter.greet(Greeter.scala:15): Greeting: Hello Kevin
01:14:29.265 [main] WARN MyApp - com.example.Greeter.greet(Greeter.scala:16): Length: 11
01:14:29.265 [main] INFO MyApp - com.example.Greeter.greet(Greeter.scala:17): Hello Kevin
```


### `afterMessage`

```scala
implicit val canLog: CanLog =
  Slf4JLogger.slf4JCanLogWithSourceLocationFormat("MyApp", SourceLocationFormat.afterMessage(" (", ")"))

Greeter.greet[IO]("Kevin").unsafeRunSync()
```

```
01:14:28.749 [main] INFO MyApp - The name is Kevin (com.example.Greeter.greet(Greeter.scala:14))
01:14:28.749 [main] DEBUG MyApp - Greeting: Hello Kevin (com.example.Greeter.greet(Greeter.scala:15))
01:14:28.749 [main] WARN MyApp - Length: 11 (com.example.Greeter.greet(Greeter.scala:16))
01:14:28.749 [main] INFO MyApp - Hello Kevin (com.example.Greeter.greet(Greeter.scala:17))
```

With `" | "` as the prefix and no suffix,

```scala
implicit val canLog: CanLog =
  Slf4JLogger.slf4JCanLogWithSourceLocationFormat("MyApp", SourceLocationFormat.afterMessage(" | ", ""))
```

```
01:14:29.006 [main] INFO MyApp - The name is Kevin | com.example.Greeter.greet(Greeter.scala:14)
01:14:29.006 [main] DEBUG MyApp - Greeting: Hello Kevin | com.example.Greeter.greet(Greeter.scala:15)
01:14:29.006 [main] WARN MyApp - Length: 11 | com.example.Greeter.greet(Greeter.scala:16)
01:14:29.006 [main] INFO MyApp - Hello Kevin | com.example.Greeter.greet(Greeter.scala:17)
```


### Empty Prefix and Suffix

logger-f adds no space when the prefix and the suffix are empty.

```scala
implicit val canLog: CanLog =
  Slf4JLogger.slf4JCanLogWithSourceLocationFormat("MyApp", SourceLocationFormat.beforeMessage("", ""))
```

```
01:14:29.520 [main] INFO MyApp - com.example.Greeter.greet(Greeter.scala:14)The name is Kevin
01:14:29.521 [main] DEBUG MyApp - com.example.Greeter.greet(Greeter.scala:15)Greeting: Hello Kevin
01:14:29.521 [main] WARN MyApp - com.example.Greeter.greet(Greeter.scala:16)Length: 11
01:14:29.521 [main] INFO MyApp - com.example.Greeter.greet(Greeter.scala:17)Hello Kevin
```

```scala
implicit val canLog: CanLog =
  Slf4JLogger.slf4JCanLogWithSourceLocationFormat("MyApp", SourceLocationFormat.afterMessage("", ""))
```

```
01:14:29.778 [main] INFO MyApp - The name is Kevincom.example.Greeter.greet(Greeter.scala:14)
01:14:29.778 [main] DEBUG MyApp - Greeting: Hello Kevincom.example.Greeter.greet(Greeter.scala:15)
01:14:29.778 [main] WARN MyApp - Length: 11com.example.Greeter.greet(Greeter.scala:16)
01:14:29.779 [main] INFO MyApp - Hello Kevincom.example.Greeter.greet(Greeter.scala:17)
```


## Set the Format with Each Backend

The slf4j, log4s and log4j backends have factory methods which take a `SourceLocationFormat`. For sbt logging, please check the sbt Logging tab. The existing factory methods still work with no change. They use `SourceLocationFormat.default`.

<Tabs
  groupId="source-location-backends"
  defaultValue="slf4j"
  values={[
    {label: 'Slf4J', value: 'slf4j'},
    {label: 'Log4s', value: 'log4s'},
    {label: 'Log4j', value: 'log4j'},
    {label: 'sbt Logging', value: 'sbt-logging'},
  ]}>
  <TabItem value="slf4j">

Get `logger-f-slf4j`.

```scala mdoc:compile-only
import loggerf.logger._

object MyApp {
  val sourceLocationFormat: SourceLocationFormat = SourceLocationFormat.beforeMessage("[", "] ")

  // The default format
  val canLog1: CanLog = Slf4JLogger.slf4JCanLog("MyApp")

  // With a SourceLocationFormat
  val canLog2: CanLog = Slf4JLogger.slf4JCanLogWithSourceLocationFormat[MyApp.type](sourceLocationFormat)

  val canLog3: CanLog = Slf4JLogger.slf4JCanLogWithSourceLocationFormat("MyApp", sourceLocationFormat)

  val canLog4: CanLog = Slf4JLogger.slf4JCanLogWithLoggerAndSourceLocationFormat(
    org.slf4j.LoggerFactory.getLogger("MyApp"),
    sourceLocationFormat,
  )
}
```

`Slf4JLogger.slf4JCanLog("MyApp")` logs

```
01:14:27.709 [main] INFO MyApp - The name is Kevin at com.example.Greeter.greet(Greeter.scala:14)
01:14:27.710 [main] DEBUG MyApp - Greeting: Hello Kevin at com.example.Greeter.greet(Greeter.scala:15)
01:14:27.710 [main] WARN MyApp - Length: 11 at com.example.Greeter.greet(Greeter.scala:16)
01:14:27.710 [main] INFO MyApp - Hello Kevin at com.example.Greeter.greet(Greeter.scala:17)
```

`canLog2`, `canLog3` and `canLog4` log

```
01:14:30.559 [main] INFO MyApp - [com.example.Greeter.greet(Greeter.scala:14)] The name is Kevin
01:14:30.560 [main] DEBUG MyApp - [com.example.Greeter.greet(Greeter.scala:15)] Greeting: Hello Kevin
01:14:30.560 [main] WARN MyApp - [com.example.Greeter.greet(Greeter.scala:16)] Length: 11
01:14:30.560 [main] INFO MyApp - [com.example.Greeter.greet(Greeter.scala:17)] Hello Kevin
```

`canLog2` uses `com.example.MyApp$` as the logger name because it gets the logger with the class of `MyApp.type`.

  </TabItem>

  <TabItem value="log4s">

Get `logger-f-log4s`.

```scala mdoc:compile-only
import loggerf.logger._

object MyApp {
  val sourceLocationFormat: SourceLocationFormat = SourceLocationFormat.beforeMessage("[", "] ")

  // The default format
  val canLog1: CanLog = Log4sLogger.log4sCanLog("MyApp")

  // With a SourceLocationFormat
  val canLog2: CanLog = Log4sLogger.log4sCanLogWithSourceLocationFormat[MyApp.type](sourceLocationFormat)

  val canLog3: CanLog = Log4sLogger.log4sCanLogWithSourceLocationFormat("MyApp", sourceLocationFormat)

  val canLog4: CanLog = Log4sLogger.log4sCanLogWithLoggerAndSourceLocationFormat(
    org.log4s.getLogger("MyApp"),
    sourceLocationFormat,
  )
}
```

`Log4sLogger.log4sCanLog("MyApp")` logs

```
01:14:31.338 [main] INFO MyApp - The name is Kevin at com.example.Greeter.greet(Greeter.scala:14)
01:14:31.338 [main] DEBUG MyApp - Greeting: Hello Kevin at com.example.Greeter.greet(Greeter.scala:15)
01:14:31.338 [main] WARN MyApp - Length: 11 at com.example.Greeter.greet(Greeter.scala:16)
01:14:31.338 [main] INFO MyApp - Hello Kevin at com.example.Greeter.greet(Greeter.scala:17)
```

`canLog3` and `canLog4` log

```
01:14:31.599 [main] INFO MyApp - [com.example.Greeter.greet(Greeter.scala:14)] The name is Kevin
01:14:31.599 [main] DEBUG MyApp - [com.example.Greeter.greet(Greeter.scala:15)] Greeting: Hello Kevin
01:14:31.599 [main] WARN MyApp - [com.example.Greeter.greet(Greeter.scala:16)] Length: 11
01:14:31.599 [main] INFO MyApp - [com.example.Greeter.greet(Greeter.scala:17)] Hello Kevin
```

  </TabItem>

  <TabItem value="log4j">

Get `logger-f-log4j`.

```scala mdoc:compile-only
import loggerf.logger._

object MyApp {
  val sourceLocationFormat: SourceLocationFormat = SourceLocationFormat.afterMessage(" (", ")")

  // The default format
  val canLog1: CanLog = Log4jLogger.log4jCanLog("MyApp")

  // With a SourceLocationFormat
  val canLog2: CanLog = Log4jLogger.log4jCanLogWithSourceLocationFormat[MyApp.type](sourceLocationFormat)

  val canLog3: CanLog = Log4jLogger.log4jCanLogWithSourceLocationFormat("MyApp", sourceLocationFormat)

  val canLog4: CanLog = Log4jLogger.log4jCanLogWithLoggerAndSourceLocationFormat(
    org.apache.logging.log4j.LogManager.getLogger("MyApp"),
    sourceLocationFormat,
  )
}
```

`Log4jLogger.log4jCanLog("MyApp")` logs

```
01:14:32.519 [main] INFO MyApp - The name is Kevin at com.example.Greeter.greet(Greeter.scala:14)
01:14:32.520 [main] DEBUG MyApp - Greeting: Hello Kevin at com.example.Greeter.greet(Greeter.scala:15)
01:14:32.520 [main] WARN MyApp - Length: 11 at com.example.Greeter.greet(Greeter.scala:16)
01:14:32.520 [main] INFO MyApp - Hello Kevin at com.example.Greeter.greet(Greeter.scala:17)
```

`canLog3` and `canLog4` log

```
01:14:32.775 [main] INFO MyApp - The name is Kevin (com.example.Greeter.greet(Greeter.scala:14))
01:14:32.775 [main] DEBUG MyApp - Greeting: Hello Kevin (com.example.Greeter.greet(Greeter.scala:15))
01:14:32.775 [main] WARN MyApp - Length: 11 (com.example.Greeter.greet(Greeter.scala:16))
01:14:32.776 [main] INFO MyApp - Hello Kevin (com.example.Greeter.greet(Greeter.scala:17))
```

  </TabItem>

  <TabItem value="sbt-logging">

Get `logger-f-sbt-logging`.

`logger-f-sbt-logging` `@LOGGERF_SBT_LOGGING_VERSION@` uses `SourceLocationFormat.default`. It has no factory method which takes a `SourceLocationFormat`. But it works with logger-f `@VERSION@`, so it adds the source location in the default format, and `withoutSourceLocation` works with it.

```scala
import loggerf.logger._

implicit val logger: sbt.util.Logger = streams.value.log

implicit val canLog: CanLog = SbtLogger.sbtLoggerCanLog
```

It logs

```
[info] The name is Kevin at com.example.Greeter.greet(Greeter.scala:14)
[debug] Greeting: Hello Kevin at com.example.Greeter.greet(Greeter.scala:15)
[warn] Length: 11 at com.example.Greeter.greet(Greeter.scala:16)
[info] Hello Kevin at com.example.Greeter.greet(Greeter.scala:17)
```

To use a different format, wrap the `CanLog` and override `sourceLocationFormat`. The default methods with `SourceLocation` use `sourceLocationFormat` of the wrapper. Then they call the methods with no `SourceLocation`, and these methods call the `underlying` `CanLog`.

```scala
import loggerf.logger._

final class FormattedCanLog(underlying: CanLog, override val sourceLocationFormat: SourceLocationFormat)
    extends CanLog {
  override def debug(message: => String): Unit                       = underlying.debug(message)
  override def debug(throwable: Throwable)(message: => String): Unit = underlying.debug(throwable)(message)
  override def info(message: => String): Unit                        = underlying.info(message)
  override def info(throwable: Throwable)(message: => String): Unit  = underlying.info(throwable)(message)
  override def warn(message: => String): Unit                        = underlying.warn(message)
  override def warn(throwable: Throwable)(message: => String): Unit  = underlying.warn(throwable)(message)
  override def error(message: => String): Unit                       = underlying.error(message)
  override def error(throwable: Throwable)(message: => String): Unit = underlying.error(throwable)(message)
}

implicit val canLog: CanLog =
  new FormattedCanLog(SbtLogger.sbtLoggerCanLog, SourceLocationFormat.beforeMessage("[", "] "))
```

It logs

```
[info] [com.example.Greeter.greet(Greeter.scala:14)] The name is Kevin
[debug] [com.example.Greeter.greet(Greeter.scala:15)] Greeting: Hello Kevin
[warn] [com.example.Greeter.greet(Greeter.scala:16)] Length: 11
[info] [com.example.Greeter.greet(Greeter.scala:17)] Hello Kevin
```

You can use `FormattedCanLog` with any `CanLog`.

  </TabItem>
</Tabs>


## Remove the Source Location

If you do not want the source location in the log message, use `withoutSourceLocation`.

```scala
import loggerf.logger._

implicit val canLog: CanLog = Slf4JLogger.slf4JCanLog("MyApp").withoutSourceLocation

Greeter.greet[IO]("Kevin").unsafeRunSync()
```

```
01:14:30.820 [main] INFO MyApp - The name is Kevin
01:14:30.820 [main] DEBUG MyApp - Greeting: Hello Kevin
01:14:30.820 [main] WARN MyApp - Length: 11
01:14:30.820 [main] INFO MyApp - Hello Kevin
```

`withoutSourceLocation` works with all the backends and with any `SourceLocationFormat`.

```scala
Slf4JLogger
  .slf4JCanLogWithSourceLocationFormat("MyApp", SourceLocationFormat.beforeMessage("[", "] "))
  .withoutSourceLocation

Log4sLogger.log4sCanLog("MyApp").withoutSourceLocation

Log4jLogger.log4jCanLog("MyApp").withoutSourceLocation

SbtLogger.sbtLoggerCanLog.withoutSourceLocation
```

e.g.) `SbtLogger.sbtLoggerCanLog.withoutSourceLocation` logs

```
[info] The name is Kevin
[debug] Greeting: Hello Kevin
[warn] Length: 11
[info] Hello Kevin
```

:::info
`LoggerFLogHandler` in `logger-f-doobie1` logs with no source location. The logs in doobie's `LogHandler` have no call site in your code.
:::


## `CanLog` Methods with `SourceLocation`

`CanLog` has methods with a `loggerf.SourceLocation` parameter.

```scala
def debug(sourceLocation: SourceLocation)(message: => String): Unit
def debug(throwable: Throwable, sourceLocation: SourceLocation)(message: => String): Unit
def info(sourceLocation: SourceLocation)(message: => String): Unit
def info(throwable: Throwable, sourceLocation: SourceLocation)(message: => String): Unit
def warn(sourceLocation: SourceLocation)(message: => String): Unit
def warn(throwable: Throwable, sourceLocation: SourceLocation)(message: => String): Unit
def error(sourceLocation: SourceLocation)(message: => String): Unit
def error(throwable: Throwable, sourceLocation: SourceLocation)(message: => String): Unit

def sourceLocationFormat: SourceLocationFormat = SourceLocationFormat.default
```

`log`, `log_`, `logS` and `logS_` call these methods. They have default implementations. A default implementation renders the message with `sourceLocationFormat` and calls the method with no `SourceLocation`. So a custom `CanLog` gets the source location with no change.

You can also call them directly. `implicitly[SourceLocation]` gets the location of the call site.

```scala
package com.example

import loggerf.SourceLocation
import loggerf.logger._

object Direct {

  def run(): Unit = {
    val canLog: CanLog = Slf4JLogger.slf4JCanLog("MyApp")

    canLog.info("Without the source location")
    canLog.info(implicitly[SourceLocation])("With the source location")
    canLog.error(new RuntimeException("Something failed"), implicitly[SourceLocation])("With a Throwable")
  }

}
```

```
01:16:02.157 [main] INFO MyApp - Without the source location
01:16:02.157 [main] INFO MyApp - With the source location at com.example.Direct.run(Direct.scala:12)
01:16:02.157 [main] ERROR MyApp - With a Throwable at com.example.Direct.run(Direct.scala:13)
java.lang.RuntimeException: Something failed
	at com.example.Direct$.run(Direct.scala:13)
	...
```


## Custom `CanLog`

### Override `sourceLocationFormat`

A custom `CanLog` can override `sourceLocationFormat` to set its own format.

```scala
package com.example

import loggerf.logger._

object ConsoleCanLog extends CanLog {

  override val sourceLocationFormat: SourceLocationFormat =
    SourceLocationFormat.beforeMessage("(", ") ")

  private def print(level: String, message: String): Unit =
    println(s"[$level] $message")

  private def print(level: String, message: String, throwable: Throwable): Unit =
    println(s"[$level] $message - ${throwable.toString}")

  override def debug(message: => String): Unit = print("DEBUG", message)

  override def debug(throwable: Throwable)(message: => String): Unit = print("DEBUG", message, throwable)

  override def info(message: => String): Unit = print("INFO", message)

  override def info(throwable: Throwable)(message: => String): Unit = print("INFO", message, throwable)

  override def warn(message: => String): Unit = print("WARN", message)

  override def warn(throwable: Throwable)(message: => String): Unit = print("WARN", message, throwable)

  override def error(message: => String): Unit = print("ERROR", message)

  override def error(throwable: Throwable)(message: => String): Unit = print("ERROR", message, throwable)

}
```

```scala
implicit val canLog: CanLog = ConsoleCanLog

Greeter.greet[IO]("Kevin").unsafeRunSync()
Calculator.divide[IO](1, 0).unsafeRunSync()
```

```
[INFO] (com.example.Greeter.greet(Greeter.scala:14)) The name is Kevin
[DEBUG] (com.example.Greeter.greet(Greeter.scala:15)) Greeting: Hello Kevin
[WARN] (com.example.Greeter.greet(Greeter.scala:16)) Length: 11
[INFO] (com.example.Greeter.greet(Greeter.scala:17)) Hello Kevin
[ERROR] (com.example.Calculator.divide(Calculator.scala:19)) Failed to divide 1 by 0 - java.lang.ArithmeticException: / by zero
```

### Override the Methods with `SourceLocation`

A custom `CanLog` can override the methods with `SourceLocation` to use the location in a different way. `SourceLocation` has these fields.

```scala
final case class SourceLocation(enclosingClass: String, enclosingMethod: String, fileName: String, line: Int)
```

This `CanLog` logs only the file name and the line number.

```scala
package com.example

import loggerf.SourceLocation
import loggerf.logger._

final class FileAndLineCanLog(underlying: CanLog) extends CanLog {

  private def render(sourceLocation: SourceLocation, message: String): String =
    s"[${sourceLocation.fileName}:${sourceLocation.line.toString}] $message"

  override def debug(message: => String): Unit = underlying.debug(message)

  override def debug(throwable: Throwable)(message: => String): Unit = underlying.debug(throwable)(message)

  override def info(message: => String): Unit = underlying.info(message)

  override def info(throwable: Throwable)(message: => String): Unit = underlying.info(throwable)(message)

  override def warn(message: => String): Unit = underlying.warn(message)

  override def warn(throwable: Throwable)(message: => String): Unit = underlying.warn(throwable)(message)

  override def error(message: => String): Unit = underlying.error(message)

  override def error(throwable: Throwable)(message: => String): Unit = underlying.error(throwable)(message)

  override def debug(sourceLocation: SourceLocation)(message: => String): Unit =
    underlying.debug(render(sourceLocation, message))

  override def debug(throwable: Throwable, sourceLocation: SourceLocation)(message: => String): Unit =
    underlying.debug(throwable)(render(sourceLocation, message))

  override def info(sourceLocation: SourceLocation)(message: => String): Unit =
    underlying.info(render(sourceLocation, message))

  override def info(throwable: Throwable, sourceLocation: SourceLocation)(message: => String): Unit =
    underlying.info(throwable)(render(sourceLocation, message))

  override def warn(sourceLocation: SourceLocation)(message: => String): Unit =
    underlying.warn(render(sourceLocation, message))

  override def warn(throwable: Throwable, sourceLocation: SourceLocation)(message: => String): Unit =
    underlying.warn(throwable)(render(sourceLocation, message))

  override def error(sourceLocation: SourceLocation)(message: => String): Unit =
    underlying.error(render(sourceLocation, message))

  override def error(throwable: Throwable, sourceLocation: SourceLocation)(message: => String): Unit =
    underlying.error(throwable)(render(sourceLocation, message))

}
```

```scala
implicit val canLog: CanLog = new FileAndLineCanLog(Slf4JLogger.slf4JCanLog("MyApp"))

Greeter.greet[IO]("Kevin").unsafeRunSync()
```

```
01:14:34.610 [main] INFO MyApp - [Greeter.scala:14] The name is Kevin
01:14:34.611 [main] DEBUG MyApp - [Greeter.scala:15] Greeting: Hello Kevin
01:14:34.611 [main] WARN MyApp - [Greeter.scala:16] Length: 11
01:14:34.611 [main] INFO MyApp - [Greeter.scala:17] Hello Kevin
```


## Test with `CanLog4Testing`

`CanLog4Testing` in `logger-f-test-kit` keeps the source locations in `sourceLocations: Vector[(Int, SourceLocation)]`. The index is the same as the index in `messages`.

`messages` has the message with no source location. So the existing tests do not change. `sourceLocations` is not part of the equality of `CanLog4Testing`.

```scala
"io.kevinlee" %% "logger-f-test-kit" % "@VERSION@" % Test
```

This test uses [Hedgehog](https://hedgehogqa.github.io/scala-hedgehog).

```scala
package com.example

import cats.effect._
import effectie.instances.ce2.fx._
import hedgehog._
import hedgehog.runner._
import loggerf.Level
import loggerf.testing.CanLog4Testing
import loggerf.testing.CanLog4Testing.OrderedMessages

object GreeterSpec extends Properties {

  override def tests: List[Test] = List(
    example("Greeter.greet logs the messages with the source locations", testGreet),
    example("Greeter.greet logs in Greeter.greet", testGreetWithoutLineNumbers),
  )

  def testGreet: Result = {
    implicit val canLog: CanLog4Testing = CanLog4Testing()

    val actual = Greeter.greet[IO]("Kevin").unsafeRunSync()

    Result.all(
      List(
        actual ==== "Hello Kevin",
        canLog.getOrderedMessages ==== OrderedMessages.withAutoIndex(
          (Level.info, "The name is Kevin"),
          (Level.debug, "Greeting: Hello Kevin"),
          (Level.warn, "Length: 11"),
          (Level.info, "Hello Kevin"),
        ),
        canLog.sourceLocations.map { case (index, location) => (index, location.render) } ==== Vector(
          (0, "com.example.Greeter.greet(Greeter.scala:14)"),
          (1, "com.example.Greeter.greet(Greeter.scala:15)"),
          (2, "com.example.Greeter.greet(Greeter.scala:16)"),
          (3, "com.example.Greeter.greet(Greeter.scala:17)"),
        ),
      )
    )
  }

  def testGreetWithoutLineNumbers: Result = {
    implicit val canLog: CanLog4Testing = CanLog4Testing()

    val _ = Greeter.greet[IO]("Kevin").unsafeRunSync()

    val actual = canLog.sourceLocations.map {
      case (index, location) =>
        (index, location.enclosingClass, location.enclosingMethod, location.fileName)
    }

    actual ==== Vector(
      (0, "com.example.Greeter", "greet", "Greeter.scala"),
      (1, "com.example.Greeter", "greet", "Greeter.scala"),
      (2, "com.example.Greeter", "greet", "Greeter.scala"),
      (3, "com.example.Greeter", "greet", "Greeter.scala"),
    )
  }

}
```

```
+ com.example.GreeterSpec.Greeter.greet logs the messages with the source locations: OK, passed 1 tests
+ com.example.GreeterSpec.Greeter.greet logs in Greeter.greet: OK, passed 1 tests
```

:::tip
A line number changes when you add or remove a line above the log call. If you want a test which does not break for that, check only `enclosingClass`, `enclosingMethod` and `fileName` as `testGreetWithoutLineNumbers` does.
:::


## `LeveledMessage`

`LeveledMessage` has a new field, `sourceLocation`. If your code creates or pattern matches `LeveledMessage` directly, you must change it.

```scala
final case class LeveledMessage(
  message: () => String,
  throwable: Option[Throwable],
  level: Level,
  sourceLocation: SourceLocation,
)
```
