package example

import zio._
import zio.http.endpoint.openapi.Codegen
import zio.internal.stacktracer.SourceLocation

import java.nio.file.Path

object OpenApiCodegen extends ZIOAppDefault {
  val petstoreJsonString = scala.io.Source.fromResource(s"openapi/petstore.json").mkString

  val run =
    for {
      code <- ZIO.fromEither(Codegen.fromJsonSchema("example.petstore", petstoreJsonString))
      path = Path.of(implicitly[SourceLocation].path).getParent.resolve("OpenApiGeneratedPetStore.scala")
      _ <- ZIO.writeFile(path, code)
    } yield ()
}
