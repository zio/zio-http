package zio.http.endpoint.cli

import java.io.{File, IOException}
import java.nio.channels.FileChannel
import java.nio.file.Path

import scala.io.Source

import zio._
import zio.cli._
import zio.json.ast._

import zio.stream.{ZSink, ZStream}

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

  def addBody(value: Retriever) =
    self.copy(body = self.body ++ Chunk(value))

  def addHeader(name: String, value: String): CliRequest =
    self.copy(headers = self.headers.addHeader(name, value))

  def addPathParam(value: String) =
    self.copy(url = self.url.copy(path = self.url.path / value))

  def addQueryParam(key: String, value: String) =
    self.copy(url = self.url.queryParams(self.url.queryParams.add(key, value)))

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

  val empty = CliRequest(Chunk.empty, Headers.empty, Method.GET, URL.empty)

}
