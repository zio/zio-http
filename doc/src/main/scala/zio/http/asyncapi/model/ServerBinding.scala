package zio.http.asyncapi.model

trait HTTPBinding
trait WebSocketBinding
trait KafkaBinding
trait AmqpBinding
trait Amqp1Binding
case class MqttBinding(
  clientId: String,
  cleanSession: Boolean,
  lastWill: Any,
  topic: String,
  qos: Int,
  retain: Boolean,
  keepAlive: Int,
  bindingVersion: String
)
trait Mqtt5
trait Nats
trait Jms
trait Sns
trait Sqs
trait Stomp
trait Redis

case class ServerBinding(
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
