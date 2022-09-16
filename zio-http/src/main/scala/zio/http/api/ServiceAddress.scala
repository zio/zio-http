package zio.http.api

sealed trait ServiceAddress
object ServiceAddress {
  def apply(server: String, port: Int): ServiceAddress = ServiceAddress.ServerPort(server, port)

  final case class ServerPort(server: String, port: Int) extends ServiceAddress
}
