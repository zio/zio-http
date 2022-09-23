package zio.http.model

sealed trait Version extends Any { self =>
  def isHttp1_0: Boolean = self == Version.Http_1_0

  def isHttp1_1: Boolean = self == Version.Http_1_1

}

object Version {
  val `HTTP/1.0`: Version = Http_1_0
  val `HTTP/1.1`: Version = Http_1_1

  case object Http_1_0 extends Version

  case object Http_1_1 extends Version

  /**
   * As of the time this was implemented, Netty support HTTP/1.0 and HTTP/1.1.
   *
   * However, being Java, Netty offers compile guarantee to guard with so this
   * is used an "escape hatch" to avoid having to throw exceptions.
   *
   * @param version
   */
  final case class Unsupported(text: String) extends AnyVal with Version
}
