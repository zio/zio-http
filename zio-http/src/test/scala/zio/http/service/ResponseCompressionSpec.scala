package zio.http.service

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPOutputStream

import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}
import zio.{Chunk, Scope, ZIO, ZInputStream, ZLayer}

import zio.stream.ZStream

import zio.http.ServerConfig.LeakDetectionLevel
import zio.http._
import zio.http.internal.{DynamicServer, HttpRunnableSpec}
import zio.http.model.{Headers, Method, Scheme, Status}

object ResponseCompressionSpec extends ZIOSpecDefault {

  private val text: HttpApp[Any, Nothing] =
    Http.collect[Request] { case Method.GET -> !! / "text" =>
      Response.text("Hello World!\n")
    }

  private val stream =
    Http.collect[Request] { case Method.GET -> !! / "stream" =>
      Response(
        Status.Ok,
        Headers(
          Seq(
            Headers.Header("Content-Type", "text/plain"),
          ),
        ),
        Body.fromStream(
          ZStream
            .unfold[Long, String](0L) { s =>
              if (s < 1000) Some((s"$s\n", s + 1)) else None
            }
            .grouped(10)
            .map(_.mkString),
        ),
      )
    }

  private val app                             = text ++ stream
  private lazy val serverConfig: ServerConfig = ServerConfig.default.port(0).responseCompression()

  override def spec =
    suite("Response compression")(
      test("with Response.text") {
        for {
          server       <- ZIO.service[Server]
          client       <- ZIO.service[Client]
          _            <- server.install(app).fork
          response     <- client.request(
            Request
              .default(
                method = Method.GET,
                url = URL(!! / "text", kind = URL.Location.Absolute(Scheme.HTTP, "localhost", server.port)),
              )
              .withAcceptEncoding("gzip,deflate"),
          )
          res          <- response.body.asChunk
          decompressed <- decompressed(res)
        } yield assertTrue(decompressed == "Hello World!\n")
      },
      test("with Response.stream") {
        for {
          server       <- ZIO.service[Server]
          client       <- ZIO.service[Client]
          _            <- server.install(app).fork
          response     <- client.request(
            Request
              .default(
                method = Method.GET,
                url = URL(!! / "stream", kind = URL.Location.Absolute(Scheme.HTTP, "localhost", server.port)),
              )
              .withAcceptEncoding("gzip,deflate"),
          )
          res          <- response.body.asChunk
          decompressed <- decompressed(res)
          expected = (0 until 1000).mkString("\n") + "\n"
        } yield assertTrue(decompressed == expected)
      },
    ).provide(
      ZLayer.succeed(serverConfig),
      Server.live,
      Client.default,
    )

  private def decompressed(bytes: Chunk[Byte]): ZIO[Any, Throwable, String] =
    ZIO.attempt {
      val inputStream = new ByteArrayInputStream(bytes.toArray)
      new java.util.zip.GZIPInputStream(inputStream)
    }.mapError(Some(_))
      .flatMap { stream =>
        ZInputStream.fromInputStream(stream).readAll(4096).map { decompressedBytes =>
          new String(decompressedBytes.toArray, StandardCharsets.UTF_8)
        }
      }
      .unsome
      .map(_.getOrElse(""))
}
