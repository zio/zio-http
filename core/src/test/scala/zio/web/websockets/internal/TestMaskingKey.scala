package zio.web.websockets.internal

import zio._

object TestMaskingKey {

  trait Service {
    def feed(maskingKey: Chunk[Byte]): UIO[Unit]
  }

  val live: ULayer[Has[MaskingKey.Service] with Has[Service]] =
    Ref
      .make(Chunk[Byte]())
      .map(ref => Has.allOf[MaskingKey.Service, Service](Test(ref), Test(ref)))
      .toLayerMany

  case class Test(ref: Ref[Chunk[Byte]]) extends MaskingKey.Service with Service {

    override def feed(maskingKey: Chunk[Byte]): UIO[Unit] =
      ref.update(_ => maskingKey)

    override def get: UIO[Chunk[Byte]] =
      ref.get
  }

  def feed(maskingKey: Chunk[Byte]): ZIO[Has[Service], Nothing, Unit] =
    ZIO.accessM(_.get.feed(maskingKey))
}
