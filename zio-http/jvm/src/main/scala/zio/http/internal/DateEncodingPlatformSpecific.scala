package zio.http.internal

import zio.http.netty.NettyDateEncoding

private[http] trait DateEncodingPlatformSpecific {
  val default: DateEncoding = NettyDateEncoding
}
