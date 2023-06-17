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

import zio._
import zio.test.Assertion._
import zio.test.{Spec, ZIOSpecDefault, assert, assertTrue, assertZIO}

object HttpSpec extends ZIOSpecDefault with ExitAssertion {
  def extractStatus(response: Response): Status = response.status
  implicit val allowUnsafe: Unsafe              = Unsafe.unsafe

  def spec: Spec[Any, Any] =
    suite("Http")(
      suite("collectExit")(
        test("should succeed") {
          val a      = Http.collectExit[Int] { case 1 => Exit.succeed("OK") }
          val actual = a.runZIOOrNull(1)
          assert(actual)(isSuccess(equalTo("OK")))
        },
        test("should fail") {
          val a      = Http.collectExit[Int] { case 1 => Exit.fail("OK") }
          val actual = a.runZIOOrNull(1)
          assert(actual)(isFailure(equalTo("OK")))
        },
        test("should die") {
          val t      = new Throwable("boom")
          val a      = Http.collectExit[Int] { case 1 => Exit.die(t) }
          val actual = a.runZIOOrNull(1)
          assert(actual)(isDie(equalTo(t)))
        },
        test("should give empty if the inout is not defined") {
          val a      = Http.collectExit[Int] { case 1 => Exit.succeed("OK") }
          val actual = a.runZIO(0)
          assertZIO(actual.exit)(fails(isNone))
        },
      ),
      suite("combine")(
        test("should resolve first") {
          val a      = Http.collect[Int] { case 1 => "A" }
          val b      = Http.collect[Int] { case 2 => "B" }
          val actual = (a ++ b).runZIOOrNull(1)
          assertZIO(actual)(equalTo("A"))
        },
        test("should resolve second") {
          val a      = Http.empty
          val b      = Handler.succeed("A").toHttp
          val actual = (a ++ b).runZIOOrNull(())
          assertZIO(actual)(equalTo("A"))
        },
        test("should resolve second") {
          val a      = Http.collect[Int] { case 1 => "A" }
          val b      = Http.collect[Int] { case 2 => "B" }
          val actual = (a ++ b).runZIOOrNull(2)
          assertZIO(actual)(equalTo("B"))
        },
        test("should not resolve") {
          val a      = Http.collect[Int] { case 1 => "A" }
          val b      = Http.collect[Int] { case 2 => "B" }
          val actual = (a ++ b).runZIO(3)
          assertZIO(actual.exit)(fails(isNone))
        },
        test("should not resolve") {
          val a      = Http.empty
          val b      = Http.empty
          val c      = Http.empty
          val actual = (a ++ b ++ c).runZIO(())
          assertZIO(actual.exit)(fails(isNone))
        },
        test("should fail with second") {
          val a      = Http.empty
          val b      = Handler.fail(100).toHttp
          val c      = Handler.succeed("A").toHttp
          val actual = (a ++ b ++ c).runZIOOrNull(())
          assert(actual)(isFailure(equalTo(100)))
        },
        test("should resolve third") {
          val a      = Http.empty
          val b      = Http.empty
          val c      = Handler.succeed("C").toHttp
          val actual = (a ++ b ++ c).runZIOOrNull(())
          assert(actual)(isSuccess(equalTo("C")))
        },
      ),
      suite("asEffect")(
        test("should resolve") {
          val a      = Http.collect[Int] { case 1 => "A" }
          val actual = a.runZIOOrNull(1)
          assertZIO(actual)(equalTo("A"))
        },
        test("should complete") {
          val a      = Http.collect[Int] { case 1 => "A" }
          val actual = a.runZIO(2).either
          assertZIO(actual)(isLeft(isNone))
        },
      ),
      suite("collect")(
        test("should succeed") {
          val a      = Http.collect[Int] { case 1 => "OK" }
          val actual = a.runZIOOrNull(1)
          assertZIO(actual)(equalTo("OK"))
        },
        test("should fail") {
          val a      = Http.collect[Int] { case 1 => "OK" }
          val actual = a.runZIO(0)
          assertZIO(actual.exit)(fails(isNone))
        },
        test("should be lazy") {
          var mutable = 0
          for {
            pf <- ZIO.succeed {
              new PartialFunction[Request, Response] {
                override def isDefinedAt(x: Request): Boolean = true

                override def apply(v1: Request): Response = {
                  mutable += 1
                  Response.ok
                }
              }
            }
            _  <- ZIO.debug(pf.toString())
            http = Http.collect(pf) @@ RequestHandlerMiddlewares.basicAuth(_ => false)
            result       <- http.runZIO(Request.get(URL(Root / "test")))
            finalMutable <- ZIO.attempt(mutable)
          } yield assertTrue(extractStatus(result) == Status.Unauthorized, finalMutable == 0)
        },
      ),
      suite("collectZIO")(
        test("should be empty") {
          val a      = Http.collectZIO[Int] { case 1 => ZIO.succeed("A") }
          val actual = a.runZIO(2)
          assertZIO(actual.exit)(fails(isNone))
        },
        test("should resolve") {
          val a      = Http.collectZIO[Int] { case 1 => ZIO.succeed("A") }
          val actual = a.runZIOOrNull(1)
          assert(actual)(isEffect)
        },
        test("should resolve second effect") {
          val a      = Http.empty
          val b      = Handler.succeed("B").toHttp
          val actual = (a ++ b).runZIOOrNull(2)
          assert(actual)(isSuccess(equalTo("B")))
        },
        test("should be lazy") {
          var mutable = 0
          for {
            pf           <- ZIO.succeed {
              new PartialFunction[Request, ZIO[Any, Throwable, Response]] {
                override def isDefinedAt(x: Request): Boolean = true

                override def apply(v1: Request): ZIO[Any, Throwable, Response] =
                  ZIO.attempt {
                    mutable += 1
                    Response.ok
                  }
              }
            }
            http = Http.collectZIO(pf) @@ RequestHandlerMiddlewares.basicAuth(_ => false)
            result       <- http.runZIO(Request.get(URL(Root / "test")))
            finalMutable <- ZIO.attempt(mutable)
          } yield assertTrue(extractStatus(result) == Status.Unauthorized, finalMutable == 0)
        },
      ),
      suite("collectHttp")(
        test("should delegate to its HTTP apps") {
          val app    = Http.collectHandler[Int] {
            case 1 => Handler.succeed(1)
            case 2 => Handler.succeed(2)
          }
          val actual = app.runZIOOrNull(2)
          assert(actual)(isSuccess(equalTo(2)))
        },
        test("should be empty if no matches") {
          val app    = Http.collectHandler[Int](Map.empty)
          val actual = app.runZIO(1)
          assertZIO(actual.exit)(fails(isNone))
        },
      ),
      suite("map")(
        test("should execute http only when condition applies") {
          val app    = Handler.succeed(1).toHttp.map(_ + 1)
          val actual = app.runZIOOrNull(0)
          assertZIO(actual.exit)(succeeds(equalTo(2)))
        },
      ),
      suite("mapZIO")(
        test("should map the result of a success") {
          for {
            _ <- ZIO.unit
            app = Handler.succeed(1).toHttp.mapZIO(in => ZIO.succeed(in + 1))
            actual <- app.runZIOOrNull(0)
          } yield assert(actual)(equalTo(2))
        },
      ),
      suite("mapError")(
        test("should map in the http in an error") {
          for {
            _ <- ZIO.unit
            app = Handler.fail(1).toHttp.mapError(_ + 1)
            actual <- app.runZIOOrNull(0).exit
          } yield assert(actual)(isFailure(equalTo(2)))
        },
      ),
      suite("mapErrorZIO")(
        test("should not be executed in case of success") {
          for {
            ref <- Ref.make(1)
            app = Handler.succeed(1).toHttp.mapErrorZIO(_ => ref.set(2))
            actual <- app.runZIOOrNull(0)
            res    <- ref.get
          } yield assert(actual)(equalTo(1)) && assert(res)(equalTo(1))
        },
        test("should remain an error in case of error") {
          for {
            _ <- ZIO.unit
            app = Handler.fail(1).toHttp.mapErrorZIO(_ => ZIO.fail(2))
            actual <- app.runZIOOrNull(0).exit
          } yield assert(actual)(isFailure(equalTo(2)))
        },
        test("should become a success") {
          for {
            _ <- ZIO.unit
            app = Handler.fail(1).toHttp.mapErrorZIO(in => ZIO.succeed(in + 1))
            actual <- app.runZIOOrNull(0).exit
          } yield assert(actual)(isSuccess(equalTo(2)))
        },
      ),
      suite("when")(
        test("should execute http only when condition applies") {
          val app    = Handler.succeed(1).toHttp.when((_: Any) => true)
          val actual = app.runZIOOrNull(0)
          assert(actual)(isSuccess(equalTo(1)))
        },
        test("should not execute http when condition doesn't apply") {
          val app    = Handler.succeed(1).toHttp.when((_: Any) => false)
          val actual = app.runZIO(0)
          assertZIO(actual.exit)(fails(isNone))
        },
        test("should die when condition throws an exception") {
          val t      = new IllegalArgumentException("boom")
          val app    = Handler.succeed(1).toHttp.when((_: Any) => throw t)
          val actual = app.runZIO(0)
          assertZIO(actual.exit)(dies(equalTo(t)))
        },
      ),
      suite("catchAllCauseZIO")(
        test("calling from outside") {
          val app1 = Handler
            .succeed("X")
            .toHttp
            .catchAllCauseZIO(cause => ZIO.succeed(cause.dieOption.map(_.getMessage).getOrElse("")))
          val app2 = Handler.succeed("X").toHttp
          for {
            out1 <- app1.runServerErrorOrNull(Cause.die(new RuntimeException("boom")))
            out2 <- app2.runServerErrorOrNull(Cause.die(new RuntimeException("boom")))
          } yield assertTrue(
            out1 == (),
            out2 == null,
          )
        },
        test("nested") {

          for {
            queue <- Queue.unbounded[String]
            app =
              Http
                .fromHttpZIO((in: Int) =>
                  in match {
                    case 0     => ZIO.die(new RuntimeException("input is 0"))
                    case 1 | 2 =>
                      ZIO.succeed {
                        Http
                          .fromHttpZIO((in: Int) =>
                            in match {
                              case 1 => ZIO.die(new RuntimeException("input is 1"))
                              case _ => ZIO.succeed(Handler.succeed("OK").toHttp)
                            },
                          )
                          .catchAllCauseZIO { cause =>
                            val msg = cause.dieOption.map(t => "#1 " + t.getMessage).getOrElse("")
                            queue.offer(msg).as(msg)
                          }
                      }
                    case _     =>
                      ZIO.succeed(Http.empty)
                  },
                )
                .catchAllCauseZIO { cause =>
                  val msg = cause.dieOption.map(t => "#0 " + t.getMessage).getOrElse("")
                  queue.offer(msg).as(msg)
                }

            out0 <- app.runZIOOrNull(0).exit
            out1 <- app.runZIOOrNull(1).exit
            out2 <- app.runZIOOrNull(2).exit
            msgs <- queue.takeAll
          } yield assertTrue(
            out0.isFailure, // "#0 input is 0",
            out1.isFailure, // "#1 input is 1",
            out2 == Exit.Success("OK"),
            msgs == Chunk(
              "#0 input is 0",
              "#1 input is 1",
              "#0 input is 1",
            ),
          )
        },
        test("relation with ++") {
          val app1 =
            Http
              .collectHandler[Int] { case 0 | 1 =>
                Handler.fromFunction { (in: Int) =>
                  if (in == 0) throw new RuntimeException("input is 0")
                  else "OK1"
                }
              }
              .catchAllCauseZIO(cause => ZIO.succeed(cause.dieOption.map(t => "#1 " + t.getMessage).getOrElse("")))

          val app2 =
            Http
              .collectHandler[Int] { case 2 | 3 =>
                Handler.fromFunction { (in: Int) =>
                  if (in == 2) throw new RuntimeException("input is 1")
                  else "OK2"
                }
              }
              .catchAllCauseZIO(cause => ZIO.succeed(cause.dieOption.map(t => "#2 " + t.getMessage).getOrElse("")))

          val app = app1 ++ app2

          for {
            out0 <- app.runZIOOrNull(0)
            out1 <- app.runZIOOrNull(1)
            out2 <- app.runZIOOrNull(2)
            out3 <- app.runZIOOrNull(3)
          } yield assertTrue(
            out0 == "#1 input is 0",
            out1 == "OK1",
            out2 == "#2 input is 1",
            out3 == "OK2",
          )
        },
      ),
    )
}
