package zio.http.endpoint.cli

import zio._
import zio.json.ast._

import zio.http._

private[cli] final case class CliRequest(
  url: URL,
  method: Method,
  headers: Headers,
  body: Json,
) { self =>
  def addFieldToBody(prefix: List[String], value: Json) = {
    def sparseJson(prefix: List[String], json: Json): Json =
      prefix match {
        case Nil          => json
        case head :: tail => Json.Obj(Chunk(head -> sparseJson(tail, json)))
      }

    self.copy(body = self.body.merge(sparseJson(prefix, value)))
  }

  def addHeader(name: String, value: String): CliRequest =
    self.copy(headers = self.headers.addHeader(name, value))

  def addPathParam(value: String) =
    self.copy(url = self.url.copy(path = self.url.path / value))

  def addQueryParam(key: String, value: String) =
    self.copy(url = self.url.queryParams(self.url.queryParams.add(key, value)))

  def addQueryParams(key: String, values: NonEmptyChunk[String]) =
    self.copy(url = self.url.queryParams(self.url.queryParams.addAll(key, values)))

  def method(method: Method): CliRequest =
    self.copy(method = method)
}
private[cli] object CliRequest {
  val empty = CliRequest(URL.empty, Method.GET, Headers.empty, Json.Obj(Chunk.empty))
}
