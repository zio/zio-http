package zio.http.internal
import java.nio.charset.Charset

import zio.http.QueryParams

private[http] trait QueryParamEncodingPlatformSpecific {
  val default: QueryParamEncoding = // throw new NotImplementedError("No version implemented for Scala.js yet.")
    new QueryParamEncoding {
      override def decode(queryStringFragment: String, charset: Charset): QueryParams = QueryParams.empty

      override def encode(baseUri: String, queryParams: QueryParams, charset: Charset): String =
        baseUri + "?" + queryParams.map.map { case (k, v) => s"$k=${v.mkString(",")}" }.mkString("&")
    }
}
