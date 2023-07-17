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

import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._
import zio.{Chunk, Scope, ZIO, ZLayer, durationInt}

import zio.stream.{ZPipeline, ZStream}

import zio.http.html.{body, div, id}
import zio.http.internal.{DynamicServer, HttpGen, HttpRunnableSpec}

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
        val status = Handler.ok.toHttpApp.deploy.status.run()
        assertZIO(status)(equalTo(Status.Ok))
      },
      test("status is 200") {
        val res = Handler.text("ABC").toHttpApp.deploy.status.run()
        assertZIO(res)(equalTo(Status.Ok))
      },
      test("content is set") {
        val res = Handler.text("ABC").toHttpApp.deploy.body.mapZIO(_.asString).run()
        assertZIO(res)(containsString("ABC"))
      },
    ),
    suite("not found") {
      val app = HttpApp2.empty
      test("status is 404") {
        val res = app.deploy.status.run()
        assertZIO(res)(equalTo(Status.NotFound))
      } +
        test("header is set") {
          val res = app.deploy.header(Header.ContentLength).run()
          assertZIO(res)(isSome(equalTo(Header.ContentLength(0L))))
        }
    } +
      suite("error") {
        val app = Handler.fail(new Error("SERVER_ERROR")).ignore.toHttpApp
        test("status is 500") {
          val res = app.deploy.status.run()
          assertZIO(res)(equalTo(Status.InternalServerError))
        } +
          test("content is empty") {
            val res = app.deploy.body.mapZIO(_.asString).run()
            assertZIO(res)(isEmptyString)
          } +
          test("header is set") {
            val res = app.deploy.header(Header.ContentLength).run()
            assertZIO(res)(isSome(anything))
          }
      } +
      suite("die") {
        val app = Handler.die(new Error("SERVER_ERROR")).toHttpApp
        test("status is 500") {
          val res = app.deploy.status.run()
          assertZIO(res)(equalTo(Status.InternalServerError))
        } +
          test("content is empty") {
            val res = app.deploy.body.mapZIO(_.asString).run()
            assertZIO(res)(isEmptyString)
          } +
          test("header is set") {
            val res = app.deploy.header(Header.ContentLength).run()
            assertZIO(res)(isSome(anything))
          }
      } +
      suite("echo content") {
        val app = (RoutePattern.any ->
          handler { (path: Path, req: Request) =>
            req.body.asString.map(text => Response.text(text))
          }).ignore.toApp

        test("status is 200") {
          val res = app.deploy.status.run()
          assertZIO(res)(equalTo(Status.Ok))
        } +
          test("body is ok") {
            val res = app.deploy.body.mapZIO(_.asString).run(body = Body.fromString("ABC"))
            assertZIO(res)(equalTo("ABC"))
          } +
          test("empty string") {
            val res = app.deploy.body.mapZIO(_.asString).run(body = Body.fromString(""))
            assertZIO(res)(equalTo(""))
          } +
          test("one char") {
            val res = app.deploy.body.mapZIO(_.asString).run(body = Body.fromString("1"))
            assertZIO(res)(equalTo("1"))
          } +
          test("data") {
            val dataStream = ZStream.repeat("A").take(MaxSize.toLong)
            val app        =
              Routes(RoutePattern.any -> handler((path: Path, req: Request) => Response(body = req.body))).toApp
            val res        = app.deploy.body.mapZIO(_.asChunk.map(_.length)).run(body = Body.fromStream(dataStream))
            assertZIO(res)(equalTo(MaxSize))
          }
      } +
      suite("headers") {
        val app = Handler.ok.addHeader("Foo", "Bar").toHttpApp
        test("headers are set") {
          val res = app.deploy.rawHeader("Foo").run()
          assertZIO(res)(isSome(equalTo("Bar")))
        }
      } + suite("response") {
        val app = Handler.response(Response(status = Status.Ok, body = Body.fromString("abc"))).toHttpApp
        test("body is set") {
          val res = app.deploy.body.mapZIO(_.asString).run()
          assertZIO(res)(equalTo("abc"))
        }
      } +
      suite("compression") {
        val body         = "some-text"
        val bodyAsStream = ZStream.fromChunk(Chunk.fromArray(body.getBytes))

        val app = Routes(
          RoutePattern.any ->
            handler { (path: Path, req: Request) =>
              req.body.asString.map(body => Response.text(body))
            },
        ).ignore.toApp.deploy

        def roundTrip[R, E <: Throwable](
          app: HttpApp2[R],
          headers: Headers,
          contentStream: ZStream[R, E, Byte],
          compressor: ZPipeline[R, E, Byte, Byte],
          decompressor: ZPipeline[R, E, Byte, Byte],
        ) = for {
          compressed <- contentStream.via(compressor).runCollect
          response   <- app.run(body = Body.fromChunk(compressed), headers = headers)
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
              app.ignore.toHttpApp,
              Headers(acceptEncoding, contentEncoding),
              bodyAsStream,
              compressor,
              decompressor,
            )
            assertZIO(result)(equalTo(body))
          }
        } +
          test("pass through for unsupported accept encoding request") {
            val result = app.run(
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
              val result = app.run(
                body = Body.fromString(body),
                headers = Headers(ae),
              )
              assertZIO(result.flatMap(_.body.asString))(not(equalTo(body)))
            }
          }
      } +
      suite("interruption")(
        test("interrupt closes the channel without response") {
          val app = Handler.fromZIO {
            ZIO.interrupt.as(Response.text("not interrupted"))
          }.toHttpApp
          assertZIO(app.deploy.run().exit)(
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
        ).ignore.toApp

        test("should be able to directly return other request") {
          for {
            body1 <- server.deploy.body
              .run(path = Root / "test", method = Method.GET)
              .flatMap(_.asString(Charsets.Utf8))
            body2 <- server.deploy.body
              .run(path = Root / "proxy" / "test-proxy", method = Method.GET)
              .flatMap(_.asString(Charsets.Utf8))
          } yield assertTrue(body1 == "Received GET query on /test", body2 == "Received GET query on /test-proxy")
        }
      },
  )

  def requestSpec = suite("RequestSpec") {
    val app: HttpApp2[Any] =
      Routes
        .singleton(handler { (path: Path, req: Request) =>
          Response.text(req.header(Header.ContentLength).map(_.length).getOrElse(-1).toString)
        })
        .ignore
        .toApp

    test("has content-length") {
      check(Gen.alphaNumericString) { string =>
        val res = app.deploy.body.mapZIO(_.asString).run(body = Body.fromString(string))
        assertZIO(res)(equalTo(string.length.toString))
      }
    } +
      test("POST Request.getBody") {
        val app = Routes
          .singleton(handler { (path: Path, req: Request) => req.body.asChunk.as(Response.ok) })
          .ignore
          .toApp
        val res = app.deploy.status.run(path = Root, method = Method.POST, body = Body.fromString("some text"))
        assertZIO(res)(equalTo(Status.Ok))
      } +
      test("body can be read multiple times") {
        val app = Routes
          .singleton(handler { (path: Path, req: Request) =>
            (req.body.asChunk *> req.body.asChunk).as(Response.ok)
          })
          .ignore
          .toApp
        val res = app.deploy.status.run(method = Method.POST, body = Body.fromString("some text"))
        assertZIO(res)(equalTo(Status.Ok))
      }
  }

  def responseSpec = suite("ResponseSpec")(
    test("data") {
      check(nonEmptyContent) { case (string, data) =>
        val res = Handler.fromBody(data).toHttpApp.deploy.body.mapZIO(_.asString).run()
        assertZIO(res)(equalTo(string))
      }
    },
    test("data from file") {
      val res = Handler.fromResource("TestFile.txt").ignore.toHttpApp.deploy.body.mapZIO(_.asString).run()
      assertZIO(res)(equalTo("foo\nbar"))
    },
    test("content-type header on file response") {
      val res =
        Handler
          .fromResource("TestFile2.mp4")
          .ignore
          .toHttpApp
          .deploy
          .header(Header.ContentType)
          .run()
          .map(_.map(_.mediaType.fullType).getOrElse("Content type header not found."))
      assertZIO(res)(equalTo("video/mp4"))
    },
    test("status") {
      checkAll(HttpGen.status) { case status =>
        val res = Handler.status(status).toHttpApp.deploy.status.run()
        assertZIO(res)(equalTo(status))
      }

    },
    test("header") {
      check(HttpGen.header) { header =>
        val res = Handler.ok.addHeader(header).toHttpApp.deploy.rawHeader(header.headerName).run()
        assertZIO(res)(isSome(equalTo(header.renderedValue)))
      }
    },
    test("text streaming") {
      val res = Handler.fromStream(ZStream("a", "b", "c")).ignore.toHttpApp.deploy.body.mapZIO(_.asString).run()
      assertZIO(res)(equalTo("abc"))
    },
    test("echo streaming") {
      val res = Routes
        .singleton(handler { (path: Path, req: Request) =>
          Handler.fromStream(ZStream.fromZIO(req.body.asChunk).flattenChunks): Handler[
            Any,
            Throwable,
            (Path, Request),
            Response,
          ]
        })
        .ignore
        .toApp
        .deploy
        .body
        .mapZIO(_.asString)
        .run(body = Body.fromString("abc"))
      assertZIO(res)(equalTo("abc"))
    },
    test("file-streaming") {
      val path = getClass.getResource("/TestFile.txt").getPath
      val res  =
        Handler.fromStream(ZStream.fromPath(Paths.get(path))).ignore.toHttpApp.deploy.body.mapZIO(_.asString).run()
      assertZIO(res)(equalTo("foo\nbar"))
    } @@ TestAspect.os(os => !os.isWindows),
    suite("html")(
      test("body") {
        val res =
          Handler
            .html(zio.http.html.html(body(div(id := "foo", "bar"))))
            .ignore
            .toHttpApp
            .deploy
            .body
            .mapZIO(_.asString)
            .run()
        assertZIO(res)(equalTo("""<!DOCTYPE html><html><body><div id="foo">bar</div></body></html>"""))
      },
      test("content-type") {
        val app = Handler.html(zio.http.html.html(body(div(id := "foo", "bar")))).ignore.toHttpApp
        val res = app.deploy.header(Header.ContentType).run()
        assertZIO(res)(isSome(equalTo(Header.ContentType(MediaType.text.html))))
      },
    ),
    suite("content-length")(
      suite("string") {
        test("unicode text") {
          val res = Handler.text("äöü").ignore.toHttpApp.deploy.contentLength.run()
          assertZIO(res)(isSome(equalTo(Header.ContentLength(6L))))
        } +
          test("already set") {
            val res =
              Handler.text("1234567890").addHeader(Header.ContentLength(4L)).ignore.toHttpApp.deploy.contentLength.run()
            assertZIO(res)(isSome(equalTo(Header.ContentLength(4L))))
          }
      },
    ),
    suite("memoize")(
      test("concurrent") {
        val size     = 100
        val expected = (0 to size) map (_ => Status.Ok)
        val response = Response.text("abc").freeze
        for {
          actual <- ZIO.foreachPar(0 to size)(_ => Handler.response(response).toHttpApp.deploy.status.run())
        } yield assertTrue(actual == expected)
      },
      test("update after cache") {
        val server = "ZIO-Http"
        val res    = Response.text("abc").freeze
        for {
          actual <- Handler.response(res).addHeader(Header.Server(server)).toHttpApp.deploy.header(Header.Server).run()
        } yield assertTrue(actual.get == Header.Server(server))
      },
    ),
  )

  def requestBodySpec = suite("RequestBodySpec")(
    test("POST Request stream") {
      val app: HttpApp2[Any] = Routes.singletonZIO { (path: Path, req: Request) =>
        ZIO.succeed(Response(body = Body.fromStream(req.body.asStream)))
      }.toApp

      check(Gen.alphaNumericString) { c =>
        assertZIO(app.deploy.body.mapZIO(_.asString).run(path = Root, method = Method.POST, body = Body.fromString(c)))(
          equalTo(c),
        )
      }
    },
  )

  def serverErrorSpec = suite("ServerErrorSpec") {
    val app = Handler.fail(new Error("SERVER_ERROR")).ignore.toHttpApp
    test("status is 500") {
      val res = app.deploy.status.run()
      assertZIO(res)(equalTo(Status.InternalServerError))
    } +
      test("content is empty") {
        val res = app.deploy.body.mapZIO(_.asString).run()
        assertZIO(res)(isEmptyString)
      } +
      test("header is set") {
        val res = app.deploy.headers.run().map(_.header(Header.ContentLength))
        assertZIO(res)(isSome(anything))
      }
  }

  override def spec =
    suite("ServerSpec") {
      val spec = dynamicAppSpec + responseSpec + requestSpec + requestBodySpec + serverErrorSpec
      suite("app without request streaming") { ZIO.scoped(app.as(List(spec))) }
    }.provideSomeShared[TestEnvironment](
      DynamicServer.live,
      ZLayer.succeed(configApp),
      Server.live,
      Client.default,
      Scope.default,
    ) @@ timeout(30 seconds) @@ sequential @@ withLiveClock

}
