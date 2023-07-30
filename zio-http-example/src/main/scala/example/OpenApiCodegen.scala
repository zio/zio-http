package example

import zio._
import zio.http.endpoint.openapi.Codegen
import zio.internal.stacktracer.SourceLocation

import java.nio.file.Paths

object OpenApiCodegen extends ZIOAppDefault {

  val run =
    for {
      petstoreJsonString <- ZIO.attempt(scala.io.Source.fromResource(s"openapi/petstore.json").mkString)
      code               <- ZIO.fromEither(Codegen.fromJsonSchema("example.petstore", petstoreJsonString))
      path = Paths
        .get(implicitly[SourceLocation].path)
        .getParent
        .resolve("OpenApiGeneratedPetStore.scala")
      _ <- ZIO.writeFile(path, code)
    } yield ()
}
