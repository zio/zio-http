package example

import zio._

import zio.stream.{ZSink, ZStream}

import zio.http._

object MultipartFormDataStreaming extends ZIOAppDefault {

  private val app: HttpApp[Any] =
    Routes(
      Method.POST / "upload-simple"    -> handler { (req: Request) =>
        for {
          count <- req.body.asStream.run(ZSink.count)
          _     <- ZIO.debug(s"Read $count bytes")
        } yield Response.text(count.toString)
      },
      Method.POST / "upload-nonstream" -> handler { (req: Request) =>
        for {
          form <- req.body.asMultipartForm
          count = form.formData.collect {
            case sb: FormField.Binary =>
              sb.data.size
            case _                    => 0
          }.sum
          _ <- ZIO.debug(s"Read $count bytes")
        } yield Response.text(count.toString)
      },
      Method.POST / "upload-collect"   -> handler { (req: Request) =>
        for {
          sform <- req.body.asMultipartFormStream
          form  <- sform.collectAll
          count = form.formData.collect {
            case sb: FormField.Binary =>
              sb.data.size
            case _                    => 0
          }.sum
          _ <- ZIO.debug(s"Read $count bytes")
        } yield Response.text(count.toString)
      },
      Method.POST / "upload"           -> handler { (req: Request) =>
        if (req.header(Header.ContentType).exists(_.mediaType == MediaType.multipart.`form-data`))
          for {
            _     <- ZIO.debug("Starting to read multipart/form stream")
            form  <- req.body.asMultipartFormStream
              .mapError(ex =>
                Response(
                  Status.InternalServerError,
                  body = Body.fromString(s"Failed to decode body as multipart/form-data (${ex.getMessage}"),
                ),
              )
            count <- form.fields.flatMap {
              case sb: FormField.StreamingBinary =>
                sb.data
              case _                             =>
                ZStream.empty
            }.run(ZSink.count)

            _ <- ZIO.debug(s"Finished reading multipart/form stream, received $count bytes of data")
          } yield Response.text(count.toString)
        else ZIO.succeed(Response(status = Status.NotFound))
      },
    ).sandbox.toHttpApp @@ Middleware.debug

  private def program: ZIO[Server, Throwable, Unit] =
    for {
      port <- Server.install(app)
      _    <- ZIO.logInfo(s"Server started on port $port")
      _    <- ZIO.never
    } yield ()

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    program
      .provide(
        ZLayer.succeed(Server.Config.default.enableRequestStreaming),
        Server.live,
      )
}
