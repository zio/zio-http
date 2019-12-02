package zio.http.doc.asyncapi.model

/*
  Supported protocols for connectivity
 */
sealed abstract class Protocol(val name: String) {
  override def toString: String = name
}

object Protocol {
  final case object Http        extends Protocol("http")
  final case object Https       extends Protocol("https")
  final case object Jms         extends Protocol("jms")
  final case object Kafka       extends Protocol("kafka")
  final case object KafkaSecure extends Protocol("kafka-secure")
  final case object Mqtt        extends Protocol("mqtt")
  final case object SecureMqtt  extends Protocol("secure-mqtt")
  final case object Stomp       extends Protocol("stomp")
  final case object Stomps      extends Protocol("stomps")
  final case object Ws          extends Protocol("ws")
  final case object Wss         extends Protocol("wss")
}
