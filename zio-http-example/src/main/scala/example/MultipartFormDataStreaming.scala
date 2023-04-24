package example

import zio._
import zio.http._
import zio.stream.ZSink

object MultipartFormDataStreaming extends ZIOAppDefault {

  private val app: App[Any] =
    Http
      .collectZIO[Request] {
        case req @ Method.POST -> !! / "upload-simple"    =>
          for {
            count <- req.body.asStream.run(ZSink.count)
            _     <- ZIO.debug(s"Read $count bytes")
          } yield Response.text(count.toString)
        case req @ Method.POST -> !! / "upload-nonstream" =>
          for {
            form <- req.body.asMultipartForm
            count = form.formData.collect {
              case sb: FormField.Binary =>
                sb.data.size
              case _                    => 0
            }.sum
            _ <- ZIO.debug(s"Read $count bytes")
          } yield Response.text(count.toString)
        case req @ Method.POST -> !! / "upload-collect"   =>
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
        case req @ Method.POST -> !! / "upload"
            if req.header(Header.ContentType).exists(_.mediaType == MediaType.multipart.`form-data`) =>
          for {
            _     <- ZIO.debug("Starting to read multipart/form stream")
            form  <- req.body.asMultipartFormStream
              .mapError(ex =>
                Response(
                  Status.InternalServerError,
                  body = Body.fromString(s"Failed to decode body as multipart/form-data (${ex.getMessage}"),
                ),
              )
            count <- form.fields
              .flatMapPar(1) { case sb: FormField.StreamingBinary =>
                sb.data
              }
              .run(ZSink.count)

            _ <- ZIO.debug(s"Finished reading multipart/form stream, received $count bytes of data")
          } yield Response.text(count.toString)
      }
      .withDefaultErrorResponse @@ RequestHandlerMiddlewares.debug

  private def program: ZIO[Server, Throwable, Unit] =
    for {
      port <- Server.install(app)
      _    <- ZIO.logInfo(s"Server started on port $port")
    } yield ()

  override def run: ZIO[Any with ZIOAppArgs with Scope, Any, Any] =
    program
      .provide(
        ZLayer.succeed(Server.Config.default.enableRequestStreaming),
        Server.live,
      )
}
