package zio-http.domain.mqtt

import org.eclipse.paho.client.{mqttv3 => jmqtt}
import zio.UIO

import java.nio.charset.Charset

final case class MqttMessage(val asJava: jmqtt.MqttMessage) extends AnyVal {

  /**
   * Returns the payload as a byte array.
   */
  def payload: UIO[Array[Byte]] = UIO(asJava.getPayload)

  /**
   * Returns a string representation of this message's payload.
   */
  def asString: UIO[String] = UIO(asJava.toString)

}

object MqttMessage {

  /**
   * Creates a new `MqttMessage` using the provided string
   */
  def fromString(msg: String, charset: Charset = Charset.defaultCharset()): UIO[MqttMessage] =
    UIO(new MqttMessage(new jmqtt.MqttMessage(msg.getBytes(charset))))
}
