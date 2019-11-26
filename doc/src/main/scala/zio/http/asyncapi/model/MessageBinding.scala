package zio.http.asyncapi.model

import zio.http.asyncapi.model.A.SchemaObject
import zio.http.doc.asyncapi.A.SchemaObject

case class HTTPBinding(headers: SchemaObject, bindingVersion: String)
trait WebSocketBinding
case class KafkaBinding(key: String, bindingVersion: String)
case class AmqpBinding(contentEncoding: String, messageType: String, bindingVersion: String)
trait Amqp1Binding
case class MqttBinding(bindingVersion: String)
trait Mqtt5
trait Nats
trait Jms
trait Sns
trait Sqs
trait Stomp
trait Redis

case class MessageBinding(
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
