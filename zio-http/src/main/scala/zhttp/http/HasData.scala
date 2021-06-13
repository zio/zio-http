package zhttp.http

import zio.Chunk
import zio.stream.ZStream

import scala.annotation.implicitAmbiguous
import scala.annotation.implicitNotFound

/**
 * Extracts data from `Content`
 */
@implicitNotFound("data is unavailable on this type of content")
sealed trait HasData[-A] {
  type Out[-R, +E]
  def data[R, E, A1 <: A](content: Content[R, E, A1]): Out[R, E]
}

object HasData {
  import Content._

  implicit case object Complete extends HasData[Complete] {
    override type Out[-R, +E] = Chunk[Byte]
    override def data[R, E, A1 <: Complete](content: Content[R, E, A1]): Out[R, E] = content match {
      case CompleteContent(bytes) => bytes
      case _                      => throw new Error("Data is Unavailable")
    }
  }

  @implicitAmbiguous("data is unavailable on this type of content")
  implicit case object Buffered extends HasData[Buffered] {
    override type Out[-R, +E] = ZStream[R, E, Byte]
    override def data[R, E, A1 <: Buffered](content: Content[R, E, A1]): Out[R, E] = content match {
      case BufferedContent(source) => source
      case _                       => throw new Error("Data is Unavailable")
    }
  }

  def apply[A: HasData]: HasData[A] = implicitly[HasData[A]]
}
