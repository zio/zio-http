package zio.http.codec

trait PathCodecPlatformSpecific {
  def parseLong(s: CharSequence, beginIndex: Int, endIndex: Int, radix: Int): Long =
    java.lang.Long.parseLong(s.subSequence(beginIndex, endIndex).toString, radix)

  def parseInt(s: CharSequence, beginIndex: Int, endIndex: Int, radix: Int): Int =
    java.lang.Integer.parseInt(s.subSequence(beginIndex, endIndex).toString, radix)
}
