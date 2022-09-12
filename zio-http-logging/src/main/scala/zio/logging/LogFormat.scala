package zio.logging

import java.time.format.DateTimeFormatter

sealed trait LogFormat { self =>
  import LogFormat._

  final def |-|(other: LogFormat): LogFormat = Combine(self, " ", other)

  final def \\(other: LogFormat): LogFormat = Combine(self, "\n", other)

  final def <+>(that: LogFormat): LogFormat = Combine(self, "", that)

  final def -(other: LogFormat): LogFormat = Combine(self, "-", other)

  final def apply(line: LogLine): String = {
    val colorStack = collection.mutable.Stack.empty[String]

    def loop(self: LogFormat, line: LogLine): String = {
      self match {
        case Location                  => line.sourceLocation.map(sp => s"${sp.file} ${sp.line}").getOrElse("")
        case Timestamp(dateFormat)     => line.timestamp.format(dateFormat)
        case ThreadInfo(f)             => f(line.thread)
        case Level                     => line.level.name
        case Combine(left, sep, right) => loop(left, line) + sep + loop(right, line)
        case FontWrap(font, fmt)       =>
          colorStack.push(font)
          val msg   = loop(fmt, line)
          val start = font
          colorStack.pop()
          val end   = colorStack.mkString("")
          s"${start}${msg}${Console.RESET}$end"
        case Msg                       => line.message
        case Tags                      => line.tags.mkString(":")
        case Literal(msg)              => msg
        case Format(fmt, f)            => loop(f(loop(fmt, line)), line)
      }
    }

    loop(self, line)
  }

  final def autoColor: LogFormat =
    self.format(str => self.font(AutoColorSeq(str.hashCode.abs % AutoColorSeq.length)))

  final def black: LogFormat = font(Font.BLACK)

  final def blackB: LogFormat = font(Font.BLACK_B)

  final def blink: LogFormat = font(Font.BLINK)

  final def blue: LogFormat = font(Font.BLUE)

  final def blueB: LogFormat = font(Font.BLUE_B)

  final def bold: LogFormat = font(Font.BOLD)

  final def bracket: LogFormat = transform(msg => s"[${msg}]")

  final def combine(str: String)(other: LogFormat): LogFormat = Combine(self, str, other)

  final def cyan: LogFormat = font(Font.CYAN)

  final def cyanB: LogFormat = font(Font.CYAN_B)

  final def fixed(n: Int): LogFormat = transform(_.padTo(n, ' '))

  final def flipColor: LogFormat = self.font(Font.REVERSED)

  final def font(font: Font): LogFormat = FontWrap(font.toAnsiColor, self)

  final def font(font: String): LogFormat = FontWrap(font, self)

  final def format(f: String => LogFormat): LogFormat = LogFormat.Format(self, f)

  final def green: LogFormat = font(Font.GREEN)

  final def greenB: LogFormat = font(Font.GREEN_B)

  final def invisible: LogFormat = font(Font.INVISIBLE)

  final def lowercase: LogFormat = transform(_.toLowerCase)

  final def magenta: LogFormat = font(Font.MAGENTA)

  final def magentaB: LogFormat = font(Font.MAGENTA_B)

  final def red: LogFormat = font(Font.RED)

  final def redB: LogFormat = font(Font.RED_B)

  final def reset: LogFormat = font(Font.RESET)

  final def reversed: LogFormat = font(Font.REVERSED)

  final def transform(f: String => String): LogFormat = format(s => LogFormat.Literal(f(s)))

  final def trim: LogFormat = transform(_.trim)

  final def underline: LogFormat = self.font(Font.UNDERLINED)

  final def uppercase: LogFormat = transform(_.toUpperCase)

  final def white: LogFormat = font(Font.WHITE)

  final def whiteB: LogFormat = font(Font.WHITE_B)

  final def yellow: LogFormat = font(Font.YELLOW)

  final def yellowB: LogFormat = font(Font.YELLOW_B)
}

object LogFormat {

  /**
   * List of colors to select from while using the auto-color operator. Taken
   * from -
   * https://www.lihaoyi.com/post/BuildyourownCommandLinewithANSIescapecodes.html#colors
   */
  private val AutoColorSeq: Array[String] = (for {
    i <- 0 to 16
    j <- 0 to 16
    code = i * 16 + j
    if code < 231 || code > 235
  } yield s"\u001b[38;5;${code};1m").toArray

  def inlineColored: LogFormat =
    LogFormat.level.uppercase.bracket.fixed(7) |-|
      LogFormat.threadName |-|
      LogFormat.tags.autoColor |-|
      LogFormat.message

  def inlineMaximus: LogFormat = {
    LogFormat.tags.bracket |-|
      LogFormat.timestamp(DateTimeFormatter.ISO_DATE) |-|
      LogFormat.threadName.bracket |-|
      LogFormat.location.bracket |-|
      LogFormat.level - LogFormat.message
  }

  def inlineMinimal: LogFormat =
    LogFormat.level.uppercase.fixed(5) |-| LogFormat.tags.bracket |-| LogFormat.message

  def level: LogFormat = Level

  def literal(a: String): LogFormat = Literal(a)

  def location: LogFormat = Location

  def message: LogFormat = Msg

  def tags: LogFormat = Tags

  def threadId: LogFormat = ThreadInfo(_.getId.toString)

  def threadName: LogFormat = ThreadInfo(_.getName)

  def timestamp(fmt: DateTimeFormatter): LogFormat = Timestamp(fmt)

  private[zio] final case class Timestamp(fmt: DateTimeFormatter)                       extends LogFormat
  private[zio] final case class ThreadInfo(f: Thread => String)                         extends LogFormat
  private[zio] final case class Combine(left: LogFormat, sep: String, right: LogFormat) extends LogFormat
  private[zio] final case class FontWrap(font: String, fmt: LogFormat)                  extends LogFormat
  private[zio] final case class Literal(lit: String)                                    extends LogFormat
  private[zio] final case class Format(fmt: LogFormat, f: String => LogFormat)          extends LogFormat
  private[zio] case object Level                                                        extends LogFormat
  private[zio] case object Location                                                     extends LogFormat
  private[zio] case object Msg                                                          extends LogFormat
  private[zio] case object Tags                                                         extends LogFormat
}
