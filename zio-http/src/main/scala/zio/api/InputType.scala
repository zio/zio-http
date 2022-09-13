package zhttp.api

import zio.schema.Schema
import zio.stream.ZStream

object InputType {
  final case class ZIOInput[A](schema: Schema[A]) extends InputType[A]
  final case object StreamInput                   extends InputType[ZStream[Any, Throwable, Byte]]
}

sealed trait InputType[A] extends Product with Serializable { self =>
  def isUnit: Boolean = self.asInstanceOf[InputType[_]] match {
    case InputType.ZIOInput(schema) => schema == Schema[Unit]
    case InputType.StreamInput      => false
  }
}
