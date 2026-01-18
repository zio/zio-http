package zio.http.gen.smithy

import zio.test._
import software.amazon.smithy.model.Model
import software.amazon.smithy.model.loader.ModelAssembler
import zio.http.gen.scala.Code
import zio.http.Method
import zio.http.Status

object SmithyEndpointGenSpec extends ZIOSpecDefault {

  def spec = suite("SmithyEndpointGenSpec")(
    test("generates simple service") {
      val smithy =
        """namespace com.example
          |
          |use smithy.api#http
          |use smithy.api#readonly
          |
          |@http(method: "GET", uri: "/hello")
          |@readonly
          |operation Hello {
          |    input: HelloInput,
          |    output: HelloOutput
          |}
          |
          |structure HelloInput {}
          |
          |structure HelloOutput {}
          |
          |service Example {
          |    version: "1.0.0",
          |    operations: [Hello]
          |}
          |""".stripMargin

      val assembler = Model.assembler()
      assembler.addUnparsedModel("example.smithy", smithy)
      val model     = assembler.assemble().unwrap()

      val generated = SmithyEndpointGen.fromSmithy(model)

      assertTrue(generated.files.exists(_.path.last == "Example.scala"))
      assertTrue(generated.files.exists(_.path.last == "HelloInput.scala"))
      assertTrue(generated.files.exists(_.path.last == "HelloOutput.scala"))
    },
    test("generates path parameters") {
      val smithy =
        """namespace com.example
          |
          |use smithy.api#http
          |use smithy.api#httpLabel
          |
          |@http(method: "GET", uri: "/users/{userId}")
          |operation GetUser {
          |    input: GetUserInput,
          |    output: GetUserOutput
          |}
          |structure GetUserInput {
          |    @httpLabel
          |    @required
          |    userId: String
          |}
          |structure GetUserOutput {}
          |
          |service User {
          |    version: "1.0.0",
          |    operations: [GetUser]
          |}
          |""".stripMargin

      val assembler = Model.assembler()
      assembler.addUnparsedModel("user.smithy", smithy)
      val model     = assembler.assemble().unwrap()

      val generated = SmithyEndpointGen.fromSmithy(model)

      val serviceFile = generated.files.find(_.path.last == "User.scala").get
      val endpoint    = serviceFile.objects.head.endpoints.head._2

      assertTrue(endpoint.pathPatternCode.segments.exists(_.name == "userId"))
    },
  )
}
