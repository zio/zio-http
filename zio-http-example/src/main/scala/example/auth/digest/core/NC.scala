package example.auth.digest.core

case class NC(value: Int) extends AnyVal {
  override def toString: String = toHexString

  private def toHexString: String = f"$value%08x"
}

object NC {
  implicit val ordering: Ordering[NC] = Ordering.by(_.value)
}
