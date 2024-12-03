import gigahorse.support.apachehttp.Gigahorse
import zio.json.ast.Json
import zio.json.ast.JsonCursor
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Try}

lazy val root = (project in file("."))
  .enablePlugins(ZioHttpCodegen)
  .settings(
    name := "uspto-sdk",
    organization := "com.example",
    scalaVersion := "2.13.15",
    libraryDependencies +="dev.zio" %% "zio-http" % "3.0.1",
    ZIOpenApi / sourceGenerators += Def.task[Seq[File]] {

      val outFile = (ZIOpenApi / sourceManaged).value / "gov" / "uspto" / "ibd" / "api.json"
      val http = Gigahorse.http(Gigahorse.config)
      val request = Gigahorse.url("https://developer.uspto.gov/ibd-api/v3/api-docs")
      val response = http.run(request, Gigahorse.asString)

      Await.result(response.transform(_.flatMap { content =>

        // TODO: this is a temporary workaround
        //       current zio-http-gen module has many gaps not yet implemented,
        //       so we need to clean the API just so we can use it here.
        //       in the future, once the module had matured enough,
        //       we should remove this part, and perhaps take a more comprehensive example like:
        //       https://petstore3.swagger.io
        val either = for {
          decodedJsObj <- Json.Obj.decoder.decodeJson(content)
          noInlined404 <- decodedJsObj.delete(JsonCursor.field("paths").isObject.field("/v1/weeklyarchivedata/searchWeeklyArchiveData").isObject.field("get").isObject.field("responses").isObject.field("404"))
          noInlinedAPI <- noInlined404.delete(JsonCursor.field("paths").isObject.field("/v1/weeklyarchivedata/apistatus"))
        } yield noInlinedAPI

        either.fold[Try[Seq[File]]](
          failure => Failure(new Exception(failure)),
          cleaned => Try {
            IO.write(outFile, cleaned.toJsonPretty)
            Seq(outFile)
          })
      })(ExecutionContext.global), 1.minute)
    }
  )