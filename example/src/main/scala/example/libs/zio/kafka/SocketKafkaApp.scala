package example.libs.zio.kafka

import org.apache.kafka.clients.producer.ProducerRecord
import zhttp.http._
import zhttp.service._
import zhttp.socket._
import zio._
import zio.kafka.consumer._
import zio.kafka.producer._
import zio.kafka.serde._
import zio.stream._
import zio.system._

import java.util.UUID

/**
 * SocketKafkaApp is an echo app that produces messages to a Kafka topic, consumes them, and sends them back to the
 * client via a web socket connection. Bootstrap Server(KAFKA_BOOTSTRAP_SERVER), Consumer Group Id(KAFKA_GROUP_ID),
 * Topic(KAFKA_TOPIC), and the port(ZHTTP_PORT) to run the server on is picked up from environment variables.
 *
 * ```
 * export KAFKA_BOOTSTRAP_SERVER="localhost:9092"
 * export KAFKA_GROUP_ID="zhttp"
 * export KAFKA_TOPIC="zhttp"
 * export ZHTTP_PORT=80
 *
 * sbt 'project example; runMain example.libs.zio.kafka.SocketKafkaApp'
 * ```
 */
trait KafkaApp[K, V] extends zio.App { self =>
  def record(topic: String, key: K, value: V) = new ProducerRecord[K, V](topic, key, value)

  def produce(keySerializer: Serde[Any, K], valueSerializer: Serde[Any, V]) = Producer
    .produceAll(keySerializer, valueSerializer)

  def consume(
    topic: String,
    keySerializer: Serde[Any, K],
    valueSerializer: Serde[Any, V],
  ) = Consumer
    .subscribeAnd(Subscription.topics(topic))
    .plainStream(keySerializer, valueSerializer)
}

object SocketKafkaApp extends KafkaApp[UUID, String] {
  def socket =
    Socket.collect[WebSocketFrame] { case WebSocketFrame.Text(msg) =>
      ZStream
        .fromEffect(env("KAFKA_TOPIC"))
        .map(_.fold("zhttp")(identity))
        .map(topic => record(topic, UUID.randomUUID(), msg))
        .transduce(produce(Serde.uuid, Serde.string))
        .flatMap(rm => consume(rm.topic(), Serde.uuid, Serde.string))
        .map(cr => WebSocketFrame.text(cr.value.toString))
    }

  def app = Http.collect[Request] { case Method.GET -> !! / "subscription" =>
    Response.socket(socket)
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    envs.flatMap { env =>
      val consumerSettings = ConsumerSettings(List(env("KAFKA_BOOTSTRAP_SERVER")))
        .withGroupId("zhttp")
      val producerSettings = ProducerSettings(List(env("KAFKA_BOOTSTRAP_SERVER")))

      val consumer = ZLayer.fromManaged(Consumer.make(consumerSettings))
      val producer = ZLayer.fromManaged(Producer.make(producerSettings))

      Server
        .start(env("ZHTTP_PORT").toInt, app)
        .provideCustomLayer(consumer ++ producer)
    }.exitCode
}
