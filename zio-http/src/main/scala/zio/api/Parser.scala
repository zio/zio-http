package zhttp.api

import java.util.UUID

trait Parser[A] {
  def parse(input: String): Option[A]
}

object Parser {
  def apply[A](implicit parser: Parser[A]): Parser[A] = parser

  implicit val stringParser: Parser[String] = new Parser[String] {
    override def parse(input: String): Option[String] = Some(input)
  }

  implicit val intParser: Parser[Int] = new Parser[Int] {
    override def parse(input: String): Option[Int] = input.toIntOption
  }

  implicit val booleanParser: Parser[Boolean] = new Parser[Boolean] {
    override def parse(input: String): Option[Boolean] = input.toBooleanOption
  }

  implicit val uuidParser: Parser[UUID] = new Parser[UUID] {
    override def parse(input: String): Option[UUID] =
      try Some(UUID.fromString(input))
      catch {
        case _: IllegalArgumentException => None
      }
  }
}
