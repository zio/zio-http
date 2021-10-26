package zhttp.endpoint

sealed trait Parameter[+A] { self =>
  def parse(string: String): Option[A] = Parameter.extract(self, string)
}
object Parameter           {
  private[zhttp] final case class Literal(s: String)         extends Parameter[Unit]
  private[zhttp] final case class Param[A](r: CanExtract[A]) extends Parameter[A]

  private[zhttp] def extract[A](rt: Parameter[A], string: String): Option[A] = rt match {
    case Parameter.Literal(s) => if (s == string) Endpoint.unit else None
    case Parameter.Param(r)   => r.parse(string)
  }

  /**
   * Creates a new Parameter placeholder for an Endpoint
   */
  def apply[A](implicit ev: CanExtract[A]): Parameter[A] = Parameter.Param(ev)
}
