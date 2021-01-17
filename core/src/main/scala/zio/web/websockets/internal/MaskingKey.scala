package zio.web.websockets.internal

import zio.{ Chunk, Has, RIO, UIO, URLayer, ZLayer }
import zio.random.Random

object MaskingKey {

  trait Service {
    def get: UIO[Chunk[Byte]]
  }

  val live: URLayer[Random, Has[MaskingKey.Service]] =
    ZLayer.fromService { random =>
      new Service {
        override def get: UIO[Chunk[Byte]] =
          random.nextDouble.map { dbl =>
            val maskKey = (dbl * Int.MaxValue).toInt

            Chunk(
              ((maskKey >> 24) & 0xFF).toByte,
              ((maskKey >> 16) & 0xFF).toByte,
              ((maskKey >> 8) & 0xFF).toByte,
              (maskKey & 0xFF).toByte
            )
          }
      }
    }

  def get: RIO[Has[MaskingKey.Service], Chunk[Byte]] =
    RIO.accessM(_.get[Service].get)
}
