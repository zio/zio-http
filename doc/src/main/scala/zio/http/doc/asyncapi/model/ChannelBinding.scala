package zio.http.doc.asyncapi.model

/*
  Protocol specific definitions for a channel
 */
sealed trait ChannelBinding

object ChannelBinding {

  final case class Queue(
    name: String,
    `type`: String,
    durable: String,
    autoDelete: String,
    vHost: String
  )

  final case class Exchange(
    name: String,
    `type`: String,
    durable: String,
    autoDelete: String,
    vHost: String
  )

  final case class Amqp(
    is: String,
    exchange: Map[String, Exchange],
    queue: Map[String, Queue],
    bindingVersion: Version
  ) extends ChannelBinding

  final case object Http      extends ChannelBinding
  final case object WebSocket extends ChannelBinding
  final case object Kafka     extends ChannelBinding
  final case object Amqp1     extends ChannelBinding
  final case object Mqtt      extends ChannelBinding
  final case object Mqtt5     extends ChannelBinding
  final case object Nats      extends ChannelBinding
  final case object Jms       extends ChannelBinding
  final case object Sns       extends ChannelBinding
  final case object Sqs       extends ChannelBinding
  final case object Stomp     extends ChannelBinding
  final case object Redis     extends ChannelBinding
}
