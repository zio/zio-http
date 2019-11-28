package zio.http.doc.asyncapi.model

sealed trait MessageBinding

object MessageBinding {

  final case class HTTP(headers: Map[SchemaProperty, String], bindingVersion: String)         extends MessageBinding
  final case class MQTT(bindingVersion: Version)                                              extends MessageBinding
  final case class KAFKA(key: String, bindingVersion: String)                                 extends MessageBinding
  final case class AMQP(contentEncoding: String, messageType: String, bindingVersion: String) extends MessageBinding

  final case object WEBSOCKET extends MessageBinding
  final case object KAFKA     extends MessageBinding
  final case object AMQP1     extends MessageBinding
  final case object MQTT5     extends MessageBinding
  final case object NATS      extends MessageBinding
  final case object JMS       extends MessageBinding
  final case object SNS       extends MessageBinding
  final case object SQS       extends MessageBinding
  final case object STOMP     extends MessageBinding
  final case object REDIS     extends MessageBinding
  final case object MQTT      extends MessageBinding
}
