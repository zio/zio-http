/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http

import java.nio.charset.StandardCharsets
import java.nio.file.Paths

import zio._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

import zio.stream.{ZPipeline, ZStream}

import zio.http.internal.{DynamicServer, HttpGen, HttpRunnableSpec}
import zio.http.netty.NettyConfig
import zio.http.template.{body, div, id}

object ServerSpec extends HttpRunnableSpec {

  private val nonEmptyContent = for {
    data    <- Gen.listOf(Gen.alphaNumericString)
    content <- HttpGen.nonEmptyBody(Gen.const(data))
  } yield (data.mkString(""), content)

  private val port    = 8080
  private val MaxSize = 1024 * 10
  val configApp       = Server.Config.default
    .requestDecompression(true)
    .disableRequestStreaming(MaxSize)
    .port(port)
    .responseCompression()

  private val app = serve

  def dynamicAppSpec = suite("DynamicAppSpec")(
    suite("success")(
      test("status is 200") {
        val status = Handler.ok.toRoutes.deploy.status.run()
        assertZIO(status)(equalTo(Status.Ok))
      },
      test("status is 200") {
        val res = Handler.text("ABC").toRoutes.deploy.status.run()
        assertZIO(res)(equalTo(Status.Ok))
      },
      test("content is set") {
        val res = Handler.text("ABC").toRoutes.deploy.body.mapZIO(_.asString).run()
        assertZIO(res)(containsString("ABC"))
      },
    ),
    suite("not found") {
      val app = Routes.empty
      test("status is 404") {
        val res = app.deploy.status.run()
        assertZIO(res)(equalTo(Status.NotFound))
      } +
        test("header is not set") {
          val res = app.deploy.header(Header.ContentLength).run()
          assertZIO(res)(isSome(equalTo(Header.ContentLength(0L))))
        }
    } +
      suite("error") {
        val routes = Handler.fail(new Error("SERVER_ERROR")).sandbox.toRoutes
        test("status is 500") {
          val res = routes.deploy.status.run()
          assertZIO(res)(equalTo(Status.InternalServerError))
        } +
          test("content is empty") {
            val res = routes.deploy.body.mapZIO(_.asString).run()
            assertZIO(res)(equalTo(""))
          } +
          test("header is set") {
            val res = routes.deploy.header(Header.ContentLength).run()
            assertZIO(res)(isSome(anything))
          }
      } +
      suite("die") {
        val routes = Handler.die(new Error("SERVER_ERROR")).toRoutes
        test("status is 500") {
          val res = routes.deploy.status.run()
          assertZIO(res)(equalTo(Status.InternalServerError))
        } +
          test("content is empty") {
            val res = routes.deploy.body.mapZIO(_.asString).run()
            assertZIO(res)(isEmptyString)
          } +
          test("header is set") {
            val res = routes.deploy.header(Header.ContentLength).run()
            assertZIO(res)(isSome(anything))
          }
      } +
      suite("echo content") {
        val routes = (RoutePattern.any ->
          handler { (_: Path, req: Request) =>
            req.body.asString.map(text => Response.text(text))
          }).sandbox.toRoutes

        test("status is 200") {
          val res = routes.deploy.status.run()
          assertZIO(res)(equalTo(Status.Ok))
        } +
          test("body is ok") {
            val res = routes.deploy.body.mapZIO(_.asString).run(body = Body.fromString("ABC"))
            assertZIO(res)(equalTo("ABC"))
          } +
          test("empty string") {
            val res = routes.deploy.body.mapZIO(_.asString).run(body = Body.fromString(""))
            assertZIO(res)(equalTo(""))
          } +
          test("one char") {
            val res = routes.deploy.body.mapZIO(_.asString).run(body = Body.fromString("1"))
            assertZIO(res)(equalTo("1"))
          } +
          test("data") {
            val dataStream = ZStream.repeat("A").take(MaxSize.toLong)
            val app        =
              Routes(RoutePattern.any -> handler((_: Path, req: Request) => Response(body = req.body)))
            val res        =
              app.deploy.body.mapZIO(_.asChunk.map(_.length)).run(body = Body.fromCharSequenceStreamChunked(dataStream))
            assertZIO(res)(equalTo(MaxSize))
          }
      } +
      suite("headers") {
        val routes = Handler.ok.addHeader("Foo", "Bar").toRoutes
        test("headers are set") {
          val res = routes.deploy.rawHeader("Foo").run()
          assertZIO(res)(isSome(equalTo("Bar")))
        }
      } + suite("response") {
        val routes = Handler.fromResponse(Response(status = Status.Ok, body = Body.fromString("abc"))).toRoutes
        test("body is set") {
          val res = routes.deploy.body.mapZIO(_.asString).run()
          assertZIO(res)(equalTo("abc"))
        }
      } +
      suite("compression") {
        val body         = "some-text"
        val bodyAsStream = ZStream.fromChunk(Chunk.fromArray(body.getBytes))

        val routes = Routes(
          RoutePattern.any ->
            handler { (_: Path, req: Request) =>
              req.body.asString.map(body => Response.text(body))
            },
        ).sandbox.deploy.toRoutes.sandbox

        def roundTrip[R, E <: Throwable](
          routes: Routes[R, Response],
          headers: Headers,
          contentStream: ZStream[R, E, Byte],
          compressor: ZPipeline[R, E, Byte, Byte],
          decompressor: ZPipeline[R, E, Byte, Byte],
        ) = for {
          compressed <- contentStream.via(compressor).runCollect
          response   <- routes.run(body = Body.fromChunk(compressed), headers = headers)
          body       <- response.body.asChunk.flatMap(ch => ZStream.fromChunk(ch).via(decompressor).runCollect)
        } yield new String(body.toArray, StandardCharsets.UTF_8)

        test("should decompress request and compress response") {
          checkAll(
            Gen.fromIterable(
              List(
                // Content-Encoding,   Client Request Compressor, Accept-Encoding,      Client Response Decompressor
                (Header.ContentEncoding.GZip, ZPipeline.gzip(), Header.AcceptEncoding.GZip(), ZPipeline.gunzip()),
                (
                  Header.ContentEncoding.Deflate,
                  ZPipeline.deflate(),
                  Header.AcceptEncoding.Deflate(),
                  ZPipeline.inflate(),
                ),
                (Header.ContentEncoding.GZip, ZPipeline.gzip(), Header.AcceptEncoding.Deflate(), ZPipeline.inflate()),
                (Header.ContentEncoding.Deflate, ZPipeline.deflate(), Header.AcceptEncoding.GZip(), ZPipeline.gunzip()),
              ),
            ),
          ) { case (contentEncoding, compressor, acceptEncoding, decompressor) =>
            val result = roundTrip(
              routes.sandbox,
              Headers(acceptEncoding, contentEncoding),
              bodyAsStream,
              compressor,
              decompressor,
            )
            assertZIO(result)(equalTo(body))
          }
        } +
          test("pass through for unsupported accept encoding request") {
            val result = routes.run(
              body = Body.fromString(body),
              headers = Headers(Header.AcceptEncoding.Br()),
            )
            assertZIO(result.flatMap(_.body.asString))(equalTo(body))
          } +
          test("fail on getting compressed response") {
            checkAll(
              Gen.fromIterable(
                List(
                  Header.AcceptEncoding.GZip(),
                  Header.AcceptEncoding.Deflate(),
                  Header.AcceptEncoding(Header.AcceptEncoding.GZip(), Header.AcceptEncoding.Deflate()),
                ),
              ),
            ) { ae =>
              val result = routes.run(
                body = Body.fromString(body),
                headers = Headers(ae),
              )
              assertZIO(result.flatMap(_.body.asString))(not(equalTo(body)))
            }
          }
      } +
      suite("interruption")(
        test("interrupt closes the channel without response") {
          val routes = Handler.fromZIO {
            ZIO.interrupt.as(Response.text("not interrupted"))
          }.toRoutes
          assertZIO(routes.deploy.run().exit)(
            fails(hasField("class.simpleName", _.getClass.getSimpleName, equalTo("PrematureChannelClosureException"))),
          )
        },
      ) +
      suite("proxy") {
        val server = Routes(
          Method.ANY / "proxy" / trailing ->
            handler { (path: Path, req: Request) =>
              val url = URL.decode(s"http://localhost:$port/$path").toOption.get

              for {
                res <-
                  Client.request(
                    Request(method = req.method, headers = req.headers, body = req.body, url = url),
                  )
              } yield res
            },
          Method.ANY / trailing           ->
            handler { (path: Path, req: Request) =>
              val method = req.method

              Response.text(s"Received ${method} query on ${path}")
            },
        ).sandbox

        test("should be able to directly return other request") {
          for {
            body1 <- server.deploy.body
              .run(path = Path.root / "test", method = Method.GET)
              .flatMap(_.asString(Charsets.Utf8))
            body2 <- server.deploy.body
              .run(path = Path.root / "proxy" / "test-proxy", method = Method.GET)
              .flatMap(_.asString(Charsets.Utf8))
          } yield assertTrue(body1 == "Received GET query on /test", body2 == "Received GET query on /test-proxy")
        }
      },
  )

