package zio.http.api

import zio.schema.Schema
import zio.stream.ZStream
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * A [[zio.http.api.Out]] describes the output of an HTTP endpoint. Outputs may
 * be single values, or they may be streams of some element type.
 */
sealed trait Out[Output] {
  type Atom
  type Type

  def bodySchema: Schema[Atom]
}
object Out               {
  def stream[A](implicit schema: Schema[A]): Out[ZStream[Any, Throwable, A]] = Stream(schema)

  val unit: Out[Unit] = value[Unit]

  def value[A](implicit schema: Schema[A]): Out[A] = Value(schema)

  final case class Value[Output](schema: Schema[Output])    extends Out[Output]                           {
    type Atom = Output
    type Type = Output

    override def bodySchema: Schema[Output] = schema
  }
  final case class Stream[Element](schema: Schema[Element]) extends Out[ZStream[Any, Throwable, Element]] {
    type Atom = Element
    type Type = ZStream[Any, Throwable, Element]

    override def bodySchema: Schema[Element] = schema
  }
}
