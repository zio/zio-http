package zio.http.model.headers.values

import scala.util.{Success, Try}

/**
 * Max-Forwards header value
 */
sealed trait MaxForwards
object MaxForwards {

  final case class MaxForwardsValue(value: Int) extends MaxForwards
  case object InvalidMaxForwardsValue           extends MaxForwards

  def toMaxForwards(value: String): MaxForwards = {
    Try(value.toInt) match {
      case Success(value) if value >= 0L => MaxForwardsValue(value)
      case _                             => InvalidMaxForwardsValue
    }
  }

  def fromMaxForwards(maxForwards: MaxForwards): String =
    maxForwards match {
      case MaxForwardsValue(value) => value.toString
      case InvalidMaxForwardsValue => ""
    }
}
