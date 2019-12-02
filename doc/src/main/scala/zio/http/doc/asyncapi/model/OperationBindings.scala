package zio.http.doc.asyncapi.model

/*
  Protocol specific definitions for an operation
 */
sealed trait OperationBinding

object OperationBinding {

  sealed trait OperationType

  object OperationType {
    final case object Request  extends OperationType
    final case object Response extends OperationType
  }

  sealed abstract class Method(val name: String) {
    override def toString: String = name
  }

  object Method {
    final case object GET     extends Method("GET")
    final case object HEAD    extends Method("HEAD")
    final case object POST    extends Method("POST")
    final case object PUT     extends Method("PUT")
    final case object DELETE  extends Method("DELETE")
    final case object CONNECT extends Method("CONNECT")
    final case object OPTIONS extends Method("OPTIONS")
    final case object TRACE   extends Method("TRACE")
    final case object PATCH   extends Method("PATCH")
  }

  final case class Http(
    `type`: OperationType,
    method: Method,
    query: Map[SchemaProperty, String],
    bindingVersion: Version
  ) extends OperationBinding

  final case class Kafka(groupId: String, clientId: String, bindingVersion: Version) extends OperationBinding

  final case class Mqtt(qos: Int, retain: Boolean, bindingVersion: String) extends OperationBinding

  final case class Amqp(
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
  ) extends OperationBinding

  final case object WebSocket extends OperationBinding
  final case object Amqp1     extends OperationBinding
  final case object Mqtt5     extends OperationBinding
  final case object Nats      extends OperationBinding
  final case object Jms       extends OperationBinding
  final case object Sns       extends OperationBinding
  final case object Sqs       extends OperationBinding
  final case object Stomp     extends OperationBinding
  final case object Stomps    extends OperationBinding
  final case object Redis     extends OperationBinding

}
