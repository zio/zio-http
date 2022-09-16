package zio.http.model

sealed trait Version { self =>
  def isHttp1_0: Boolean = self == Version.Http_1_0

  def isHttp1_1: Boolean = self == Version.Http_1_1

}

object Version {
  val `HTTP/1.0`: Version = Http_1_0
  val `HTTP/1.1`: Version = Http_1_1

  case object Http_1_0 extends Version

  case object Http_1_1 extends Version
}
