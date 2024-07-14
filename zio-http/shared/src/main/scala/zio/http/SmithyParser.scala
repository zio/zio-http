import java.nio.file.{Files, Paths, Path}
import scala.jdk.CollectionConverters._ // Updated import
import com.google.gson.{Gson, GsonBuilder}
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.node.Node
import software.amazon.smithy.model.shapes.{ServiceShape, ShapeId}
import software.amazon.smithy.openapi.OpenApiConfig
import software.amazon.smithy.openapi.fromsmithy.OpenApiConverter
import zio.http.endpoint.openapi.OpenAPI
import zio.http.gen.openapi.EndpointGen
import zio.http.gen.scala.CodeGen

object SmithyParser {
  def run(modelPath: Path, outputPath: Path, basePackage: String): Unit = {
    val converter = OpenApiConverter.create()

    if (!Files.exists(modelPath)) {
      sys.error(s"Model file not found at path: $modelPath")
    }

    val model = Model.assembler().addImport(modelPath).discoverModels().assemble().unwrap()
    val serviceShapes = model.shapes().collect(java.util.stream.Collectors.toList()).asScala.filter(_.isServiceShape)

    if (serviceShapes.isEmpty) {
      sys.error("No service shapes found in the model.")
    }

    serviceShapes.foreach { shape =>
      val optionalServiceShape = shape.asServiceShape()
      optionalServiceShape.ifPresent { serviceShape =>
        val openApiConfig = new OpenApiConfig()
        openApiConfig.setService(serviceShape.getId)
        val openApi = converter.config(openApiConfig).convert(model)
        val jsonString = Node.prettyPrintJson(openApi.toNode)
        val openAPI = OpenAPI.fromJson(jsonString.stripMargin)

        val api = openAPI.fold(
          error => sys.error(s"Error converting OpenAPI: $error"),
          identity
        )

        val endpointObjects = EndpointGen.fromOpenAPI(api)

        if (!Files.exists(outputPath)) {
          Files.createDirectories(outputPath)
        }

        CodeGen.writeFiles(
          endpointObjects,
          basePath = outputPath,
          basePackage = basePackage,
          scalafmtPath = None
        )
        () // Explicitly ignore the return value
      }
    }
  }
}
