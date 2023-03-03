package zio.http.internal

import zio.http.QueryParams
import zio.http.netty.NettyQueryParamEncoding

private[http] trait QueryParamEncoding {
  def decode(queryStringFragment: String): QueryParams
  def encode(baseUri: String, queryParams: QueryParams): String
}

private[http] object QueryParamEncoding {
  val default: QueryParamEncoding = NettyQueryParamEncoding
}
