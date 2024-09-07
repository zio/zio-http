package zio.http.endpoint.cli

import zio._

import zio.http._

/**
 * Represents a Request. The body parameter allows implementation of multipart
 * form data and the retrieval of a body from a file or an URL.
 */

private[cli] final case class CliRequest(
  body: Chunk[Retriever],
  headers: Headers,
  method: Method,
  url: URL,
  outputResponse: Boolean = true,
  saveResponse: Boolean = false,
) { self =>

  def addBody(value: Retriever): CliRequest =
    self.copy(body = self.body ++ Chunk(value))

  def addHeader(name: String, value: String): CliRequest =
    self.copy(headers = self.headers.addHeader(name, value))

  def addPathParam(value: String): CliRequest =
    self.copy(url = self.url.copy(path = self.url.path / value))

  def addQueryParam(key: String, value: String): CliRequest =
    self.copy(url = self.url.setQueryParams(self.url.queryParams.addQueryParam(key, value)))

  def method(method: Method): CliRequest =
    self.copy(method = method)

  /*
   * Retrieves data from files, urls or command options and construct a HTTP Request.
   */
  def toRequest(host: String, port: Int, retrieverClient: CliClient): Task[Request] = {
    val clientLayer = retrieverClient match {
      case CliZIOClient(client)    => ZLayer { ZIO.succeed(client) }
      case CliZLayerClient(client) => client
      case DefaultClient()         => Client.default
    }
    for {

      forms     <- ZIO.foreach(body)(_.retrieve()).provide(clientLayer)
      finalBody <- Body.fromMultipartFormUUID(Form(forms))
    } yield Request(method = method, url = url.host(host).port(port), body = finalBody, headers = headers)
  }

}

private[cli] object CliRequest {

  val empty: CliRequest = CliRequest(Chunk.empty, Headers.empty, Method.GET, URL.empty)

}
