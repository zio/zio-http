package example

import zio._
import zio.http._

object CurlLoggerExample extends ZIOAppDefault {
  val program =
    for {
      client   <- ZIO.service[Client].map(_ @@ ZClientAspect.curlLogger(logEffect = s => ZIO.debug("CURL: " + s)))
      response <- client.request(
        Request
          .post(
            "https://gorest.co.in/public/v2/users",
            Body.fromString(
              """{
                |  "name": "John Doe",
                |  "gender": "male",
                |  "email": "john.doe.unique123@example.com",
                |  "status": "active"
                |}""".stripMargin,
            ),
          )
          .addHeader(Header.ContentType(MediaType.application.json))
          .addHeader(Header.Authorization.Bearer("<YOUR_GOREST_ACCESS_TOKEN>")),
      )
      _        <- response.body.asString.debug
    } yield ()

  override val run = program.provide(Client.default, Scope.default)
}
