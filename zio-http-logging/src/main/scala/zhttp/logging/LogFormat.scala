package zhttp.logging

import zhttp.logging.LogFormat.DateFormat.ISODateTime

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Setup {
  final case class DefaultFormat(format: LogLine => String) {
    def apply(line: LogLine): String = format(line)
  }

}

sealed trait LogFormat { self =>
  import LogFormat._

  def <+>(that: LogFormat): LogFormat = self combine that

  def combine(that: LogFormat): LogFormat = LogFormat.Combine(self, that)

  final def color(color: Color): LogFormat = ColorWrap(color, self)

  final def wrap(wrapper: TextWrapper): LogFormat = TextWrappers(wrapper, self)

  final def fixed(size: Int): LogFormat = Fixed(size, self)

  final def |-|(other: LogFormat): LogFormat = Spaced(self, other)

  final def -(other: LogFormat): LogFormat = Dash(self, other)

  final def \\(other: LogFormat): LogFormat = NewLine(self, other)

  final def trim: LogFormat = Trim(self)

  def apply(line: LogLine): String = LogFormat.run(self, line)

}

object LogFormat {

  sealed trait DateFormat
  object DateFormat {
    case object ISODateTime extends DateFormat
  }

  sealed trait Color
  object Color {
    case object RED     extends Color
    case object BLUE    extends Color
    case object YELLOW  extends Color
    case object CYAN    extends Color
    case object GREEN   extends Color
    case object MAGENTA extends Color
    case object WHITE   extends Color
    case object RESET   extends Color
    case object DEFAULT extends Color

    def asConsole(color: Color): String = color match {
      case RED     => Console.RED
      case BLUE    => Console.BLUE
      case YELLOW  => Console.YELLOW
      case CYAN    => Console.CYAN
      case GREEN   => Console.GREEN
      case MAGENTA => Console.MAGENTA
      case WHITE   => Console.WHITE
      case RESET   => Console.RESET
      case DEFAULT => ""
    }
  }

  sealed trait TextWrapper
  object TextWrapper {
    case object BRACKET extends TextWrapper
    case object QUOTED  extends TextWrapper
    case object EMPTY   extends TextWrapper
  }

  final case class FormatDate(dateFormat: DateFormat)                                            extends LogFormat
  final case class ThreadName(includeThreadName: Boolean)                                        extends LogFormat
  final case class ThreadId(includeThreadId: Boolean)                                            extends LogFormat
  case object LoggerLevel                                                                        extends LogFormat
  final case class Combine(left: LogFormat, right: LogFormat)                                    extends LogFormat
  final case class ColorWrap(color: Color, configuration: LogFormat)                             extends LogFormat
  final case class LineColor(info: Color, error: Color, debug: Color, trace: Color, warn: Color) extends LogFormat
  final case class TextWrappers(wrapper: TextWrapper, configuration: LogFormat)                  extends LogFormat
  final case class Fixed(size: Int, configuration: LogFormat)                                    extends LogFormat
  final case class Spaced(left: LogFormat, right: LogFormat)                                     extends LogFormat
  final case class Dash(left: LogFormat, right: LogFormat)                                       extends LogFormat
  final case class NewLine(left: LogFormat, right: LogFormat)                                    extends LogFormat
  final case class Trim(logFmt: LogFormat)                                                       extends LogFormat
  case object SourceLocation                                                                     extends LogFormat
  case object Msg                                                                                extends LogFormat
  case object Tags                                                                               extends LogFormat

  def logLevel: LogFormat                     = LoggerLevel
  def date(dateFormat: DateFormat): LogFormat = FormatDate(dateFormat)
  def threadName: LogFormat                   = ThreadName(true)
  def threadId: LogFormat                     = ThreadId(true)
  def msg: LogFormat                          = Msg
  def sourceLocation: LogFormat               = SourceLocation
  def tags: LogFormat                         = Tags

