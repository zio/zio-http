package zio.http.netty

import java.net.InetSocketAddress

import zio.http.Proxy
import zio.http.middleware.Auth.Credentials
import zio.http.netty.model.Conversions

import io.netty.handler.proxy.HttpProxyHandler

class NettyProxy private (proxy: Proxy) {

  /**
   * Converts a Proxy to [io.netty.handler.proxy.HttpProxyHandler]
   */
  def encode: Option[HttpProxyHandler] = proxy.credentials.fold(unauthorizedProxy)(authorizedProxy)

  private def authorizedProxy(credentials: Credentials): Option[HttpProxyHandler] = for {
    proxyAddress <- buildProxyAddress
    uname          = credentials.uname
    upassword      = credentials.upassword
    encodedHeaders = Conversions.headersToNetty(proxy.headers)
  } yield new HttpProxyHandler(proxyAddress, uname, upassword, encodedHeaders)

  private def unauthorizedProxy: Option[HttpProxyHandler] = for {
    proxyAddress <- buildProxyAddress
    encodedHeaders = Conversions.headersToNetty(proxy.headers)
  } yield {
    new HttpProxyHandler(proxyAddress, encodedHeaders)
  }

  private def buildProxyAddress: Option[InetSocketAddress] = for {
    proxyHost <- proxy.url.host
    proxyPort <- proxy.url.port
  } yield new InetSocketAddress(proxyHost, proxyPort)
}

object NettyProxy {
  def fromProxy(proxy: Proxy): NettyProxy = new NettyProxy(proxy)
}
