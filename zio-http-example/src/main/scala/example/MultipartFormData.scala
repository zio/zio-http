package example

import zio.{Chunk, Scope, ZIO, ZIOAppArgs, ZIOAppDefault}

import zio.http._

object MultipartFormData extends ZIOAppDefault {

  private val app: Routes[Any, Response] =
    Routes(
      Method.POST / "upload" ->
        handler { (req: Request) =>
          if (req.header(Header.ContentType).exists(_.mediaType == MediaType.multipart.`form-data`))
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
                    case FormField.Binary(_, data, contentType, transferEncoding, filename) =>
                      ZIO.succeed(
                        Response.text(
                          s"Received ${data.length} bytes of $contentType filename $filename and transfer encoding $transferEncoding",
                        ),
                      )
                    case _                                                                  =>
                      ZIO.fail(
                        Response(Status.BadRequest, body = Body.fromString("Parameter 'file' must be a binary file")),
                      )
                  }
                case None       =>
                  ZIO.fail(Response(Status.BadRequest, body = Body.fromString("Missing 'file' from body")))
              }
            } yield response
          else ZIO.succeed(Response(status = Status.NotFound))
        },
    ).sandbox

  private def program: ZIO[Client with Server with Scope, Throwable, Unit] =
    for {
      port         <- Server.install(app)
      _            <- ZIO.logInfo(s"Server started on port $port")
      client       <- ZIO.service[Client]
      response     <- client
        .host("localhost")
        .port(port)
        .post("/upload")(
          Body.fromMultipartForm(
            Form(
              FormField.binaryField(
                "file",
                Chunk.fromArray("Hello, world!".getBytes),
                MediaType.application.`octet-stream`,
                filename = Some("hello.txt"),
              ),
            ),
            Boundary("AaB03x"),
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
        Scope.default,
      )
}
