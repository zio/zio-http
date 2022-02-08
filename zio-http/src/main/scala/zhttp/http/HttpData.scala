package zhttp.http

import io.netty.buffer.{ByteBuf, ByteBufUtil, Unpooled}
import zhttp.http.HttpData.Outgoing._
import zhttp.service.HTTP_CONTENT_HANDLER
import zhttp.service.server.content.handlers.UnsafeRequestHandler.{UnsafeChannel, UnsafeContent}
import zio.blocking.Blocking.Service.live.effectBlocking
import zio.stream.ZStream
import zio.{Chunk, IO, Task, UIO, ZIO}

import java.nio.charset.Charset
import java.nio.file.Files

/**
 * Holds HttpData that needs to be written on the HttpChannel
 */
sealed trait HttpData { self =>

  def asByteBuf: Task[ByteBuf] = self match {
    case HttpData.Incoming(unsafeRun) =>
      for {
        buffer <- UIO(Unpooled.compositeBuffer())
        body   <- ZIO.effectAsync[Any, Throwable, ByteBuf](cb =>
          unsafeRun((ch, msg) => {
            if (buffer.readableBytes() + msg.content.content().readableBytes() > msg.limit) {
              ch.ctx.fireChannelRead(Response.status(Status.REQUEST_ENTITY_TOO_LARGE)): Unit
            } else {
              buffer.writeBytes(msg.content.content())
              if (msg.isLast) {
                cb(UIO(buffer) ensuring UIO(ch.ctx.pipeline().remove(HTTP_CONTENT_HANDLER)))

              } else {
                ch.read()
                ()
              }
            }
            msg.content.release(msg.content.refCnt()): Unit
          }),
        )
      } yield body
    case outgoing: HttpData.Outgoing  => outgoing.toByteBuf
  }

  def asString: Task[String]     = asByteBuf.map(buf => buf.toString(HTTP_CHARSET))
  def asBytes: Task[Chunk[Byte]] = asByteBuf.map(buf => Chunk.fromArray(ByteBufUtil.getBytes(buf)))
  final def asByteChunk: IO[Option[Throwable], Chunk[Byte]] = self match {
    case HttpData.Incoming(unsafeRun) =>
      ZIO.effectAsync(cb =>
        unsafeRun((ch, msg) => {
          val chunk = Chunk.fromArray(ByteBufUtil.getBytes(msg.content.content()))
          cb(UIO(chunk) ensuring UIO(msg.content.release(msg.content.refCnt())))
          if (msg.isLast) {
            ch.ctx.pipeline().remove(HTTP_CONTENT_HANDLER): Unit
          } else {
            ch.read()
            ()
          }
        }),
      )
    case _: HttpData.Outgoing         => ???
  }
  def asStreamByteBuf: ZStream[Any, Throwable, ByteBuf]     = self match {
    case HttpData.Incoming(unsafeRun) =>
      ZStream
        .effectAsync[Any, Throwable, ByteBuf](cb =>
          unsafeRun((ch, msg) => {
            cb(IO.succeed(Chunk(msg.content.content())))
            if (msg.isLast) {
              ch.ctx.pipeline().remove(HTTP_CONTENT_HANDLER)
              cb(IO.fail(None))
            } else {
              ch.read()
            }
          }),
        )
    case _: HttpData.Outgoing         => ???
  }

  /**
   * Returns true if HttpData is a stream
   */
  def isChunked: Boolean = self match {
    case BinaryStream(_) => true
    case _               => false
  }

  /**
   * Returns true if HttpData is empty
   */
  def isEmpty: Boolean = self match {
    case Empty => true
    case _     => false
  }

  def toByteBuf: Task[ByteBuf] = {
    self match {
      case HttpData.Incoming(_)        => ???
      case outgoing: HttpData.Outgoing =>
        outgoing match {
          case Text(text, charset)  => UIO(Unpooled.copiedBuffer(text, charset))
          case BinaryChunk(data)    => UIO(Unpooled.copiedBuffer(data.toArray))
          case BinaryByteBuf(data)  => UIO(data)
          case Empty                => UIO(Unpooled.EMPTY_BUFFER)
          case BinaryStream(stream) =>
            stream
              .asInstanceOf[ZStream[Any, Throwable, ByteBuf]]
              .fold(Unpooled.compositeBuffer())((c, b) => c.addComponent(b))
          case File(file)           =>
            effectBlocking {
              val fileContent = Files.readAllBytes(file.toPath)
              Unpooled.copiedBuffer(fileContent)
            }
        }
    }
  }
}

object HttpData {

  /**
   * Helper to create empty HttpData
   */
  def empty: HttpData = Empty

  /**
   * Helper to create HttpData from ByteBuf
   */
  def fromByteBuf(byteBuf: ByteBuf): HttpData = BinaryByteBuf(byteBuf)

  /**
   * Helper to create HttpData from chunk of bytes
   */
  def fromChunk(data: Chunk[Byte]): HttpData = BinaryChunk(data)

  /**
   * Helper to create HttpData from Stream of bytes
   */
  def fromStream(stream: ZStream[Any, Throwable, Byte]): HttpData =
    BinaryStream(stream.mapChunks(chunks => Chunk(Unpooled.wrappedBuffer(chunks.toArray))))

  /**
   * Helper to create HttpData from Stream of string
   */
  def fromStream(stream: ZStream[Any, Throwable, String], charset: Charset = HTTP_CHARSET): HttpData =
    BinaryStream(stream.map(str => Unpooled.copiedBuffer(str, charset)))

  def fromStreamByteBuf(stream: ZStream[Any, Throwable, ByteBuf]): HttpData =
    BinaryStream(stream)

  /**
   * Helper to create HttpData from String
   */
  def fromString(text: String, charset: Charset = HTTP_CHARSET): HttpData = Text(text, charset)

  /**
   * Helper to create HttpData from contents of a file
   */
  def fromFile(file: java.io.File): HttpData = File(file)

  final case class Incoming(unsafeRun: ((UnsafeChannel, UnsafeContent) => Unit) => Unit) extends HttpData

  sealed trait Outgoing extends HttpData
  object Outgoing {
    private[zhttp] final case class Text(text: String, charset: Charset)                   extends Outgoing
    private[zhttp] final case class BinaryChunk(data: Chunk[Byte])                         extends Outgoing
    private[zhttp] final case class BinaryByteBuf(data: ByteBuf)                           extends Outgoing
    private[zhttp] final case class BinaryStream(stream: ZStream[Any, Throwable, ByteBuf]) extends Outgoing
    private[zhttp] final case class File(file: java.io.File)                               extends Outgoing
    private[zhttp] case object Empty                                                       extends Outgoing
  }

}
