package zio.logging

import zio.logging.Font._

private[logging] sealed trait Font { self =>
  def toAnsiColor: String = self match {
    case BLACK      => Console.BLACK
    case BLACK_B    => Console.BLACK_B
    case BLINK      => Console.BLINK
    case BLUE       => Console.BLUE
    case BLUE_B     => Console.BLUE_B
    case BOLD       => Console.BOLD
    case CYAN       => Console.CYAN
    case CYAN_B     => Console.CYAN_B
    case GREEN      => Console.GREEN
    case GREEN_B    => Console.GREEN_B
    case INVISIBLE  => Console.INVISIBLE
    case MAGENTA    => Console.MAGENTA
    case MAGENTA_B  => Console.MAGENTA_B
    case RED        => Console.RED
    case RED_B      => Console.RED_B
    case RESET      => Console.RESET
    case REVERSED   => Console.REVERSED
    case UNDERLINED => Console.UNDERLINED
    case WHITE      => Console.WHITE
    case WHITE_B    => Console.WHITE_B
    case YELLOW     => Console.YELLOW
    case YELLOW_B   => Console.YELLOW_B
  }
}

object Font {
  case object BLACK      extends Font
  case object RED        extends Font
  case object GREEN      extends Font
  case object YELLOW     extends Font
  case object BLUE       extends Font
  case object MAGENTA    extends Font
  case object CYAN       extends Font
  case object WHITE      extends Font
  case object BLACK_B    extends Font
  case object RED_B      extends Font
  case object GREEN_B    extends Font
  case object YELLOW_B   extends Font
  case object BLUE_B     extends Font
  case object MAGENTA_B  extends Font
  case object CYAN_B     extends Font
  case object WHITE_B    extends Font
  case object RESET      extends Font
  case object BOLD       extends Font
  case object UNDERLINED extends Font
  case object BLINK      extends Font
  case object REVERSED   extends Font
  case object INVISIBLE  extends Font
}
