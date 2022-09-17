package zio.http.api

sealed trait APIAddress
object APIAddress {
  def apply(server: String, port: Int): APIAddress = APIAddress.ServerPort(server, port)

  final case class ServerPort(server: String, port: Int) extends APIAddress
}
