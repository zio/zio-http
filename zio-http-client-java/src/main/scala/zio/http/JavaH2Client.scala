package zio.http

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

import scala.jdk.CollectionConverters._

class JavaH2Client(
  httpClient: HttpClient,
  config: ClientConfig,
) extends Client {

  def send(request: Request): Response = {
    val javaRequest  = toJavaRequest(request)
    val javaResponse = httpClient.send(javaRequest, HttpResponse.BodyHandlers.ofByteArray())

    toResponse(javaResponse)
  }

  private def toJavaRequest(request: Request): HttpRequest = {
    val builder = HttpRequest
      .newBuilder(toUri(request.url))
      .timeout(config.requestTimeout)
      .method(request.method.name, toBodyPublisher(request.body))

    val headerPairs = request.headers.toList
    var i           = 0
    while (i < headerPairs.length) {
      val (name, value) = headerPairs(i)
      builder.header(name, value)
      i += 1
    }

    builder.build()
  }

  private def toResponse(javaResponse: HttpResponse[Array[Byte]]): Response = {
    val headers     = toHeaders(javaResponse.headers())
    val contentType = headers.get(Header.ContentType).map(_.value).getOrElse(ContentType.`application/octet-stream`)

    Response(
      status = Status.fromInt(javaResponse.statusCode()),
      headers = headers,
      body = Body.fromArray(javaResponse.body(), contentType),
      version = toVersion(javaResponse.version()),
    )
  }

  private def toUri(url: URL): URI =
    if (url.isAbsolute) URI.create(url.encode)
    else throw new IllegalArgumentException("JavaH2Client requires absolute request URLs")

  private def toBodyPublisher(body: Body): HttpRequest.BodyPublisher =
    if (body.isEmpty) HttpRequest.BodyPublishers.noBody()
    else HttpRequest.BodyPublishers.ofByteArray(body.toArray)

  private def toHeaders(httpHeaders: java.net.http.HttpHeaders): Headers = {
    val builder = HeadersBuilder.make()

    for {
      entry <- httpHeaders.map().entrySet().asScala
      value <- entry.getValue.asScala
    } builder.add(entry.getKey, value)

    builder.build()
  }

  private def toVersion(version: HttpClient.Version): Version = version match {
    case HttpClient.Version.HTTP_2   => Version.`HTTP/2.0`
    case HttpClient.Version.HTTP_1_1 => Version.`HTTP/1.1`
  }
}

object JavaH2Client {

  def default: JavaH2Client = apply(ClientConfig())

  def apply(config: ClientConfig): JavaH2Client =
    new JavaH2Client(configuredHttpClient(config), config)

  private def configuredHttpClient(config: ClientConfig): HttpClient =
    HttpClient
      .newBuilder()
      .version(HttpClient.Version.HTTP_2)
      .connectTimeout(config.connectTimeout)
      .followRedirects(if (config.followRedirects) HttpClient.Redirect.NORMAL else HttpClient.Redirect.NEVER)
      .build()
}
