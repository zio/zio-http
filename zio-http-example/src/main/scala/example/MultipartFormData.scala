package example

import zio.{Chunk, Scope, ZIO, ZIOAppArgs, ZIOAppDefault}

import zio.http._
import zio.http.forms.{Form, FormData}
import zio.http.model.{MediaType, Method, Status}

object MultipartFormData extends ZIOAppDefault {

  private def isMultipartFormData(contentType: CharSequence): Boolean =
    MediaType
      .forContentType(contentType.toString)
      .exists(mediaType => mediaType.fullType == MediaType.multipart.`form-data`.fullType)

  private val app: App[Any] =
    Http.collectZIO[Request] {
      case req @ Method.POST -> !! / "upload" if req.headers.contentType.exists(isMultipartFormData) =>
        for {
          form     <- req.body.asMultipartForm
            .mapError(ex =>
              Response(
                Status.InternalServerError,
                body = Body.fromString(s"Failed to decode body as multipart/form-data (${ex.getMessage}"),
              ),
            )
          response <- form.get("file") match {
            case Some(file) =>
              file match {
                case FormData.Binary(_, data, contentType, transferEncoding, filename) =>
                  ZIO.succeed(
                    Response.text(
                      s"Received ${data.length} bytes of $contentType filename $filename and transfer encoding $transferEncoding",
                    ),
                  )
                case _                                                                 =>
                  ZIO.fail(
                    Response(Status.BadRequest, body = Body.fromString("Parameter 'file' must be a binary file")),
                  )
              }
            case None       =>
              ZIO.fail(Response(Status.BadRequest, body = Body.fromString("Missing 'file' from body")))
          }
        } yield response
    }

  private def program: ZIO[Client with Server, Throwable, Unit] =
    for {
      port         <- Server.install(app)
      _            <- ZIO.logInfo(s"Server started on port $port")
      client       <- ZIO.service[Client]
      response     <- client
        .host("localhost")
        .port(port)
        .post(
          "/upload",
          Body.fromMultipartForm(
            Form(
              FormData.binaryField(
                "file",
                Chunk.fromArray("Hello, world!".getBytes),
                MediaType.application.`octet-stream`,
                filename = Some("hello.txt"),
              ),
            ),
          ),
        )
      responseBody <- response.body.asString
      _            <- ZIO.logInfo(s"Response: [${response.status}] $responseBody")
      _            <- ZIO.never
    } yield ()

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    program
      .provide(
        Server.default,
        Client.default,
      )
}
