package zio.http.doc.asyncapi.model

sealed trait ChannelBinding

object ChannelBinding {
  final case class Queue(
    queueName: String,
    queueType: String,
    queueDurable: String,
    queueAutoDelete: String,
    queueVHost: String
  )
  final case class Exchange(
    exchangeName: String,
    exchangeType: String,
    exchangeDurable: String,
    exchangeAutoDelete: String,
    exchangeVHost: String
  )
  final case class AMQP(
    is: String,
    exchange: Map[String, Exchange],
    queue: Map[String, Queue],
    bindingVersion: String
  )
  final case object HTTP      extends ChannelBinding
  final case object WEBSOCKET extends ChannelBinding
  final case object KAFKA     extends ChannelBinding
  final case object AMQP      extends ChannelBinding
  final case object AMQP1     extends ChannelBinding
  final case object MQTT5     extends ChannelBinding
  final case object NATS      extends ChannelBinding
  final case object JMS       extends ChannelBinding
  final case object SNS       extends ChannelBinding
  final case object SQS       extends ChannelBinding
  final case object STOMP     extends ChannelBinding
  final case object REDIS     extends ChannelBinding
  final case object MQTT      extends ChannelBinding
}
