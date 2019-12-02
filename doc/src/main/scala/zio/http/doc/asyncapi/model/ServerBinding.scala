package zio.http.doc.asyncapi.model

/**
 * Protocol-specific definitions for a server.
 */
sealed trait ServerBinding

object ServerBinding {
  final case object Http      extends ServerBinding
  final case object WebSocket extends ServerBinding
  final case object Kafka     extends ServerBinding
  final case object Amqp      extends ServerBinding
  final case object Amqp1     extends ServerBinding
  final case object Mqtt5     extends ServerBinding
  final case object Nats      extends ServerBinding
  final case object Jms       extends ServerBinding
  final case object Sns       extends ServerBinding
  final case object Sqs       extends ServerBinding
  final case object Stomp     extends ServerBinding
  final case object Redis     extends ServerBinding
  final case class Mqtt(
    clientId: String,
    cleanSession: Boolean,
    lastWill: LastWill,
    keepAlive: Int,
    bindingVersion: String
  )
  final case class LastWill(topic: String, qos: Int, retain: Boolean)
}
