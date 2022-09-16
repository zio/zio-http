package zio.http.api

import zio.stream.ZStream
import zio.schema.Schema

/**
 * A [[zio.http.api.Out]] describes the output of an HTTP endpoint. Outputs may
 * be single values, or they may be streams of some element type.
 */
sealed trait Out[Output]
object Out {
  def stream[A](implicit schema: Schema[A]): Out[ZStream[Any, Throwable, A]] = Stream(schema)

  val unit: Out[Unit] = value[Unit]

  def value[A](implicit schema: Schema[A]): Out[A] = Value(schema)

  final case class Value[Output](schema: Schema[Output])    extends Out[Output]
  final case class Stream[Element](schema: Schema[Element]) extends Out[ZStream[Any, Throwable, Element]]
}
