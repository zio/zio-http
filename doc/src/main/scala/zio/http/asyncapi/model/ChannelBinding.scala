package zio.http.asyncapi.model

trait HTTPBinding
trait WebSocketBinding
trait KafkaBinding
case class AmqpBinding(
  is: String,
  exchange: Map[String, Any],
  exchangeName: String,
  exchangeType: String,
  exchangeDurable: String,
  exchangeAutoDelete: String,
  exchangeVHost: String,
  queue: Map[String, Any],
  queueName: String,
  queueType: String,
  queueDurable: String,
  queueAutoDelete: String,
  queueVHost: String,
  bindingVersion: String
)
trait MqttBinding
trait Mqtt5
trait Nats
trait Jms
trait Sns
trait Sqs
trait Stomp
trait Redis

case class ChannelBinding(
  http: HTTPBinding,
  ws: WebSocketBinding,
  kafka: KafkaBinding,
  amqp: AmqpBinding,
  amqp1: Amqp1Binding,
  mqtt: MqttBinding,
  mqtt5: Mqtt5,
  nats: Nats,
  jms: Jms,
  sns: Sns,
  sqs: Sqs,
  stomp: Stomp,
  redis: Redis
)
