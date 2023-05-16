package zio.http.endpoint.cli

import zio._
import zio.json.ast._
import java.nio.file.Path

import zio.http._
import zio.cli._
import java.nio.channels.FileChannel

import zio.stream.{ZSink, ZStream}

import java.io.File
import scala.io.Source
import java.io.IOException



private[cli] final case class CliRequest(
  body: Chunk[Either[Either[(String, Path, MediaType), String], FormField]],
  headers: Headers,
  method: Method,
  url: URL,
  printResponse: Boolean = false,
  saveResponse: Boolean = false
) { self =>

  def addBody(value: Either[Either[(String, Path, MediaType), String], FormField]) =
    self.copy(body = self.body ++ Chunk(value))

  def addHeader(name: String, value: String): CliRequest =
    self.copy(headers = self.headers.addHeader(name, value))
    

  def addPathParam(value: String) =
    self.copy(url = self.url.copy(path = self.url.path / value))

  def addQueryParam(key: String, value: String) =
    self.copy(url = self.url.withQueryParams(self.url.queryParams.add(key, value)))

  def withMethod(method: Method): CliRequest =
    self.copy(method = method)

  def toRequest(host: String, port: Int): Task[Request] = for {
    formFields <- ZIO.foreach(body)( _ match {
        case Left(Left((name, file, mediaType))) => for {
          chunk <- Body.fromFile(new File(file.toUri())).asChunk
        } yield FormField.binaryField(name, chunk, mediaType)
        case Left(Right(url)) => ???
        case Right(formField) => ZIO.succeed(formField)
      })
    finalBody <- Body.fromMultipartFormUUID(Form(formFields))
  } yield Request
      .default(
        method,
        url.withHost(host).withPort(port),
        finalBody,
        )
      .setHeaders(headers)
  
}

private[cli] object CliRequest {

  val empty = CliRequest(Chunk.empty, Headers.empty, Method.GET, URL.empty)

}
