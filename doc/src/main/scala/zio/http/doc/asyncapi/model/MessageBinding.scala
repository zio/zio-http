package zio.http.doc.asyncapi.model

/*
  Protocol specific bindings for a message
 */
sealed trait MessageBinding

object MessageBinding {

  final case class Http(headers: Map[SchemaProperty, String], bindingVersion: String)         extends MessageBinding
  final case class Mqtt(bindingVersion: Version)                                              extends MessageBinding
  final case class Kafka(key: String, bindingVersion: String)                                 extends MessageBinding
  final case class Amqp(contentEncoding: String, messageType: String, bindingVersion: String) extends MessageBinding

  final case object WebSocket extends MessageBinding
  final case object Kafka     extends MessageBinding
  final case object Amqp1     extends MessageBinding
  final case object Mqtt      extends MessageBinding
  final case object Mqtt5     extends MessageBinding
  final case object Nats      extends MessageBinding
  final case object Jms       extends MessageBinding
  final case object Sns       extends MessageBinding
  final case object Sqs       extends MessageBinding
  final case object Stomp     extends MessageBinding
  final case object Redis     extends MessageBinding

}
