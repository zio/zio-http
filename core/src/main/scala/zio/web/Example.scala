package zio.web

import zio._
import zio.logging.{ LogFormat, LogLevel, Logging, log }
import zio.schema._
import zio.web.codec.JsonCodec
import zio.web.http.{ HttpMiddleware, HttpProtocolModule, HttpServer, HttpServerConfig }
import zio.web.http.model.{ Method, Route }

/**
 * Example server. Go to `/restclient` for test requests.
 */
object ExampleApp extends App with Example {

  // TODO: not used yet, here just to satisfy the compiler
  val allProtocols    = Map.empty
  val defaultProtocol = JsonCodec

  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    program.provideSomeLayer[ZEnv](loggingLayer).exitCode

  lazy val program =
    for {
      _      <- log.info("App started")
      config = HttpServerConfig("localhost", 8080)
      server <- HttpServer.run.provideLayer(httpLayer(config))
      _      <- log.info("Halting until interrupted")
      _      <- server.awaitShutdown.orDie
      _      <- log.info("App stopped")
    } yield ()

  lazy val loggingLayer =
    Logging.console(LogLevel.Debug, LogFormat.ColoredLogFormat()) >>>
      Logging.withRootLoggerName("example-http-server")

  def httpLayer(config: HttpServerConfig) =
    (ZLayer.requires[ZEnv with Logging] ++ ZLayer.succeed(config)) >+> helloServerLayer
}

/**
 * Usage examples.
 */
trait Example extends HttpProtocolModule {

  sealed case class UserId(id: String)
  sealed case class UserProfile(age: Int, fullName: String, address: String)

  sealed trait Status
  case class Ok(code: Int)                                               extends Status
  case class Failed(code: Int, reason: String, field3: Int, field4: Int) extends Status
  case object Pending                                                    extends Status

  implicit val statusSchema: Schema[Status] = DeriveSchema.gen

  val userJoe: UserId = UserId("123123")

  val userIdSchema: Schema[UserId] = Schema.caseClassN("id" -> Schema[String])(UserId(_), UserId.unapply(_))

  val textPlainSchema: Schema[TextPlainResponse] =
    Schema.caseClassN("content" -> Schema[String])(TextPlainResponse(_), TextPlainResponse.unapply(_))

  val userProfileSchema: Schema[UserProfile] = Schema.caseClassN(
    "age"      -> Schema[Int],
    "fullName" -> Schema[String],
    "address"  -> Schema[String]
  )(UserProfile(_, _, _), UserProfile.unapply(_))

  lazy val inMemoryDb = scala.collection.mutable.Map(
    UserId("123123") -> UserProfile(42, "Joe Doe", "Fairy Street 13, Fantasyland"),
    UserId("22")     -> UserProfile(18, "Mary Sue", "5 The Elms, Swampland")
  )

  val getUserProfile =
    endpoint("getUserProfile")
      .withRequest(userIdSchema)
      .withResponse(userProfileSchema.?) @@ Route("/users/") @@ Method.GET

  val setUserProfile =
    endpoint("setUserProfile")
      .withRequest(Schema.zipN(userIdSchema, userProfileSchema))
      .withResponse(Schema[Unit]) @@ Route("/users/{id}") @@ Method.POST

  val userService = getUserProfile + setUserProfile

  val getUserProfileHandler =
    Handler.make(getUserProfile) { id =>
      for {
        _       <- console.putStrLn(s"Handling getUserProfile request for $id")
        profile = inMemoryDb.get(id)
      } yield profile
    }

  val setUserProfileHandler =
    Handler.make(setUserProfile) {
      // for Scala 2.12 we need to hint the compiler about the types
      // for 2.13 and above the compiler is able to infer this information
      input: (UserId, UserProfile) =>
        val (id, profile) = input
        for {
          _ <- console.putStrLn(s"Handling setUserProfile request for $id and $profile")
          _ = inMemoryDb.update(id, profile)
        } yield ()
    }

  val userServiceHandlers = getUserProfileHandler + setUserProfileHandler

  // client example
  lazy val userProfile = userService.invoke(getUserProfile)(userJoe).provideLayer(makeClient(userService))

  // server example
  lazy val userServerLayer = makeServer(HttpMiddleware.none, userService, userServiceHandlers)

  // docs example
  lazy val docs = makeDocs(userService)

  // -- BASIC HTTP SERVER
  // say hello example
  sealed case class HelloRequest(name: String, message: String)
  sealed case class TextPlainResponse(content: String)

  lazy val helloSchema: Schema[HelloRequest] =
    Schema.caseClassN(
      "name"    -> Schema[String],
      "message" -> Schema[String]
    )(HelloRequest(_, _), HelloRequest.unapply(_))

  val sayHello =
    endpoint("sayHello")
      .withRequest(helloSchema)
      .withResponse(textPlainSchema) @@ Route("/{name}") @@ Method.GET

  lazy val sayHelloService = Endpoints(sayHello)

  lazy val sayHelloHandlers =
    Handlers(Handler.make(sayHello) { (req: HelloRequest) =>
      for {
        _ <- console.putStrLn(s"Handling sayHello request for ${req.name}")
      } yield TextPlainResponse(s"Hello ${req.name}!")
    })

  lazy val helloServerLayer =
    makeServer(HttpMiddleware.none, sayHelloService, sayHelloHandlers)
}
