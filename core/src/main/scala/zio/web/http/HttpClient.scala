package zio.web.http

import java.io.{ IOException, StringReader }

import zio.{ Chunk, IO, Task, ZIO, ZManaged }
import zio.clock.Clock
import zio.duration._
import zio.nio.core.{ InetSocketAddress, SocketAddress }
import zio.nio.core.channels.{ AsynchronousChannelGroup, AsynchronousSocketChannel }
import zio.logging.{ Logging, log }
import zio.stream.ZStream
import zio.web.{ Annotations, AnyF, Client, Endpoint, Endpoints }
import zio.web.codec.JsonCodec
import zio.web.http.model.{ HttpAnn, Method, Route, Version }
import zio.web.http.internal.{ ChannelReader, HttpLexer }

final class HttpClient[Ids](
  address: InetSocketAddress,
  channelGroup: AsynchronousChannelGroup,
  env: Clock with Logging
) extends Client[HttpAnn, Ids] {

  import HttpClient._

  def invoke[P, M[+_] <: HttpAnn[_], I, O](endpoint: Endpoint[M, P, I, O])(params: P, input: I)(
    implicit ev: Ids <:< endpoint.Id
  ): Task[O] =
    (for {
      _           <- log.info(s"Invoking [${endpoint.endpointName}].")
      channel     <- AsynchronousSocketChannel(channelGroup)
      _           <- channel.connect(address)
      request     <- encodeRequest(endpoint)(params, input)
      _           <- channel.writeChunk(request)
      _           <- log.info(s"Sent request")
      reader      = ChannelReader(channel, 32, 30.seconds)
      data        <- reader.readUntilNewLine()
      statusLine  = new String(data.value.toArray)
      _           <- log.info(s"Read status line:\n$statusLine")
      data        <- reader.readUntilEmptyLine(data.tail)
      headerLines = new String(data.value.toArray)
      _           <- log.info(s"Read headers:\n$headerLines")
      // TODO: extract headers parsing and wrap them in HttpHeaders to tidy up a little here
      headerNames  = Array("Content-Length") // TODO: when not present then do not read the content
      headerValues = HttpLexer.parseHeaders(headerNames, new StringReader(headerLines))
      headers = headerNames
        .zip(headerValues.map(_.headOption))
        .collect { case (key, Some(value)) => key -> value }
        .toMap
      _          <- log.info(s"Parsed headers:\n$headers")
      bodyLength = headers("Content-Length").toInt
      data       <- reader.read(bodyLength, data.tail)
      output     <- HttpClient.decodeResponse(endpoint)(data)
    } yield output).provide(env)
}

object HttpClient {

  def build[M[+_] <: HttpAnn[_], Ids](
    config: HttpClientConfig,
    endpoints: Endpoints[M, Ids]
  ): ZManaged[Clock with Logging, IOException, HttpClient[Ids]] =
    for {
      address <- SocketAddress.inetSocketAddress(config.host, config.port).toManaged_
      eces    <- ZIO.runtime.map((rts: zio.Runtime[Any]) => rts.platform.executor.asECES).toManaged_
      group   <- AsynchronousChannelGroup(eces).toManaged_
      env     <- ZIO.environment[Clock with Logging].toManaged_
      client  <- ZIO.succeed(new HttpClient[Ids](address, group, env)).toManaged_
      _       = endpoints
    } yield client

  final private[http] case class ResponseMessage(headers: Chunk[Byte], body: Chunk[Byte])

  private def encodeRequest[P, M[+_] <: HttpAnn[_], I](
    endpoint: Endpoint[M, P, I, _]
  )(params: P, input: I): ZIO[Logging, IOException, Chunk[Byte]] =
    for {
      body      <- ZStream.succeed(input).transduce(JsonCodec.encoder(endpoint.request)).runCollect
      startLine = buildStartLine(endpoint, params)
      header <- ZIO.succeed(
                 Seq(
                   startLine,
                   "Content-Type: application/json",
                   s"Content-Length: ${body.size}"
                 ).mkString("", "\r\n", "\r\n\r\n")
               )
      _ <- log.info(s"Request header: $header")
    } yield charSequenceToByteChunk(header) ++ body

  private def decodeResponse[O](endpoint: Endpoint[AnyF, _, _, O])(bytes: Chunk[Byte]): IO[IOException, O] =
    ZStream
      .fromChunk(bytes)
      .transduce(JsonCodec.decoder(endpoint.response))
      .runHead
      .catchAll(_ => ZIO.none)
      .someOrElseM(ZIO.fail(new IOException("Could not decode output")))

  private def charSequenceToByteChunk(chars: CharSequence): Chunk[Byte] = {
    val bytes: Seq[Byte] = for (i <- 0 until chars.length) yield chars.charAt(i).toByte
    Chunk.fromIterable(bytes)
  }

  // TODO: once the server router correctly parses URI's clean up a little
  final case class StartLine(method: Option[String], uri: Option[String])

  private def buildStartLine[P, M[+_] <: HttpAnn[_]](endpoint: Endpoint[M, P, _, _], params: P): String = {
    def loop[P0](acc: StartLine, ann: Annotations[M, P0], params: P0): String =
      ann match {
        case cons: Annotations.Cons[M, _, _, P0] =>
          val (pHead, pTail) = cons.combine.unpack(params)
          val nextAcc = cons.head.asInstanceOf[HttpAnn[cons.combine.Left]] match {
            case m: Method                   => acc.copy(method = Some(m.name))
            case r: Route[cons.combine.Left] => acc.copy(uri = Some(renderRoute(r, pHead)))
          }

          loop(nextAcc, cons.tail, pTail)

        case Annotations.None =>
          val method  = acc.method.getOrElse(Method.GET.name)
          val uri     = acc.uri.getOrElse("/")
          val version = Version.V1_1.name
          s"$method $uri $version"
      }

    loop(StartLine(None, None), endpoint.annotations, params)
  }

  private def renderRoute[A](route: Route[A], param: A): String = {
    def loop[B](acc: List[String], route: Route[B], param: B): List[String] =
      route match {
        case Route.Root => acc
        case r: Route.Cons[_, _, _, _, B] =>
          val (hParam, tParam) = r.combine.unpack(param)
          val segment          = render(r.head, hParam)

          loop(segment :: acc, r.tail, tParam)
      }

    def render[B](segment: Route.Segment[B], param: B): String =
      segment match {
        case Route.Segment.Static(segment) => segment
        case Route.Segment.Param(_, to)    => to(param)
      }

    loop(Nil, route, param).mkString("/", "/", "")
  }
}
