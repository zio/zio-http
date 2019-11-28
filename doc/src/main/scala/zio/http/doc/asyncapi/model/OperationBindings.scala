package zio.http.doc.asyncapi.model

sealed trait OperationBinding

object OperationBinding {
  final case class HTTP(`type`: String, method: String, query: Map[SchemaProperty, String], bindingVersion: String)
      extends OperationBinding
  final case class KAFKA(groupId: String, clientId: String, bindingVersion: String) extends OperationBinding
  final case class MQTT(qos: Int, retain: Boolean, bindingVersion: String)          extends OperationBinding
  final case class AMQP(
    expiration: Int,
    userId: String,
    cc: List[String],
    priority: Int,
    deliveryMode: Int,
    mandatory: Boolean,
    bcc: List[String],
    replyTo: String,
    timestamp: Boolean,
    ack: Boolean,
    bindingVersion: Version
  )
  final case object WEBSOCKET extends OperationBinding
  final case object AMQP1     extends OperationBinding
  final case object MQTT5     extends OperationBinding
  final case object NATS      extends OperationBinding
  final case object JMS       extends OperationBinding
  final case object SNS       extends OperationBinding
  final case object SQS       extends OperationBinding
  final case object STOMP     extends OperationBinding
  final case object REDIS     extends OperationBinding

}
