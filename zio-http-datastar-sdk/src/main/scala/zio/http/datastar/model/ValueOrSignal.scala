package zio.http.datastar.model
import scala.language.implicitConversions

import zio.schema.Schema

import zio.http.datastar.signal.Signal

sealed trait ValueOrSignal[T] {
  def isValue: Boolean = this match {
    case ValueOrSignal.Value(_)       => true
    case ValueOrSignal.SignalValue(_) => false
  }
}

object ValueOrSignal {
  final case class Value[T](value: T)                extends ValueOrSignal[T]
  final case class SignalValue[T](signal: Signal[T]) extends ValueOrSignal[T]

  implicit def fromValue[T](value: T): ValueOrSignal[T]           = Value(value)
  implicit def fromSignal[T](signal: Signal[T]): ValueOrSignal[T] = SignalValue(signal)
}
