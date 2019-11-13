package zio.http.model

case class ContentType(`type`: String) extends AnyVal {
  override def toString: String = `type`
}

object ContentType {
  val Plain       = ContentType("text/plain")
  val HTML        = ContentType("text/html")
  val CSV         = ContentType("text/csv")
  val XML         = ContentType("text/xml")
  val JSON        = ContentType("application/json")
  val OctetStream = ContentType("application/octet-stream")
  val Form        = ContentType("application/x-www-form-urlencoded")
}
