package example

import zio.http.codec.HttpCodec._
import zio.http.codec._

object HttpCodecExample {

  val pathOnly: PathCodec[Unit]     = "organisation" / "accounts"
  val queryOnly: QueryCodec[String] = paramStr("name")

  val pathWithQuery: HttpCodec[HttpCodecType.PathQuery, String] =
    "organisation" / "accounts" :? paramStr("accountId")

  // This doesn't compile as expected :-)
  //   val s =
  //     "organisation" / "accounts" & paramStr("accountId")

  val pathWithMultipleQuery: HttpCodec[HttpCodecType.PathQuery, (String, String, String, String, Int)] =
    "organisation" / "accounts" / string("accountId") :?
      paramStr("foo") &
      paramStr("bar") &
      paramStr("baz") &
      paramInt("in")

  val multipleQueryOnly: HttpCodec[HttpCodecType.Query, (String, Int, String)] =
    paramStr("foo") & paramInt("bar") & paramStr("hmm")

}
