package zio.http.datastar

import zio._
import zio.test._

import zio.http._
import zio.http.template2._

object DatastarMethodSpec extends ZIOSpecDefault {
  override def spec = suite("DatastarMethodSpec")(
    suite("datastar method integration")(
      test("should create handler that returns SSE response with patch elements") {
        val handler = Handler.fromZIO {
          ServerSentEventGenerator.patchElements(
            div(id := "content")("Hello, Datastar!"),
          )
        }

        val datastarHandler = events(handler)

        for {
          response <- datastarHandler(())
          body     <- response.body.asString
        } yield assertTrue(
          response.status == Status.Ok,
          response.headers.get(Header.ContentType).exists(_.renderedValue.contains("text/event-stream")),
          body.contains("event: datastar-patch-elements"),
          body.contains("data: elements <div id=\"content\">Hello, Datastar!</div>"),
        )
      },
      test("should handle patch signals through datastar method") {
        import zio.http.codec.TextBinaryCodec._
        val handler = Handler.fromZIO {
          ServerSentEventGenerator.patchSignals("""{"count": 42, "message": "updated"}""")
        }

        val datastarHandler = events(handler)

        for {
          response <- datastarHandler(())
          body     <- response.body.asServerSentEvents[String].take(1).runCollect.map(_.map(_.encode).mkString)
        } yield assertTrue(
          response.status == Status.Ok,
          body.contains("event: datastar-patch-signals"),
          body.contains("""data: signals {"count": 42, "message": "updated"}"""),
        )
      },
      test("should handle execute script through datastar method") {
        val handler = Handler.fromZIO {
          ServerSentEventGenerator.executeScript("console.log('Script executed via datastar')")
        }

        val datastarHandler = events(handler)

        for {
          response <- datastarHandler(())
          body     <- response.body.asString
        } yield assertTrue(
          response.status == Status.Ok,
          body.contains("event: datastar-patch-elements"),
          body.contains("data: elements <script"),
          body.contains("console.log('Script executed via datastar')"),
          body.contains("data-effect=\"el.remove\""),
        )
      },
      test("should handle multiple events in sequence") {
        val handler = Handler.fromZIO {
          for {
            _ <- ServerSentEventGenerator.patchElements(
              div(id := "header")("Updated Header"),
            )
            _ <- ServerSentEventGenerator.patchSignals("""{"status": "success"}""")
            _ <- ServerSentEventGenerator.executeScript("document.title = 'Updated'")
          } yield ()
        }

        val datastarHandler = events(handler)

        for {
          response <- datastarHandler(())
          body     <- response.body.asString
        } yield assertTrue(
          response.status == Status.Ok,
          body.contains("event: datastar-patch-elements") &&
            body
              .split("event: datastar-patch-elements")
              .length >= 3, // At least 2 patch-elements events (header + script)
          body.contains("event: datastar-patch-signals"),
          body.contains("Updated Header"),
          body.contains("""signals {"status": "success"}"""),
          body.contains("document.title = 'Updated'"),
        )
      },
      test("should work with complex patch element options") {
        val handler = Handler.fromZIO {
          ServerSentEventGenerator.patchElements(
            div(id := "dynamic-content")(
              h2("Dynamic Title"),
              p("This content was updated via Datastar"),
            ),
            PatchElementOptions(
              selector = Some(selector"#main-container"),
              mode = ElementPatchMode.Inner,
              useViewTransition = true,
              eventId = Some("update-123"),
              retryDuration = 5000.millis,
            ),
          )
        }

        val datastarHandler = events(handler)

        for {
          response <- datastarHandler(())
          body     <- response.body.asString
        } yield assertTrue(
          response.status == Status.Ok,
          body.contains("id: update-123"),
          body.contains("retry: 5000"),
          body.contains("data: selector #main-container"),
          body.contains("data: mode inner"),
          body.contains("data: useViewTransition true"),
          body.contains("data: elements <div id=\"dynamic-content\">"),
        )
      },
      test("should work with complex patch signal options") {
        import zio.http.codec.TextBinaryCodec._
        val handler = Handler.fromZIO {
          ServerSentEventGenerator.patchSignals(
            Seq(
              """{"user": {"id": 1, "name": "Alice"}}""",
              """{"notifications": {"count": 3}}""",
            ),
            PatchSignalOptions(
              onlyIfMissing = true,
              eventId = Some("signals-456"),
              retryDuration = 3000.millis,
            ),
          )
        }

        val datastarHandler = events(handler)

        for {
          response <- datastarHandler(())
          body     <- response.body.asServerSentEvents[String].take(1).runCollect.map(_.map(_.encode).mkString)
        } yield assertTrue(
          response.status == Status.Ok,
          body.contains("id: signals-456"),
          body.contains("retry: 3000"),
          body.contains("data: onlyIfMissing true"),
          body.contains("""data: signals {"user": {"id": 1, "name": "Alice"}}"""),
          body.contains("""data: signals {"notifications": {"count": 3}}"""),
        )
      },
      test("should handle handler with input parameter") {
        case class UserInput(userId: Int, action: String)

        val h = handler { (input: UserInput) =>
          ServerSentEventGenerator.patchElements(
            div(id := s"user-${input.userId}")(
              s"User ${input.userId} performed: ${input.action}",
            ),
          )
        }

        val datastarHandler = events(h)
        val userInput       = UserInput(42, "login")

        for {
          response <- datastarHandler(userInput)
          body     <- response.body.asServerSentEvents[String].take(1).runCollect.map(_.map(_.data).mkString)
        } yield assertTrue(
          response.status == Status.Ok,
          body.contains("elements <div id=\"user-42\">User 42 performed: login</div>"),
        )
      },
      test("should handle streaming multiple events over time") {
        val handler = Handler.fromZIO {
          for {
            _ <- ServerSentEventGenerator.patchElements(
              div(id := "step1")("Step 1: Starting process..."),
            )
            _ <- ZIO.sleep(10.millis) // Simulate some processing time
            _ <- ServerSentEventGenerator.patchElements(
              div(id := "step2")("Step 2: Processing data..."),
            )
            _ <- ZIO.sleep(10.millis)
            _ <- ServerSentEventGenerator.patchElements(
              div(id := "step3")("Step 3: Complete!"),
            )
            _ <- ServerSentEventGenerator.patchSignals("""{"process": "completed", "success": true}""")
          } yield ()
        }

        val datastarHandler = events(handler)

        for {
          response <- datastarHandler(())
          body     <- response.body.asString
        } yield assertTrue(
          response.status == Status.Ok,
          body.contains("Step 1: Starting process"),
          body.contains("Step 2: Processing data"),
          body.contains("Step 3: Complete!"),
          body.contains("""signals {"process": "completed", "success": true}"""),
        )
      } @@ TestAspect.withLiveClock,
      test("should work with all ElementPatchMode options") {
        val modes = List(
          (ElementPatchMode.Outer, "outer"),
          (ElementPatchMode.Inner, "inner"),
          (ElementPatchMode.Replace, "replace"),
          (ElementPatchMode.Append, "append"),
          (ElementPatchMode.Prepend, "prepend"),
          (ElementPatchMode.Before, "before"),
          (ElementPatchMode.After, "after"),
          (ElementPatchMode.Remove, "remove"),
        )

        ZIO
          .foreach(modes) { case (mode, expectedString) =>
            val handler = Handler.fromZIO {
              ServerSentEventGenerator.patchElements(
                div(id := s"test-${expectedString}")("Test content"),
                PatchElementOptions(mode = mode),
              )
            }

            val datastarHandler = events(handler)

            val expected2 = s"mode $expectedString"

            for {
              response <- datastarHandler(())
              body     <- response.body.asServerSentEvents[String].take(1).runCollect.map(_.map(_.data).mkString)
            } yield assertTrue(
              response.status == Status.Ok,
              if (mode == ElementPatchMode.Outer) {
                !body.contains("mode") // Outer mode is default, should not be included
              } else {
                body.contains(expected2)
              },
            )
          }
          .map(_.reduce(_ && _))
      },
    ),
    test("should work with Handler.fromZIO with environment") {

      val handler = Handler.fromZIO {
        for {
          config <- ZIO.service[Config]
          _      <- ServerSentEventGenerator.patchElements(
            div(id := "app-title")(s"Welcome to ${config.appName}"),
          )
        } yield ()
      }

      val datastarHandler: Handler[Config, Nothing, Unit, Response] = events(handler)

      for {
        response <- datastarHandler(()).provideSome[Scope](ZLayer.succeed(Config("TestApp")))
        body     <- response.body.asServerSentEvents[String].take(1).runCollect.map(_.map(_.data).mkString)
      } yield assertTrue(
        response.status == Status.Ok,
        body.contains("Welcome to TestApp"),
      )
    },
  )

  case class Config(appName: String)
}
