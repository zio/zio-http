package zio-http.domain.mqtt

import zio.ZIO

case class Mqtt[-R, -T, -M] private (run: Event[T, M] => ZIO[R, Unit, Unit]) { self =>

  /**
   * Pick either one of the 2 MqttCallbacks
   */
  def <>[R1 <: R, T1 <: T, M1 <: M](other: Mqtt[R1, T1, M1]): Mqtt[R1, T1, M1] =
    Mqtt(event => self.run(event) <> other.run(event))

  /**
   * Combine both the MqttCallbacks
   */
  def ++[R1 <: R, T1 <: T, M1 <: M](other: Mqtt[R1, T1, M1]): Mqtt[R1, T1, M1] =
    Mqtt(event => self.run(event).either *> other.run(event))
}

object Mqtt {
  def of[T, M]: MqttCallbackPF[T, M] = MqttCallbackPF[T, M](())

  final case class MqttCallbackPF[T, M](u: Unit) extends AnyVal {
    def apply[R](pf: PartialFunction[Event[T, M], ZIO[R, Nothing, Unit]]): Mqtt[R, T, M] =
      Mqtt[R, T, M](event => if (pf.isDefinedAt(event)) pf.apply(event) else ZIO.fail(()))
  }
}
