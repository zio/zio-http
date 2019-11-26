package zio.http.asyncapi.model

sealed abstract class Protocol(val name: String) {
  override def toString: String = name
}

object Protocol {
  final case object HTTP         extends Protocol("http")
  final case object HTTPS        extends Protocol("https")
  final case object JMS          extends Protocol("jms")
  final case object KAFKA        extends Protocol("kafka")
  final case object KAFKA_SECURE extends Protocol("kafka-secure")
  final case object MQTT         extends Protocol("mqtt")
  final case object SECURE_MQTT  extends Protocol("secure-mqtt")
  final case object STOMP        extends Protocol("stomp")
  final case object STOMPS       extends Protocol("stomps")
  final case object WS           extends Protocol("ws")
  final case object WSS          extends Protocol("wss")
}
