//> using dep "dev.zio::zio-http:3.4.1"

package example.middleware

import zio._

import zio.http._

object CounterProtocolStackExample extends ZIOAppDefault {
  val uppercaseHandler: Handler[Any, Nothing, String, String] =
    Handler.fromFunction[String](_.toUpperCase)

  def requestCounter[I, O]: ProtocolStack[Ref[Long], I, I, O, (Long, O)] =
    ProtocolStack.interceptHandlerStateful {
      Handler.fromFunctionZIO[I] { (incomingInput: I) =>
        for {
          db <- ZIO.service[Ref[Long]]
          _  <- db.update(_ + 1)
          c  <- db.get
        } yield (c, incomingInput)
      }
    }(Handler.identity)

  val handler: Handler[Ref[Long], (Long, String), String, (Long, String)] =
    requestCounter[String, String](uppercaseHandler)

  def app = for {
    _ <- handler("Hello!").debug
    _ <- handler("Hello, World!").debug
    _ <- handler("What is ZIO?").debug
  } yield ()

  def run = app.provide(Scope.default, ZLayer.fromZIO(Ref.make(0L)))
}
