package zhttp.logging

import zhttp.logging.LogFormat.DateFormat.ISODateTime

import java.time.format.DateTimeFormatter

object Setup {
  final case class DefaultFormat(format: LogLine => String) {
    def apply(line: LogLine): String = format(line)
  }
}

sealed trait LogFormat { self =>
  import LogFormat._

  final def |-|(other: LogFormat): LogFormat = Combine(self, " ", other)

  final def \\(other: LogFormat): LogFormat = Combine(self, "\n", other)

  final def <+>(that: LogFormat): LogFormat = Combine(self, "", that)

  final def -(other: LogFormat): LogFormat = Combine(self, "-", other)

  final def apply(line: LogLine): String = {
    val colorStack = collection.mutable.Stack.empty[Font]

    def loop(self: LogFormat, line: LogLine): String = {
      self match {
        case Location                      => line.sourceLocation.map(sp => s"${sp.file} ${sp.line}").getOrElse("")
        case FormatDate(dateFormat)        =>
          dateFormat match {
            case DateFormat.ISODateTime => line.timestamp.format(DateTimeFormatter.ISO_TIME)
          }
        case ThreadName(includeThreadName) => if (includeThreadName) line.thread.getName else ""
        case ThreadId(includeThreadId)     => if (includeThreadId) line.thread.getId.toString else ""
        case Level                         => line.level.name
        case Combine(left, sep, right)     => loop(left, line) + sep + loop(right, line)
        case FontWrap(font, fmt)           =>
          colorStack.push(font)
          val msg   = loop(fmt, line)
          val start = font.toAnsiColor
          colorStack.pop()
          val end   = colorStack.map(_.toAnsiColor).mkString("")
          s"${start}${msg}${Console.RESET}$end"
        case Msg                           => line.message
        case Tags                          => line.tags.mkString(":")
        case Literal(msg)                  => msg
        case Format(fmt, f)                => loop(f(loop(fmt, line)), line)
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

  final def cyan: LogFormat = font(Font.CYAN)

  final def cyanB: LogFormat = font(Font.CYAN_B)

  final def fixed(n: Int): LogFormat = transform(_.padTo(n, ' '))

  final def flipColor: LogFormat = self.font(Font.REVERSED)

  final def font(font: Font): LogFormat = FontWrap(font, self)

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

  final def underlined: LogFormat = font(Font.UNDERLINED)

  final def uppercase: LogFormat = transform(_.toUpperCase)

  final def white: LogFormat = font(Font.WHITE)

  final def whiteB: LogFormat = font(Font.WHITE_B)

  final def yellow: LogFormat = font(Font.YELLOW)

  final def yellowB: LogFormat = font(Font.YELLOW_B)
}

object LogFormat {

  val AutoColorSeq: Array[Font] = Array(
    Font.BLUE,
    Font.CYAN,
    Font.GREEN,
    Font.MAGENTA,
    Font.YELLOW,
  )

  def colored: LogFormat =
    LogFormat.level.uppercase.bracket.fixed(7).white |-|
      LogFormat.tags.autoColor.flipColor |-|
      LogFormat.message

  def date(dateFormat: DateFormat): LogFormat = FormatDate(dateFormat)

  def level: LogFormat = Level

  def literal(a: String): LogFormat = Literal(a)

  def location: LogFormat = Location

  def maximus: LogFormat =
    LogFormat.tags.bracket |-|
      LogFormat.date(ISODateTime) |-|
      LogFormat.threadName.bracket |-|
      LogFormat.location.bracket |-|
      LogFormat.level - LogFormat.message

  def message: LogFormat = Msg

  def tags: LogFormat = Tags

  def threadId: LogFormat = ThreadId(true)

  def threadName: LogFormat = ThreadName(true)

  sealed trait DateFormat

  final case class FormatDate(dateFormat: DateFormat) extends LogFormat

  final case class ThreadName(includeThreadName: Boolean) extends LogFormat

  final case class ThreadId(includeThreadId: Boolean) extends LogFormat

  final case class Combine(left: LogFormat, sep: String, right: LogFormat) extends LogFormat

  final case class FontWrap(font: Font, fmt: LogFormat) extends LogFormat

  final case class Literal(lit: String) extends LogFormat

  final case class Format(fmt: LogFormat, f: String => LogFormat) extends LogFormat

  object DateFormat {
    case object ISODateTime extends DateFormat
  }

  case object Level    extends LogFormat
  case object Location extends LogFormat
  case object Msg      extends LogFormat
  case object Tags     extends LogFormat
}
