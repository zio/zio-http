package zhttp.endpoint

import scala.util.Try

trait CanExtract[+A] {
  def parse(data: String): Option[A]
}
object CanExtract    {
  implicit object IntImpl     extends CanExtract[Int]     {
    override def parse(data: String): Option[Int] = Try(data.toInt).toOption
  }
  implicit object StringImpl  extends CanExtract[String]  {
    override def parse(data: String): Option[String] = Option(data)
  }
  implicit object BooleanImpl extends CanExtract[Boolean] {
    override def parse(data: String): Option[Boolean] = Try(data.toBoolean).toOption
  }
}
