package zio-http.domain.mqtt

/**
 * Represents a topic name
 */
final case class MqttTopic(name: String) extends AnyVal

object MqttTopic {
  implicit def toMqttTopic(s: String): MqttTopic = MqttTopic(s)
}
