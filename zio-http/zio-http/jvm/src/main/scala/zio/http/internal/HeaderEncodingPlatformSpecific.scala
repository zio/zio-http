package zio.http.internal

import zio.http.netty.NettyHeaderEncoding

private[http] trait HeaderEncodingPlatformSpecific {
  val default: HeaderEncoding = NettyHeaderEncoding
}
