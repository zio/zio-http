package example

import zio.http.codec.HttpCodec._
import zio.http.codec._

object HttpEndPointExperimentations {

  val pathOnly: PathCodec[Unit]     = "organisation" / "accounts"
  val queryOnly: QueryCodec[String] = paramStr("name")

  // Suggestion 1 - Query-params is more intuitive to be appended along with the path
  val pathWithQuery: PathQueryCodec[String] = "organisation" / "accounts" :? paramStr("accountId")

  val pathWithMultipleQuery: PathQueryCodec[(String, Int, String)] =
    "organisation" / "accounts" / string("accountId") :? paramInt("age") & paramStr("country")

  // Suggestion 2 - `in` can have an alias of `withRequestBody`
  import zio.http.endpoint.Endpoint

  Endpoint.post(pathOnly).withRequestBody[String]

  // Slight (ignorable) downside to suggestion 1 - standalone query composition returns path-query type (solveable though)
  val multipleQueryOnly: PathQueryCodec[(String, Int)] = paramStr("name") & paramInt("age")

}
