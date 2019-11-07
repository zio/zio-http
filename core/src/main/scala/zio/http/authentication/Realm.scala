package zio.http.authentication

final case class Realm(key: String, value: String) {
  override def toString = s"$key=$value"
}
