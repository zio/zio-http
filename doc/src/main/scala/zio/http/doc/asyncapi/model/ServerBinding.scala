package zio.http.doc.asyncapi.model

/**
 * Protocol-specific definitions for a server.
 */
sealed trait ServerBinding

object ServerBinding {
  final case object HTTP      extends ServerBinding
  final case object WEBSOCKET extends ServerBinding
  final case object KAFKA     extends ServerBinding
  final case object AMQP      extends ServerBinding
  final case object AMQP1     extends ServerBinding
  final case object MQTT5     extends ServerBinding
  final case object NATS      extends ServerBinding
  final case object JMS       extends ServerBinding
  final case object SNS       extends ServerBinding
  final case object SQS       extends ServerBinding
  final case object STOMP     extends ServerBinding
  final case object REDIS     extends ServerBinding
  final case class MQTT(
    clientId: String,
    cleanSession: Boolean,
    lastWill: LastWill,
    keepAlive: Int,
    bindingVersion: String
  )
  final case class LastWill(topic: String, qos: Int, retain: Boolean)
}
