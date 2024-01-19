package zio.http.internal

import zio.http.netty.NettyQueryParamEncoding

private[http] trait QueryParamEncodingPlatformSpecific {
  val default: QueryParamEncoding = NettyQueryParamEncoding
}
