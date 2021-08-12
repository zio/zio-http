package zhttp.experiment

case class Check[-A](is: A => Boolean) { self =>
  def &&[A1 <: A](other: Check[A1]): Check[A1]     = Check(a => self.is(a) && other.is(a))
  def ||[A1 <: A](other: Check[A1]): Check[A1]     = self orElse other
  def orElse[A1 <: A](other: Check[A1]): Check[A1] = Check(a => self.is(a) || other.is(a))
}

object Check {
  def isTrue: Check[Any]  = Check(_ => true)
  def isFalse: Check[Any] = Check(_ => false)

  def startsWith(prefix: String): Check[AnyRequest] = Check(_.url.path.asString.startsWith(prefix))
}
