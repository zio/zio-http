package zio.http.codec

import java.util.Objects

trait PathCodecPlatformSpecific {
  private[codec] def parseLong(s: CharSequence, beginIndex: Int, endIndex: Int, radix: Int): Long = {
    Objects.requireNonNull(s)
    Objects.checkFromToIndex(beginIndex, endIndex, s.length)
    if (radix < Character.MIN_RADIX)
      throw new NumberFormatException("radix " + radix + " less than Character.MIN_RADIX")
    if (radix > Character.MAX_RADIX)
      throw new NumberFormatException("radix " + radix + " greater than Character.MAX_RADIX")
    var negative = false
    var i        = beginIndex
    var limit    = -Long.MaxValue
    if (i < endIndex) {
      val firstChar = s.charAt(i)
      if (firstChar < '0') { // Possible leading "+" or "-"
        if (firstChar == '-') {
          negative = true
          limit = Long.MinValue
        } else if (firstChar != '+') throw forCharSequence(s, beginIndex, endIndex, i)
        i += 1
      }
      if (i >= endIndex) { // Cannot have lone "+", "-" or ""
        throw forCharSequence(s, beginIndex, endIndex, i)
      }
      val multmin   = limit / radix
      var result    = 0L
      while (i < endIndex) {
        // Accumulating negatively avoids surprises near MAX_VALUE
        val digit = Character.digit(s.charAt(i), radix)
        if (digit < 0 || result < multmin) throw forCharSequence(s, beginIndex, endIndex, i)
        result *= radix
        if (result < limit + digit) throw forCharSequence(s, beginIndex, endIndex, i)
        i += 1
        result -= digit
      }
      if (negative) result
      else -result
    } else throw new NumberFormatException("")
  }

  private[codec] def parseInt(s: CharSequence, beginIndex: Int, endIndex: Int, radix: Int): Int = {
    Objects.requireNonNull(s)
    Objects.checkFromToIndex(beginIndex, endIndex, s.length)
    if (radix < Character.MIN_RADIX)
      throw new NumberFormatException("radix " + radix + " less than Character.MIN_RADIX")
    if (radix > Character.MAX_RADIX)
      throw new NumberFormatException("radix " + radix + " greater than Character.MAX_RADIX")
    var negative = false
    var i        = beginIndex
    var limit    = -Int.MaxValue
    if (i < endIndex) {
      val firstChar = s.charAt(i)
      if (firstChar < '0') { // Possible leading "+" or "-"
        if (firstChar == '-') {
          negative = true
          limit = Int.MinValue
        } else if (firstChar != '+') throw forCharSequence(s, beginIndex, endIndex, i)
        i += 1
        if (i == endIndex) { // Cannot have lone "+" or "-"
          throw forCharSequence(s, beginIndex, endIndex, i)
        }
      }
      val multmin   = limit / radix
      var result    = 0
      while (i < endIndex) {
        // Accumulating negatively avoids surprises near MAX_VALUE
        val digit = Character.digit(s.charAt(i), radix)
        if (digit < 0 || result < multmin) throw forCharSequence(s, beginIndex, endIndex, i)
        result *= radix
        if (result < limit + digit) throw forCharSequence(s, beginIndex, endIndex, i)
        i += 1
        result -= digit
      }
      if (negative) result
      else -result
    } else throw forInputString("", radix)
  }

  private[codec] def forCharSequence(s: CharSequence, beginIndex: Int, endIndex: Int, errorIndex: Int) =
    new NumberFormatException(
      "Error at index " + (errorIndex - beginIndex) + " in: \"" + s.subSequence(beginIndex, endIndex) + "\"",
    )

  private[codec] def forInputString(s: String, radix: Int) = new NumberFormatException(
    "For input string: \"" + s + "\"" + (if (radix == 10) ""
                                         else " under radix " + radix),
  )
}
