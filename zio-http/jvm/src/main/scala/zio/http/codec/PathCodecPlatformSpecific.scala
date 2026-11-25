package zio.http.codec

/**
 * Number parsing helpers that [[PathCodec]] uses to decode an `int` or `long`
 * value inside a path segment that holds more than one value, such as
 * `user-{id}`. On the JVM they call `java.lang.Integer.parseInt` and
 * `java.lang.Long.parseLong` and are public; on Scala.js they are written in
 * Scala and are `private[codec]`.
 */
trait PathCodecPlatformSpecific {

  /**
   * Parses the characters of `s` from `beginIndex` (inclusive) to `endIndex`
   * (exclusive) as a `Long` in base `radix`, with an optional leading `+` or
   * `-`. Throws a `java.lang.NumberFormatException` when those characters are
   * not a valid number.
   */
  def parseLong(s: CharSequence, beginIndex: Int, endIndex: Int, radix: Int): Long =
    java.lang.Long.parseLong(s.subSequence(beginIndex, endIndex).toString, radix)

  /**
   * Parses the characters of `s` from `beginIndex` (inclusive) to `endIndex`
   * (exclusive) as an `Int` in base `radix`, with an optional leading `+` or
   * `-`. Throws a `java.lang.NumberFormatException` when those characters are
   * not a valid number.
   */
  def parseInt(s: CharSequence, beginIndex: Int, endIndex: Int, radix: Int): Int =
    java.lang.Integer.parseInt(s.subSequence(beginIndex, endIndex).toString, radix)
}
