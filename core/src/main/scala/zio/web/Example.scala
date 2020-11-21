package zio.web

import zio.schema._

trait Example extends http.HttpProtocolModule {
  import http.HttpMiddleware

  sealed case class UserId(id: String)
  sealed case class UserProfile(age: Int, fullName: String, address: String)

  sealed trait Status
  case class Ok(code: Int)                                               extends Status
  case class Failed(code: Int, reason: String, field3: Int, field4: Int) extends Status
  case object Pending                                                    extends Status

  implicit val statusSchema: Schema[Status] = DeriveSchema.gen

  val userJoe: UserId = UserId("123123")

  val userIdSchema: Schema[UserId] = Schema.caseClassN("id" -> Schema[String])(UserId(_), UserId.unapply(_))

  val userProfileSchema: Schema[UserProfile] = Schema.caseClassN(
    "age"      -> Schema[Int],
    "fullName" -> Schema[String],
    "address"  -> Schema[String]
  )(UserProfile(_, _, _), UserProfile.unapply(_))

  import _root_.zio.web.http.model._

  lazy val getUserProfile: Endpoint2[Any, UserId, UserProfile] =
    endpoint("getUserProfile")
      .withRequest(userIdSchema)
      .withResponse(userProfileSchema)
      .handler(_ => ???) @@ Route("/users/") @@ Method.GET

  lazy val setUserProfile =
    endpoint("setUserProfile")
      .withRequest(Schema.zipN(userIdSchema, userProfileSchema))
      .withResponse(Schema[Unit])
      .handler(_ => ???)

  lazy val userService =
    getUserProfile ::
      setUserProfile :: Endpoints.empty

  object client_example {
    lazy val userProfile = userService.invoke(getUserProfile)(userJoe).provideLayer(makeClient(userService))
  }

  object server_example {
    lazy val serverLayer = makeServer(HttpMiddleware.none, userService)
  }

  object docs_example {
    val docs = makeDocs(userService)
  }
}
