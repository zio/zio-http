package zio.web.http.middleware

import zio.{ Chunk, UIO, ZIO, ZManaged }
import zio.blocking.Blocking
import zio.duration._
import zio.stream.{ ZSink, ZStream }
import zio.test.Assertion._
import zio.test.{ DefaultRunnableSpec, assert }
import zio.test.environment.TestClock
import zio.web.http.auth.BasicAuth.AuthResult.{ Denied, Granted }
import zio.web.http.auth.BasicAuth.{ AuthParams, AuthResult }
import zio.web.http.{ HttpHeaders, Patch }
import zio.web.http.auth.{ AuthParamsSpec, BasicAuth }
import zio.web.http.model.StatusCode

import java.io.{ ByteArrayOutputStream, File }
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Paths

object HttpMiddlewareSpec extends DefaultRunnableSpec {

  def spec =
    suite("HttpMiddleware")(loggingSpec, basicAuthSpec)

  val loggingSpec = suite("logging")(
    testM("with the True-Client-IP header") {
      ZManaged.fromAutoCloseable(ZIO.succeed(new ByteArrayOutputStream())).use {
        out =>
          val dest = ZSink
            .fromOutputStream(out)
            .contramapChunks[String](_.flatMap(str => Chunk.fromIterable(str.getBytes)))

          for {
            l       <- logging(dest).make
            _       <- TestClock.setTime(0.seconds)
            state   <- l.runRequest(method, new URI(uri), version, HttpHeaders(Map(clientHeader -> ipAddr)))
            _       <- l.runResponse(state, status, HttpHeaders(Map(contentLengthHeader -> length.toString)))
            _       <- ZIO.succeed(out.size()).repeatUntil(_ > 0)
            content = new String(out.toByteArray, StandardCharsets.UTF_8)
          } yield assert(content)(
            equalTo(
              s"$ipAddr - - [01/Jan/1970:00:00:00 +0000] ${"\""}$method $uri $version${"\""} $status $length\n"
            )
          )
      }
    },
    testM("with the X-Forwarded-For header") {
      ZManaged.fromAutoCloseable(ZIO.succeed(new ByteArrayOutputStream())).use {
        out =>
          val dest = ZSink
            .fromOutputStream(out)
            .contramapChunks[String](_.flatMap(str => Chunk.fromIterable(str.getBytes)))

          for {
            l       <- logging(dest).make
            _       <- TestClock.setTime(0.seconds)
            state   <- l.runRequest(method, new URI(uri), version, HttpHeaders(Map(forwardedHeader -> ipAddr)))
            _       <- l.runResponse(state, status, HttpHeaders(Map(contentLengthHeader -> length.toString)))
            _       <- ZIO.succeed(out.size()).repeatUntil(_ > 0)
            content = new String(out.toByteArray, StandardCharsets.UTF_8)
          } yield assert(content)(
            equalTo(
              s"$ipAddr - - [01/Jan/1970:00:00:00 +0000] ${"\""}$method $uri $version${"\""} $status $length\n"
            )
          )
      }
    },
    testM("without IP address") {
      ZManaged.fromAutoCloseable(ZIO.succeed(new ByteArrayOutputStream())).use {
        out =>
          val dest = ZSink
            .fromOutputStream(out)
            .contramapChunks[String](_.flatMap(str => Chunk.fromIterable(str.getBytes)))

          for {
            l       <- logging(dest).make
            _       <- TestClock.setTime(0.seconds)
            state   <- l.runRequest(method, new URI(uri), version, HttpHeaders.empty)
            _       <- l.runResponse(state, status, HttpHeaders(Map(contentLengthHeader -> length.toString)))
            _       <- ZIO.succeed(out.size()).repeatUntil(_ > 0)
            content = new String(out.toByteArray, StandardCharsets.UTF_8)
          } yield assert(content)(
            equalTo(s"- - - [01/Jan/1970:00:00:00 +0000] ${"\""}$method $uri $version${"\""} $status $length\n")
          )
      }
    },
    testM("to the file") {
      ZManaged
        .make(ZIO.succeed(logFile))(path => ZIO.effect(new File(path).delete()).orDie)
        .use {
          path =>
            for {
              l       <- fileLogging(path).make
              _       <- TestClock.setTime(0.seconds)
              state   <- l.runRequest(method, new URI(uri), version, HttpHeaders(Map(clientHeader -> ipAddr)))
              _       <- l.runResponse(state, status, HttpHeaders(Map(contentLengthHeader -> length.toString)))
              result  <- ZStream.fromFile(Paths.get(path), 32).runCollect
              content = new String(result.toArray, StandardCharsets.UTF_8)
            } yield assert(content)(
              equalTo(
                s"$ipAddr - - [01/Jan/1970:00:00:00 +0000] ${"\""}$method $uri $version${"\""} $status $length\n"
              )
            )
        }
    }
  ).provideCustomLayerShared(Blocking.live)

  val basicAuthSpec = suite("basicAuth")(
    testM("don't modify the response when credentials are valid") {
      (for {
        m <- basicAuth(realm, authFn).make
        state <- m.runRequest(
                  method,
                  new URI(uri),
                  version,
                  HttpHeaders(Map(AuthParamsSpec.authHeader("user", "password")))
                )
        patch <- m.runResponse(state, StatusCode.Ok.code, HttpHeaders(Map()))
      } yield {
        assert(patch)(equalTo(Patch.empty))
      })
    },
    testM("return Unauthorized when the auth header is missing") {
      (for {
        m <- basicAuth(realm, authFn).make
        state <- m.runRequest(
                  method,
                  new URI(uri),
                  version,
                  HttpHeaders(Map())
                )
        patch <- m.runResponse(state, StatusCode.Ok.code, HttpHeaders(Map()))
      } yield {
        val expected = BasicAuth.unauthorized("realm")
        assert(patch)(equalTo(expected))
      })
    },
    testM("return Forbidden when credentials are invalid") {
      (for {
        m <- basicAuth(realm, authFn).make
        state <- m.runRequest(
                  method,
                  new URI(uri),
                  version,
                  HttpHeaders(Map(AuthParamsSpec.authHeader("user", "wrong_password")))
                )
        patch <- m.runResponse(state, StatusCode.Ok.code, HttpHeaders(Map()))
      } yield {
        assert(patch)(equalTo(BasicAuth.forbidden))
      })
    }
  )

  private def authFn(p: AuthParams): UIO[AuthResult] = ZIO.succeed {
    if (p.user == "user" && p.password == "password") {
      Granted
    } else {
      Denied
    }
  }

  val clientHeader        = "True-Client-IP"
  val forwardedHeader     = "X-Forwarded-For"
  val contentLengthHeader = "Content-Length"
  val method              = "GET"
  val uri                 = "http://zio.dev"
  val version             = "HTTP/1.1"
  val ipAddr              = "127.0.0.1"
  val status              = 200
  val length              = 1000
  val logFile             = "test.log"
  val realm               = "realm"
}
