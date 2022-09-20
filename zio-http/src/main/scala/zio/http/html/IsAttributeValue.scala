package zio.http.html

import scala.language.implicitConversions
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * Checks if the value A can be represented as a valid html attribute.
 */
sealed trait IsAttributeValue[-A] {
  implicit def apply(a: A): String
}

object IsAttributeValue {
  implicit def fromString: IsAttributeValue[String] = new IsAttributeValue[String] {
    override def apply(a: String): String = a
  }

  implicit def fromInt: IsAttributeValue[Int] = new IsAttributeValue[Int] {
    override def apply(a: Int): String = a.toString
  }

  implicit def fromList: IsAttributeValue[Seq[String]] = new IsAttributeValue[Seq[String]] {
    override def apply(a: Seq[String]): String = a.mkString(" ")
  }

  implicit def fromTuple2Seq: IsAttributeValue[Seq[(String, String)]] = new IsAttributeValue[Seq[(String, String)]] {
    override def apply(a: Seq[(String, String)]): String =
      a.map { case (k, v) => s"""${k}:${v}""" }.mkString(";")
  }
}
