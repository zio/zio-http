package zio.http.asyncapi.model

import A.SchemaObject

case class HTTPBinding(`type`: String, method: String, query: SchemaObject, bindingVersion: String)
trait WebSocketBinding
case class KafkaBinding(groupId: String, clientId: String, bindingVersion: String)
case class AmqpBinding(
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
  bindingVersion: String
)
trait Amqp1Binding
case class MqttBinding(qos: Int, retain: Boolean, bindingVersion: String)
trait Mqtt5
trait Nats
trait Jms
trait Sns
trait Sqs
trait Stomp
trait Redis

case class OperationBinding(
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
