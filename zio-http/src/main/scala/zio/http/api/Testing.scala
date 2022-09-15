package zio.http.api

import zio._
import zio.http.api.internal._

object CommonPrefixTesting extends App {
  import In._

  val api1 = API.get(literal("users") / int / literal("posts") / int)
  val api2 = API.get(literal("users") / int)
  val api3 = API.get(literal("users") / int / literal("posts"))

  val handled1 = HandledAPI(api1, (r: Any) => ZIO.debug(s"RESULT 1: $r"))
  val handled2 = HandledAPI(api2, (r: Any) => ZIO.debug(s"RESULT 2: $r"))
  val handled3 = HandledAPI(api3, (r: Any) => ZIO.debug(s"RESULT 3: $r"))

  val zippable1 = ZippableHandledAPI.fromHandledAPI(handled1)
  val zippable2 = ZippableHandledAPI.fromHandledAPI(handled2)
  val zippable3 = ZippableHandledAPI.fromHandledAPI(handled3)

  val combined =
    ZippedHandledAPIs.fromZippableHandledAPI(zippable1) merge
      ZippedHandledAPIs.fromZippableHandledAPI(zippable2) merge
      ZippedHandledAPIs.fromZippableHandledAPI(zippable3)

  println(combined.render)

}
