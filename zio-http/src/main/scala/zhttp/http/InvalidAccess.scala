package zhttp.http

final case class InvalidAccess(property: String, obj: Any) extends Throwable {
  override def getMessage(): String = s"""The property "$property" is unavailable on: $obj"""
}
