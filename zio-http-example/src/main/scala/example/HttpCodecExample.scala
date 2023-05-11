package example

import zio.http.codec.HttpCodec._
import zio.http.codec.HttpCodecType.PathQuery
import zio.http.codec.{HttpCodec, HttpCodecType, PathCodec, QueryCodec}
import zio.http.endpoint.Endpoint

object HttpCodecExample {

  val pathOnly: PathCodec[(String, String)] =
    "organisation" / string("organisationId") / "accounts" / string("accountId")

  val queryOnly: HttpCodec[HttpCodecType.Query, (String, Int)] =
    paramStr("place") & paramInt("age")

  val pathWithQuery: HttpCodec[PathQuery, (String, String, String, Int)] =
    "organisation" / string("organisationId") / "accounts" / string("accountId") ^?
      paramStr("place") & paramInt("age")

  val reusePathQuery: HttpCodec[PathQuery, (String, String, String, Int)] =
    pathOnly ^? queryOnly

  // Note that the following  doesn't compile as expected
  //   val s =  "organisation" / "accounts" & paramStr("accountId")

  Endpoint.get(pathWithQuery)
  Endpoint.get(pathOnly)
}
