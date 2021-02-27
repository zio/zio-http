package zio-http.domain.mqtt

/**
 * Mqtt ClientEvents. To be fired inside MqttCallback methods
 */
sealed trait Event[+T, +M]
object Event {
  sealed trait User[+T, +M] extends Event[T, M]
  object User {
    case class Subscribed[T](topic: T)            extends User[T, Nothing]
    case class Unubscribed[T](topic: T)           extends User[T, Nothing]
    case class Published[T, M](topic: T, data: M) extends User[T, M]
  }

  sealed trait Broker[+T, +M] extends Event[T, M]
  object Broker {
    case class ConnectComplete(reconnect: Boolean, serverURI: String) extends Broker[Nothing, Nothing]
    case class ConnectionLost(cause: Throwable)                       extends Broker[Nothing, Nothing]
    case class MessageArrived[T, M](topic: T, data: M)                extends Broker[T, M]
    case object DeliveryComplete                                      extends Broker[Nothing, Nothing]
  }
}
