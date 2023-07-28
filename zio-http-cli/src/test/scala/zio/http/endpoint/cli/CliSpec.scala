package zio.http.endpoint.cli

import zio._
import zio.cli._
import zio.test.TestAspect._
import zio.test._

import zio.schema._

import zio.http._
import zio.http.codec._
import zio.http.endpoint._
import zio.http.endpoint.cli.CliRepr._

/**
 * Test suite for Http CliApp. It tests:
 *   - CliEndpoint.fromEndpoint method
 *   - HttpCommands.addOptions method
 *   - Correct generation of commands
 *   - Correct behaviour of CliApp
 */

object CliSpec extends ZIOSpecDefault {

  val bodyCodec1 = HttpCodec.Content(Schema.bigDecimal, None, Some("body1"))

  val bodyCodec2 = HttpCodec.Content(Schema[String], None, Some("body2"))

  val bodyStream = HttpCodec.ContentStream(Schema.bigInt, None, Some("bodyStream"))

  val headerCodec = HttpCodec.Header("header", TextCodec.string)

  val path1 = PathCodec.bool("path1")

  val path2 = PathCodec.string("path2")

  val path3 = path1 / path2

  val simpleEndpoint = Endpoint(Method.GET / path1).inCodec(bodyCodec1).inCodec(headerCodec)

  val multiformEndpoint = Endpoint(Method.POST / path2).inCodec(bodyCodec1).inCodec(bodyCodec2)

  val streamEndpoint = Endpoint(Method.PUT / path3).inCodec(bodyStream)

  val endpoints = Chunk(simpleEndpoint, multiformEndpoint, streamEndpoint)

  val testClient: ZLayer[Any, Nothing, TestClient & Client] =
    ZLayer.scopedEnvironment {
      for {
        behavior       <- Ref.make[PartialFunction[Request, ZIO[Any, Response, Response]]](PartialFunction.empty)
        socketBehavior <- Ref.make[SocketApp[Any]](Handler.unit)
        driver = TestClient(behavior, socketBehavior)
        _ <- driver.addHandler {
          case Request(_, Method.GET, URL(path, _, _, _), _, _, _) if path.encode == "/fromURL" =>
            ZIO.succeed(Response.text("342.76"))
          case Request(_, Method.GET, _, headers, body, _)
              if headers.headOption.map(_.renderedValue) == Some("fromURL") =>
            ZIO.succeed(Response(Status.Ok, headers, body))
          case Request(_, Method.GET, _, _, body, _)                                            =>
            for {
              text <- body.asMultipartForm
                .map(_.formData)
                .map(_.map(_.stringValue.toString()))
                .map(_.toString())
                .mapError(e => Response.error(Status.BadRequest, e.getMessage()))
            } yield if (text == "Chunk(Some(342.76))") Response.text("received 1") else Response.text(text)
          case Request(_, Method.POST, _, _, body, _)                                           =>
            for {
              text     <- body.asMultipartForm
                .map(_.formData)
                .map(_.map(_.stringValue.toString()))
                .map(_.toString())
                .mapError(e => Response.error(Status.BadRequest, e.getMessage()))
              response <-
                if (text == """Chunk(Some(342.76),Some("sample"))""") ZIO.succeed("received 2")
                else ZIO.succeed(text)
            } yield Response.text(response)
          case Request(_, Method.PUT, _, _, body, _)                                            =>
            for {
              text     <- body.asMultipartForm
                .map(_.formData)
                .map(_.map(_.stringValue.toString()))
                .map(_.toString())
                .mapError(e => Response.error(Status.BadRequest, e.getMessage()))
              response <-
                if (text == "Chunk(Some(342))") ZIO.succeed("received 3")
                else ZIO.succeed(text)
            } yield Response.text(response)
          case _ => ZIO.succeed(Response.text("not received"))
        }
      } yield ZEnvironment[TestClient, Client](driver, ZClient.fromDriver(driver))
    }

  val cliApp: CliApp[Any, Throwable, CliRequest] =
    HttpCliApp
      .fromEndpoints(
        "simple",
        "1.0.0",
        HelpDoc.Span.empty,
        endpoints,
        "localhost",
        0,
        client = CliZLayerClient(testClient),
      )
      .cliApp

  override def spec =
    suite("HttpCliSpec")(
      suite("GenTests")(
        test("fromEndpoint generates the correct CliEndpoint") {
          check(EndpointGen.anyCliEndpoint) { case CliRepr(endpoint, repr) =>
            assertTrue(endpoint == repr)
          }
        },
        test("CliEndpoint generates correct Options") {
          check(OptionsGen.anyCliEndpoint) {
            case CliRepr(options, repr) => {
              assertTrue(
                HttpCommand.addOptionsTo(repr).helpDoc.toPlaintext() == options.helpDoc.toPlaintext()
                  && HttpCommand.addOptionsTo(repr).uid == options.uid,
              )
            }
          }
        },
        test("fromEndpoints generates correct Command") {
          check(CommandGen.anyEndpoint) {
            case CliRepr(endpoint, helpDoc) => {
              val command = HttpCommand.fromEndpoints("cli", Chunk(endpoint), true)
              val a1      = command.helpDoc.toPlaintext().replace(Array(27, 91, 48, 109).map(_.toChar).mkString, "")
              val a2      = helpDoc.toPlaintext().replace(Array(27, 91, 48, 109).map(_.toChar).mkString, "")
              assertTrue(a1 == a2)
            }
          }
        },
      ) @@ timeout(60.second),
      suite("Correct behaviour of CliApp")(
        test("Simple endpoint") {
          for {
            response <- cliApp.run(List("get", "--path1", "--body1", "342.76", "--header", "header"))
            result   <- response match {
              case r: Response => r.body.asString
              case _           => ZIO.succeed("wrong type")
            }
          } yield assertTrue(result == "received 1")
        },
        test("Multiform endpoint") {
          for {
            response <- cliApp.run(List("create", "--path2", "sampleText", "--body1", "342.76", "--body2", "sample"))
            result   <- response match {
              case r: Response => r.body.asString
              case _           => ZIO.succeed("wrong type")
            }
          } yield assertTrue(result == "received 2")
        },
        test("Stream content endpoint") {
          for {
            response <- cliApp.run(List("update", "--path2", "sampleText", "--path1", "--bodyStream", "342"))
            result   <- response match {
              case r: Response => r.body.asString
              case _           => ZIO.succeed("wrong type")
            }
          } yield assertTrue(result == "received 3")
        },
        test("From URL") {
          for {
            response <- cliApp.run(List("get", "--header", "fromURL", "--u-body1", "/fromURL"))
            result   <- response match {
              case r: Response => ZIO.succeed(r)
              case _           => ZIO.succeed(Response.text("wrong type"))
            }
            text     <- result.body.asString
          } yield assertTrue(text.contains("342.76"))
        },
      ) @@ sequential @@ withLiveClock,
    )
}