  def requestSpec = suite("RequestSpec") {
    val app: Routes[Any, Response] =
      Routes
        .singleton(handler { (_: Path, req: Request) =>
          Response.text(req.header(Header.ContentLength).map(_.length).getOrElse(-1).toString)
        })
        .sandbox

    test("has content-length") {
      check(Gen.alphaNumericString) { string =>
        val res = app.deploy.body.mapZIO(_.asString).run(body = Body.fromString(string))
        assertZIO(res)(equalTo(string.length.toString))
      }
    } +
      test("POST Request.getBody") {
        val app = Routes
          .singleton(handler { (_: Path, req: Request) => req.body.asChunk.as(Response.ok) })
          .sandbox

        val res = app.deploy.status.run(path = Path.root, method = Method.POST, body = Body.fromString("some text"))
        assertZIO(res)(equalTo(Status.Ok))
      } +
      test("body can be read multiple times") {
        val app = Routes
          .singleton(handler { (_: Path, req: Request) =>
            (req.body.asChunk *> req.body.asChunk).as(Response.ok)
          })
          .sandbox

        val res = app.deploy.status.run(method = Method.POST, body = Body.fromString("some text"))
        assertZIO(res)(equalTo(Status.Ok))
      }
  }

  def responseSpec = suite("ResponseSpec")(
    test("data") {
      check(nonEmptyContent) { case (string, data) =>
        val res = Handler.fromBody(data).toRoutes.deploy.body.mapZIO(_.asString).run()
        assertZIO(res)(equalTo(string))
      }
    },
    test("data from file") {
      val res = Handler.fromResource("TestFile.txt").sandbox.toRoutes.deploy.body.mapZIO(_.asString).run()
      assertZIO(res)(equalTo("foo\nbar"))
    },
    test("content-type header on file response") {
      val res =
        Handler
          .fromResource("TestFile2.mp4")
          .sandbox
          .toRoutes
          .deploy
          .header(Header.ContentType)
          .run()
          .map(_.map(_.mediaType.fullType).getOrElse("Content type header not found."))
      assertZIO(res)(equalTo("video/mp4"))
    },
    test("status") {
      checkAll(HttpGen.status) { case status =>
        val res = Handler.status(status).toRoutes.deploy.status.run()
        assertZIO(res)(equalTo(status))
      }

    },
    test("header") {
      check(HttpGen.header) { header =>
        val res = Handler.ok.addHeader(header).toRoutes.deploy.rawHeader(header.headerName).run()
        assertZIO(res)(isSome(equalTo(header.renderedValue)))
      }
    },
    test("text streaming") {
      val res = Handler.fromStreamChunked(ZStream("a", "b", "c")).sandbox.toRoutes.deploy.body.mapZIO(_.asString).run()
      assertZIO(res)(equalTo("abc"))
    },
    test("echo streaming") {
      val res = Routes
        .singleton(handler { (_: Path, req: Request) =>
          Handler.fromStreamChunked(ZStream.fromZIO(req.body.asChunk).flattenChunks): Handler[
            Any,
            Throwable,
            (Path, Request),
            Response,
          ]
        })
        .sandbox
        .deploy
        .body
        .mapZIO(_.asString)
        .run(body = Body.fromString("abc"))
      assertZIO(res)(equalTo("abc"))
    },
    test("file-streaming") {
      val path = getClass.getResource("/TestFile.txt").getPath
      val res  =
        Handler
          .fromStreamChunked(ZStream.fromPath(Paths.get(path)))
          .sandbox
          .toRoutes
          .deploy
          .body
          .mapZIO(_.asString)
          .run()
      assertZIO(res)(equalTo("foo\nbar"))
    } @@ TestAspect.os(os => !os.isWindows),
    test("streaming failure - known content type") {
      val res =
        Handler
          .fromStream(ZStream.fromZIO(ZIO.attempt(throw new Exception("boom"))), 42)
          .sandbox
          .toRoutes
          .deploy
          .body
          .mapZIO(_.asString)
          .run()
          .exit
      assertZIO(res)(fails(anything))
    } @@ TestAspect.timeout(10.seconds),
    test("streaming failure - unknown content type") {
      val res =
        Handler
          .fromStreamChunked(ZStream.fromZIO(ZIO.attempt(throw new Exception("boom"))))
          .sandbox
          .toRoutes
          .deploy
          .body
          .mapZIO(_.asString)
          .run()
          .exit
      assertZIO(res)(fails(anything))
    } @@ TestAspect.timeout(10.seconds),
    suite("html")(
      test("body") {
        val res =
          Handler
            .html(zio.http.template.html(body(div(id := "foo", "bar"))))
            .sandbox
            .toRoutes
            .deploy
            .body
            .mapZIO(_.asString)
            .run()
        assertZIO(res)(equalTo("""<!DOCTYPE html><html><body><div id="foo">bar</div></body></html>"""))
      },
      test("content-type") {
        val app = Handler.html(zio.http.template.html(body(div(id := "foo", "bar")))).sandbox
        val res = app.toRoutes.deploy.header(Header.ContentType).run()
        assertZIO(res)(isSome(equalTo(Header.ContentType(MediaType.text.html))))
      },
    ),
    suite("content-length")(
      suite("string") {
        test("unicode text") {
          val res = Handler.text("äöü").sandbox.toRoutes.deploy.contentLength.run()
          assertZIO(res)(isSome(equalTo(Header.ContentLength(6L))))
        } +
          test("already set") {
            val res =
              Handler
                .text("1234567890")
                .addHeader(Header.ContentLength(4L))
                .sandbox
                .toRoutes
                .deploy
                .contentLength
                .run()
            assertZIO(res)(isSome(equalTo(Header.ContentLength(4L))))
          }
      },
    ),
    suite("memoize")(
      test("concurrent") {
        val size     = 100
        val expected = (0 to size) map (_ => Status.Ok)
        val response = Response.text("abc")
        for {
          actual <- ZIO.foreachPar(0 to size)(_ => Handler.fromResponse(response).toRoutes.deploy.status.run())
        } yield assertTrue(actual == expected)
      },
      test("update after cache") {
        val server = "ZIO-Http"
        val res    = Response.text("abc")
        for {
          actual <- Handler
            .fromResponse(res)
            .addHeader(Header.Server(server))
            .toRoutes
            .deploy
            .header(Header.Server)
            .run()
        } yield assertTrue(actual.get == Header.Server(server))
      },
    ),
  )

