package zhttp.core

import io.netty.channel.{embedded => jEmbedded, socket => jSocket}
import io.netty.handler.codec.http.{websocketx => jwebsocketx}
import io.netty.handler.codec.{http => jHttp}
import io.netty.util.{concurrent => jConcurrent}
import io.netty.{bootstrap => jBootstrap, buffer => jBuffer, channel => jChannel, util => jUtil}

/**
 * Netty Type Aliases
 */
trait AliasModule {
  // Channel
  type JChannel                           = jChannel.Channel
  type JChannelFactory[A <: JChannel]     = jChannel.ChannelFactory[A]
  type JChannelHandlerContext             = jChannel.ChannelHandlerContext
  type JChannelInboundHandlerAdapter      = jChannel.ChannelInboundHandlerAdapter
  type JChannelInitializer[A <: JChannel] = jChannel.ChannelInitializer[A]
  type JDefaultEventLoopGroup             = jChannel.DefaultEventLoopGroup
  type JEpollEventLoopGroup               = jChannel.epoll.EpollEventLoopGroup
  type JEpollSocketChannel                = jChannel.epoll.EpollSocketChannel
  type JEventLoopGroup                    = jChannel.EventLoopGroup
  type JNioEventLoopGroup                 = jChannel.nio.NioEventLoopGroup
  type JServerChannel                     = jChannel.ServerChannel
  type JSimpleChannelInboundHandler[A]    = jChannel.SimpleChannelInboundHandler[A]
  type JSharable                          = jChannel.ChannelHandler.Sharable
  type JEpollServerSocketChannel          = jChannel.epoll.EpollServerSocketChannel
  type JKQueueServerSocketChannel         = jChannel.kqueue.KQueueServerSocketChannel
  type JNioServerSocketChannel            = jChannel.socket.nio.NioServerSocketChannel
  type JChannelConfig                     = jChannel.ChannelConfig
  type JChannelOption[A]                  = jChannel.ChannelOption[A]
  type JRecvByteBufAllocator              = jChannel.RecvByteBufAllocator
  type JMessageSizeEstimator              = jChannel.MessageSizeEstimator
  type JWriteBufferWaterMark              = jChannel.WriteBufferWaterMark
  type JChannelPipeline                   = jChannel.ChannelPipeline
  type JChannelHandler                    = jChannel.ChannelHandler
  type JChannelFuture                     = jChannel.ChannelFuture

  // WebSocket
  type JBinaryWebSocketFrame             = jwebsocketx.BinaryWebSocketFrame
  type JCloseWebSocketFrame              = jwebsocketx.CloseWebSocketFrame
  type JContinuationWebSocketFrame       = jwebsocketx.ContinuationWebSocketFrame
  type JPingWebSocketFrame               = jwebsocketx.PingWebSocketFrame
  type JPongWebSocketFrame               = jwebsocketx.PongWebSocketFrame
  type JTextWebSocketFrame               = jwebsocketx.TextWebSocketFrame
  type JWebSocketFrame                   = jwebsocketx.WebSocketFrame
  type JWebSocketServerHandshakerFactory = jwebsocketx.WebSocketServerHandshakerFactory
  type JWebSocketServerHandshaker        = jwebsocketx.WebSocketServerHandshaker
  type JWebSocketHandshakeException      = jwebsocketx.WebSocketHandshakeException

  // HTTP
  type JHttpMethod              = jHttp.HttpMethod
  type JHttpScheme              = jHttp.HttpScheme
  type JHttpHeaders             = jHttp.HttpHeaders
  type JHttpVersion             = jHttp.HttpVersion
  type JHttpRequest             = jHttp.HttpRequest
  type JDefaultHttpHeaders      = jHttp.DefaultHttpHeaders
  type JFullHttpRequest         = jHttp.FullHttpRequest
  type JFullHttpResponse        = jHttp.FullHttpResponse
  type JHttpObjectAggregator    = jHttp.HttpObjectAggregator
  type JHttpServerCodec         = jHttp.HttpServerCodec
  type JHttpResponseStatus      = jHttp.HttpResponseStatus
  type JDefaultFullHttpResponse = jHttp.DefaultFullHttpResponse
  type JDefaultFullHttpRequest  = jHttp.DefaultFullHttpRequest

  // Misc
  type JEmbeddedChannel         = jEmbedded.EmbeddedChannel
  type JNioSocketChannel        = jSocket.nio.NioSocketChannel
  type JServerBootstrap         = jBootstrap.ServerBootstrap
  type JBootstrap               = jBootstrap.Bootstrap
  type JByteBuf                 = jBuffer.ByteBuf
  type JByteBufAllocator        = jBuffer.ByteBufAllocator
  type JCharsetUtil             = jUtil.CharsetUtil
  type JResourceLeakDetector[A] = jUtil.ResourceLeakDetector[A]
  type JFuture[A]               = jConcurrent.Future[A]
  type JAsciiString             = jUtil.AsciiString
  type JReferenceCountUtil      = jUtil.ReferenceCountUtil

}
