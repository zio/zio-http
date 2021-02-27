package zio-http.domain.data

import io.circe.Json

/**
 * Models internal subscription metrics for debugging purposes
 */
case class SubscriptionMetrics private (
  totalTopicCount: Long,
  totalSubscriptionCount: Long,
  subscriptions: List[(String, Long)],
) { self =>
  def toJson: Json = SubscriptionMetrics.encode(self)
}

object SubscriptionMetrics {
  import io.circe.syntax._
  import io.circe.generic.auto._

  def apply(s: List[(String, Long)]): SubscriptionMetrics =
    SubscriptionMetrics(
      s.length.toLong,
      s.map({ case (_, s) => s }).sum,
      s,
    )

  /**
   * Encodes SubscriptionMetrics into JSON format
   */
  def encode(m: SubscriptionMetrics): Json = m.asJson
}