  def requestBodySpec = suite("RequestBodySpec")(
    test("POST Request stream") {
      val app: Routes[Any, Response] = Routes.singleton {
        handler { (_: Path, req: Request) =>
          Response(body = Body.fromStreamChunked(req.body.asStream))
        }
      }

      check(Gen.alphaNumericString) { c =>
        assertZIO(
          app.deploy.body.mapZIO(_.asString).run(path = Path.root, method = Method.POST, body = Body.fromString(c)),
        )(
          equalTo(c),
        )
      }
    },
  )

  def serverErrorSpec = suite("ServerErrorSpec") {
    val routes = Handler.fail(new Error("SERVER_ERROR")).sandbox.toRoutes
    test("status is 500") {
      val res = routes.deploy.status.run()
      assertZIO(res)(equalTo(Status.InternalServerError))
    } +
      test("content is empty") {
        val res = routes.deploy.body.mapZIO(_.asString).run()
        assertZIO(res)(equalTo(""))
      } +
      test("header is set") {
        val res = routes.deploy.headers.run().map(_.header(Header.ContentLength))
        assertZIO(res)(isSome(anything))
      }
  }

  override def spec =
    suite("ServerSpec") {
      val spec = dynamicAppSpec + responseSpec + requestSpec + requestBodySpec + serverErrorSpec
      suite("app without request streaming") { ZIO.scoped(app.as(List(spec))) }
    }.provideSome[DynamicServer & Server & Client](Scope.default)
      .provideShared(
        DynamicServer.live,
        ZLayer.succeed(configApp),
        Server.customized,
        ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
        Client.default,
      ) @@ sequential @@ withLiveClock

}