  def color(info: Color, error: Color, debug: Color, trace: Color, warn: Color): LogFormat =
    LineColor(info, error, debug, trace, warn)

  private def run(logFormat: LogFormat, logLine: LogLine): String = {

    logFormat match {
      case SourceLocation                => logLine.sourceLocation.map(sp => s"${sp.file} ${sp.line}").getOrElse("")
      case FormatDate(dateFormat)        => formatDate(dateFormat, logLine.timestamp)
      case ThreadName(includeThreadName) => if (includeThreadName) logLine.thread.getName else ""
      case ThreadId(includeThreadId)     => if (includeThreadId) logLine.thread.getId.toString else ""
      case LoggerLevel                   => logLine.level.name
      case Combine(left, right)          => left(logLine) ++ right(logLine)
      case ColorWrap(color, conf)        =>
        colorText(color, conf(logLine))
      case TextWrappers(wrapper, conf)   => wrap(wrapper, conf(logLine))
      case Fixed(_, conf)                => conf(logLine)
      case Spaced(left, right)           => left(logLine) + " " + right(logLine)
      case Dash(left, right)             => left(logLine) + " - " + right(logLine)
      case NewLine(left, right)          => left(logLine) + "\n" + right(logLine)
      case Msg                           => logLine.message
      case Trim(conf)                    => conf(logLine).trim
      case LineColor(info, error, debug, trace, warn) =>
        logLine.level match {
          case LogLevel.Trace => Color.asConsole(trace)
          case LogLevel.Debug => Color.asConsole(debug)
          case LogLevel.Info  => Color.asConsole(info)
          case LogLevel.Warn  => Color.asConsole(warn)
          case LogLevel.Error => Color.asConsole(error)
        }
      case Tags                                       => logLine.tags.mkString(",")
    }
  }

  private def formatDate(format: LogFormat.DateFormat, time: LocalDateTime): String = format match {
    case DateFormat.ISODateTime => time.format(DateTimeFormatter.ISO_TIME)
  }

  /**
   * Wrap a text if the text is not empty.
   */
  private def wrap(wrapper: TextWrapper, value: String): String = {
    if (value.isEmpty) ""
    else
      wrapper match {
        case TextWrapper.BRACKET => s"[$value]"
        case TextWrapper.QUOTED  => s"{$value}"
        case TextWrapper.EMPTY   => value
      }
  }

  private def colorText(color: Color, value: String): String = {
    val consoleColor = Color.asConsole(color)
    color match {
      case Color.RED     => s"$consoleColor$value${Console.RESET}"
      case Color.BLUE    => s"$consoleColor$value${Console.RESET}"
      case Color.YELLOW  => s"$consoleColor$value${Console.RESET}"
      case Color.CYAN    => s"$consoleColor$value${Console.RESET}"
      case Color.GREEN   => s"$consoleColor$value${Console.RESET}"
      case Color.MAGENTA => s"$consoleColor$value${Console.RESET}"
      case Color.WHITE   => s"$consoleColor$value${Console.RESET}"
      case Color.RESET   => Console.RESET
      case Color.DEFAULT => value
    }
  }

  val minimal: LogFormat =
    LogFormat.Tags.wrap(TextWrapper.BRACKET) |-|
      LogFormat.sourceLocation.wrap(TextWrapper.BRACKET) |-|
      LogFormat.msg

  val maximus: LogFormat =
    LogFormat.Tags.wrap(TextWrapper.BRACKET) |-|
      LogFormat.date(ISODateTime) |-|
      LogFormat.threadName.wrap(TextWrapper.BRACKET) |-|
      LogFormat.sourceLocation.wrap(TextWrapper.BRACKET) |-|
      LogFormat.logLevel - LogFormat.msg

  val colored: LogFormat = LogFormat.color(
    info = Color.GREEN,
    error = Color.RED,
    debug = Color.CYAN,
    warn = Color.YELLOW,
    trace = Color.WHITE,
  ) <+> minimal
}
