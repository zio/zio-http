package zio.http.datastar.model
import scala.language.implicitConversions

import zio.schema.Schema

import zio.http.datastar.signal.Signal

/**
 * Either a plain value of type `T` or a `Signal` of `T` that stands for it.
 * Implicit conversions from both let a method such as `datastarRequest` accept
 * either.
 */
sealed trait ValueOrSignal[T] {

  /** Returns `true` for a `Value` and `false` for a `SignalValue`. */
  def isValue: Boolean = this match {
    case ValueOrSignal.Value(_)       => true
    case ValueOrSignal.SignalValue(_) => false
  }
}

/** Cases and implicit conversions of `ValueOrSignal`. */
object ValueOrSignal {

  /** A plain value. */
  final case class Value[T](value: T) extends ValueOrSignal[T]

  /** A signal that stands for a value. */
  final case class SignalValue[T](signal: Signal[T]) extends ValueOrSignal[T]

  /** Wraps a plain value as a `Value`. */
  implicit def fromValue[T](value: T): ValueOrSignal[T] = Value(value)

  /** Wraps a signal as a `SignalValue`. */
  implicit def fromSignal[T](signal: Signal[T]): ValueOrSignal[T] = SignalValue(signal)
}
