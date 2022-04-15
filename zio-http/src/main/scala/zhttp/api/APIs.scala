package zhttp.api

import scala.language.implicitConversions

case class APIs(toList: List[API[_, _, _]]) {
  def ++(that: API[_, _, _]): APIs = APIs(toList :+ that)
  def ++(that: APIs): APIs         = APIs(toList ++ that.toList)
}

object APIs {
  def apply(api: API[_, _, _]): APIs =
    APIs(List(api))

  implicit def api2Apis(api: API[_, _, _]): APIs =
    APIs(List(api))
}
