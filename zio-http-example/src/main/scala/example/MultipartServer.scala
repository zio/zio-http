package example

import zio.http._
import zio.http.model.Multipart.FileUpload
import zio.stream.ZPipeline
import zio.{ZIO, ZIOAppDefault, ZLayer}

object MultipartServer extends ZIOAppDefault {

  val app = Http.collectZIO[Request] { case req =>
    ZIO.scoped {
      req.body.multipart.flatMap(x =>
        ZIO.foreachDiscard(x)(m =>
          (ZIO.debug(s"${m.name} - ${m.getClass.getSimpleName} - ${m.length}")) *>
            ZIO.whenCase(m) { case u: FileUpload =>
              ZIO.debug(s"File name: ${u.filename}}") *>
                u.content.via(ZPipeline.utf8Decode >>> ZPipeline.splitLines).foreach(ZIO.debug(_))
            },
        ),
      )
    } as Response.ok
  }

  val run =
    ZIO.debug("To test upload big file:\ncurl -v -F key1=value1 -F upload=@bigfile.txt localhost:8080") *>
      Server
        .serve(app)
        .provide(ZLayer.succeed(ServerConfig.default.objectAggregator(1024 * 3000)), Server.live)
        .exitCode
}
