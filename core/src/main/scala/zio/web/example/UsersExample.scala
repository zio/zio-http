package zio.web.example

import zio._
import zio.schema._
import zio.web.{ Handler, endpoint }
import zio.web.codec.JsonCodec
import zio.web.http.{ HttpMiddleware, HttpProtocol }
import zio.web.http.model.{ Method, Route }

object UsersServer extends UsersExample {

  val inMemoryDb = scala.collection.mutable.Map(
    joeId  -> UserProfile(42, "Joe Doe", "Fairy Street 13, Fantasyland"),
    maryId -> UserProfile(18, "Mary Sue", "5 The Elms, Swampland")
  )

  val getUserProfileHandler =
    Handler.make(getUserProfile) { id =>
      for {
        _       <- console.putStrLn(s"Handling getUserProfile request for $id")
        profile = inMemoryDb.get(id)
      } yield profile
    }

  val setUserProfileHandler =
    Handler.make(setUserProfile) { (id: UserId, profile: UserProfile) =>
      for {
        _ <- console.putStrLn(s"Handling setUserProfile request for $id and $profile")
        _ = inMemoryDb.update(id, profile)
      } yield ()
    }

  val userServiceHandlers = getUserProfileHandler + setUserProfileHandler

  val userServerLayer = makeServer(HttpMiddleware.none, userService, userServiceHandlers)
}

object UsersClient extends UsersExample {

  lazy val userProfile = userService.invoke(getUserProfile)(joeId).provideLayer(makeClient(userService))
}

object UsersDocs extends UsersExample {

  lazy val docs = makeDocs(userService)
}

trait UsersExample extends HttpProtocol {

  val allProtocols    = Map.empty
  val defaultProtocol = JsonCodec

  case class UserProfile(age: Int, fullName: String, address: String)
  case class UserId(id: Int)

  val userProfileSchema: Schema[UserProfile] = Schema.caseClassN(
    "age"      -> Schema[Int],
    "fullName" -> Schema[String],
    "address"  -> Schema[String]
  )(UserProfile(_, _, _), UserProfile.unapply(_))

  val userIdSchema: Schema[UserId] = DeriveSchema.gen

  val joeId: UserId  = UserId(123123)
  val maryId: UserId = UserId(22)

  import Route._

  val UserIdVal = IntVal.derive[UserId](UserId(_), _.id)

  val getUserProfile =
    endpoint("getUserProfile")
      .withRequest(userIdSchema)
      .withResponse(userProfileSchema.?) @@ Route(_ / "users") @@ Method.GET

  val setUserProfile =
    endpoint("setUserProfile")
      .withRequest(userProfileSchema)
      .withResponse(Schema[Unit]) @@ Route(_ / "users" / UserIdVal) @@ Method.POST

  val userService =
    getUserProfile + setUserProfile
}
