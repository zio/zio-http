package zio.http

trait ClientPackageExtensions {
  type Client = ZClient[Any, Body, Throwable, Response]
  def Client: ZClient.type = ZClient
}
