package zio.http

import zio.blocks.context.Context
import zio.blocks.endpoint.PathCodec._
import zio.blocks.endpoint.RoutePattern.{MethodSyntax, RoutePatternOps}
import zio.blocks.endpoint.SegmentCodec
import zio.blocks.scope.Scope
import zio.http.RouteBinding._
import zio.test._

import java.util.UUID

/**
 * Reproduces every Worked Example from `.omo/drafts/route-pattern-typed-vars.md`'s "Worked
 * examples" section, in the FINAL `pattern -> handler(fn)` syntax (D9/D12), on Scala 3.
 */
object PathVarHandlerBindingSpec extends ZIOSpecDefault {

  final case class BasketId(value: String)

  private def urlPath(segments: String*): URL = segments.foldLeft(URL.root)(_ / _)

  private def run[Ctx](route: Route[Ctx], context: Context[Ctx], method: Method, path: Path): Response | Halt = {
    val extracted = route.pattern.decode(method, path).getOrElse(throw new RuntimeException("path did not match"))
    route.handler.handle(Request.get(URL.root.copy(path = path)), context, extracted, Scope.global)
  }

  def spec = suite("PathVarHandlerBinding (Scala 3)")(
    test("1. single named var, full use") {
      val route  = Method.GET / int("id") -> handler((id: Int) => Response.text(s"user $id"))
      val result = run(route, Context.empty, Method.GET, urlPath("42").path)
      assertTrue(result == Response.text("user 42"))
    },
    test("2/3. multiple vars, any declared order in the handler fn") {
      val route = Method.GET / int("userId") / string("postId") ->
        handler((postId: String, userId: Int) => Response.text(s"post=$postId user=$userId"))
      val result = run(route, Context.empty, Method.GET, urlPath("7", "abc").path)
      assertTrue(result == Response.text("post=abc user=7"))
    },
    test("4. partial use compiles and returns correctly (unused-var warning verified separately)") {
      val route = Method.GET / int("userId") / string("postId") ->
        handler((userId: Int) => Response.text(s"user=$userId"))
      val result = run(route, Context.empty, Method.GET, urlPath("9", "ignored").path)
      assertTrue(result == Response.text("user=9"))
    },
    test("5. zero PathVars consumed still compiles and dispatches") {
      val route = Method.GET / int("userId") / string("postId") ->
        handler((request: Request) => Response.text("ignoring vars"))
      val result = run(route, Context.empty, Method.GET, urlPath("1", "x").path)
      assertTrue(result == Response.text("ignoring vars"))
    },
    test("6/7. same-type collision disambiguated purely by name") {
      val route = Method.GET / int("page") / int("limit") ->
        handler((limit: Int, page: Int) => Response.text(s"page=$page limit=$limit"))
      val result = run(route, Context.empty, Method.GET, urlPath("2", "50").path)
      assertTrue(result == Response.text("page=2 limit=50"))
    },
    test("8. PathVar name+type miss falls through to Context[Ctx] by type") {
      val route = Method.GET / uuid("customerId") ->
        handler((customerId: UUID, basketId: BasketId) => Response.text(s"customer=$customerId basket=${basketId.value}"))
      val customerId = UUID.randomUUID()
      val result      = run(route, Context(BasketId("cart-1")), Method.GET, urlPath(customerId.toString).path)
      assertTrue(result == Response.text(s"customer=$customerId basket=cart-1"))
    },
    test("9. Request/Scope combine freely with PathVar and Context params, any order") {
      val route = Method.GET / int("id") ->
        handler((id: Int, request: Request, basketId: BasketId) =>
          Response.text(s"${request.method} user=$id basket=${basketId.value}")
        )
      val result = run(route, Context(BasketId("cart-2")), Method.GET, urlPath("5").path)
      assertTrue(result == Response.text("GET user=5 basket=cart-2"))
    },
    test("10. composed/prefixed patterns accumulate the PathVar registry in order") {
      val userPrefix  = Method.GET / "users" / int("userId")
      val fullPattern = userPrefix / "posts" / string("postId")
      val route       = fullPattern -> handler((userId: Int, postId: String) => Response.text(s"u=$userId p=$postId"))
      val result      = run(route, Context.empty, Method.GET, urlPath("users", "3", "posts", "hi").path)
      assertTrue(result == Response.text("u=3 p=hi"))
    },
    test("11. same RoutePattern reused by two handlers with independently-checked usage") {
      val pattern       = Method.GET / int("userId") / string("postId")
      val fullRoute     = pattern -> handler((userId: Int, postId: String) => Response.text(s"full:$userId:$postId"))
      val partialRoute  = pattern -> handler((userId: Int) => Response.text(s"partial:$userId"))
      val fullResult    = run(fullRoute, Context.empty, Method.GET, urlPath("1", "a").path)
      val partialResult = run(partialRoute, Context.empty, Method.GET, urlPath("1", "a").path)
      assertTrue(fullResult == Response.text("full:1:a"), partialResult == Response.text("partial:1"))
    },
    test("pre-built Handler values still work via the separate `->` overload") {
      val route  = Method.GET / int("id") -> Handler.succeed(Response.ok)
      val result = run(route, Context.empty, Method.GET, urlPath("99").path)
      assertTrue(result == Response.ok)
    },
    test("Routes(...) @@ mw middleware idiom compiles unchanged") {
      val routes = Routes(
        Method.GET / int("id") -> handler((id: Int) => Response.text(s"user $id")),
        Method.POST / "logout" -> handler(() => Response.ok),
      ) @@ Middleware.identity
      assertTrue(routes.size == 2)
    },
    test("negative: a handler param matching no PathVar and no Context type fails to compile at ->") {
      assertZIO(typeCheck {
        """import zio.blocks.endpoint.PathCodec._
import zio.blocks.endpoint.RoutePattern.{MethodSyntax, RoutePatternOps}
import zio.http.RouteBinding._
import zio.http._

val pattern = Method.GET / int("id")
pattern -> handler((wrongName: Int) => Response.text("x"))
"""
      })(Assertion.isLeft(Assertion.anything))
    },
    test("12. multiple unused vars in a 3-segment pattern still run correctly (separateness of the warnings is verified below via a real -Werror compile)") {
      val route  = Method.GET / int("userId") / string("postId") / string("tag") ->
        handler((userId: Int) => Response.text(s"user=$userId"))
      val result = run(route, Context.empty, Method.GET, urlPath("3", "ignored-post", "ignored-tag").path)
      assertTrue(result == Response.text("user=3"))
    },
    test("13. a Context capability declared-but-body-unused never triggers the path-var warning") {
      // `basketId` must be DECLARED to be resolved from Context at all (D7 tier 2) - "unused" here
      // means its VALUE is never referenced in the function body, which is ordinary Scala and must
      // NOT trigger the macro's "was defined in the path" warning (that warning is reserved for
      // PathVar entries the ROUTE PATTERN declares, never for Context capabilities). Confirmed at
      // the real compiler-diagnostic level (not just observed absence) by test 16 below.
      val route  = Method.GET / int("id") ->
        handler((id: Int, basketId: BasketId) => Response.text(s"user=$id"))
      val result = run(route, Context(BasketId("unused-basket")), Method.GET, urlPath("7").path)
      assertTrue(result == Response.text("user=7"))
    },
    test(
      "14 (-Werror build-level proof). a 3-segment pattern with only 1 var consumed FAILS to compile under fatal-warnings with exactly 2 distinct warnings naming the other 2 vars"
    ) {
      val code =
        """package zio.http.scratch14
          |import zio.blocks.endpoint.PathCodec._
          |import zio.blocks.endpoint.RoutePattern.{MethodSyntax, RoutePatternOps}
          |import zio.http.RouteBinding._
          |import zio.http.{Response, Method}
          |object Scratch14 {
          |  val route = Method.GET / int("userId") / string("postId") / string("tag") ->
          |    handler((userId: Int) => Response.text(s"user=$userId"))
          |}
          |""".stripMargin
      val (exitCode, output) = FatalWarningsProof.compileScala3(code)
      val postIdWarnings     = "Variable postId:String was defined in the path but is never used".r.findAllIn(output).length
      val tagWarnings        = "Variable tag:String was defined in the path but is never used".r.findAllIn(output).length
      assertTrue(exitCode != 0, postIdWarnings == 1, tagWarnings == 1)
    },
    test("15 (-Werror build-level proof). the SAME pattern with full use compiles cleanly (zero warnings) under fatal-warnings") {
      val code =
        """package zio.http.scratch15
          |import zio.blocks.endpoint.PathCodec._
          |import zio.blocks.endpoint.RoutePattern.{MethodSyntax, RoutePatternOps}
          |import zio.http.RouteBinding._
          |import zio.http.{Response, Method}
          |object Scratch15 {
          |  val route = Method.GET / int("userId") / string("postId") / string("tag") ->
          |    handler((userId: Int, postId: String, tag: String) => Response.text(s"$userId $postId $tag"))
          |}
          |""".stripMargin
      val (exitCode, output) = FatalWarningsProof.compileScala3(code)
      assertTrue(exitCode == 0, !output.contains("was defined in the path"))
    },
    test(
      "16 (-Werror build-level proof). a Context capability declared-but-body-unused does NOT fail under fatal-warnings (D7 exemption is a real compiler-diagnostic guarantee)"
    ) {
      val code =
        """package zio.http.scratch16
          |import zio.blocks.endpoint.PathCodec._
          |import zio.blocks.endpoint.RoutePattern.{MethodSyntax, RoutePatternOps}
          |import zio.http.RouteBinding._
          |import zio.http.{Response, Method}
          |final case class ScratchBasketId(value: String)
          |object Scratch16 {
          |  val route = Method.GET / int("id") ->
          |    handler((id: Int, basketId: ScratchBasketId) => Response.text(s"user=$id"))
          |}
          |""".stripMargin
      val (exitCode, output) = FatalWarningsProof.compileScala3(code)
      assertTrue(exitCode == 0, !output.contains("was defined in the path"))
    },
    test("17. an .unused segment sandwiched between two normal vars is silently skipped, positions stay correct") {
      // Proves the positional runtime-access logic (`arrowImpl`'s `positions`/`buildTuple`) is
      // unaffected by an `Ignored` entry sitting in the MIDDLE of the pattern's real value tuple:
      // `b` still occupies a real slot at runtime, so `c`'s position must be 2 (not 1), and `a`'s
      // decoded value must not be corrupted by `b`'s presence between them.
      val route  = Method.GET / int("a") / SegmentCodec.string("b").unused / int("c") ->
        handler((a: Int, c: Int) => Response.text(s"a=$a c=$c"))
      val result = run(route, Context.empty, Method.GET, urlPath("1", "ignored-b", "3").path)
      assertTrue(result == Response.text("a=1 c=3"))
    },
    test(
      "18. a handler referencing an .unused segment still binds its real decoded value (a lint, not a functional change)"
    ) {
      val route  = Method.GET / int("a") / SegmentCodec.string("b").unused / int("c") ->
        handler((a: Int, b: String, c: Int) => Response.text(s"a=$a b=$b c=$c"))
      val result = run(route, Context.empty, Method.GET, urlPath("1", "hello", "3").path)
      assertTrue(result == Response.text("a=1 b=hello c=3"))
    },
    test(
      "19 (-Werror build-level proof). an .unused segment never referenced by the handler compiles cleanly (zero warnings) - the whole point of .unused"
    ) {
      val code =
        """package zio.http.scratch19
          |import zio.blocks.endpoint.PathCodec._
          |import zio.blocks.endpoint.RoutePattern.{MethodSyntax, RoutePatternOps}
          |import zio.blocks.endpoint.SegmentCodec
          |import zio.http.RouteBinding._
          |import zio.http.{Response, Method}
          |object Scratch19 {
          |  val route = Method.GET / int("userId") / SegmentCodec.string("postId").unused ->
          |    handler((userId: Int) => Response.text(s"user=$userId"))
          |}
          |""".stripMargin
      val (exitCode, output) = FatalWarningsProof.compileScala3(code)
      assertTrue(exitCode == 0, !output.contains("was defined in the path"), !output.contains("was marked .unused"))
    },
    test(
      "20 (-Werror build-level proof). an .unused segment referenced by the handler emits the new 'marked .unused but referenced' warning (a lint, not a compile failure of its own accord - it only fails here because -Werror escalates EVERY warning to an error, exactly like test 14's plain-unused case)"
    ) {
      val code =
        """package zio.http.scratch20
          |import zio.blocks.endpoint.PathCodec._
          |import zio.blocks.endpoint.RoutePattern.{MethodSyntax, RoutePatternOps}
          |import zio.blocks.endpoint.SegmentCodec
          |import zio.http.RouteBinding._
          |import zio.http.{Response, Method}
          |object Scratch20 {
          |  val route = Method.GET / int("userId") / SegmentCodec.string("postId").unused ->
          |    handler((userId: Int, postId: String) => Response.text(s"user=$userId post=$postId"))
          |}
          |""".stripMargin
      val (exitCode, output) = FatalWarningsProof.compileScala3(code)
      val postIdWarnings     =
        "Variable postId:String was marked .unused but is referenced by the handler".r.findAllIn(output).length
      assertTrue(exitCode != 0, postIdWarnings == 1, !output.contains("was defined in the path"))
    },
    test(
      "21 (-Werror build-level proof). a plain unconsumed var and a referenced .unused var in the same pattern fire BOTH warnings independently, with correct distinct text each"
    ) {
      val code =
        """package zio.http.scratch21
          |import zio.blocks.endpoint.PathCodec._
          |import zio.blocks.endpoint.RoutePattern.{MethodSyntax, RoutePatternOps}
          |import zio.blocks.endpoint.SegmentCodec
          |import zio.http.RouteBinding._
          |import zio.http.{Response, Method}
          |object Scratch21 {
          |  val route = Method.GET / int("userId") / SegmentCodec.string("postId").unused / string("tag") ->
          |    handler((userId: Int, postId: String) => Response.text(s"user=$userId post=$postId"))
          |}
          |""".stripMargin
      val (exitCode, output) = FatalWarningsProof.compileScala3(code)
      val postIdWarnings     =
        "Variable postId:String was marked .unused but is referenced by the handler".r.findAllIn(output).length
      val tagWarnings        = "Variable tag:String was defined in the path but is never used".r.findAllIn(output).length
      assertTrue(exitCode != 0, postIdWarnings == 1, tagWarnings == 1)
    },
  )
}

/**
 * Drives the REAL `dotc` compiler (out-of-process, `-Werror` = the Scala 3 spelling of
 * `-Xfatal-warnings`) against a scratch snippet, using this test JVM's own `java.class.path` (the
 * exact classpath mill resolved for `core.jvm[3.8.3].test`) plus the Scala 3 compiler's own jars
 * (located under the same coursier cache the running JVM's `scala-library` jar came from). This
 * proves the unused-PathVar warning is a REAL compiler diagnostic participating in a
 * warnings-as-errors build (Todo 6's deliverable 3) - not an informational side effect - since a
 * genuine external `dotc` process, given the real production `RouteBinding.scala`, either fails or
 * succeeds to compile based on it.
 */
private object FatalWarningsProof {

  private def coursierRoot(): java.io.File = {
    val cp = sys.props("java.class.path").split(java.io.File.pathSeparatorChar)
    val libEntry = cp
      .find(_.replace('\\', '/').contains("/org/scala-lang/scala-library/"))
      .getOrElse(
        throw new IllegalStateException(s"scala-library not found on java.class.path: ${cp.mkString(", ")}")
      )
    // .../org/scala-lang/scala-library/<version>/scala-library-<version>.jar -> cache root is 4 dirs up
    new java.io.File(libEntry).getParentFile.getParentFile.getParentFile.getParentFile.getParentFile
  }

  private def findJar(root: java.io.File, groupPath: String, artifactPrefix: String): String = {
    val dir = new java.io.File(root, groupPath)
    def search(d: java.io.File): List[java.io.File] =
      Option(d.listFiles()).toList.flatten.flatMap { f =>
        if (f.isDirectory) search(f)
        else if (f.getName.startsWith(artifactPrefix) && f.getName.endsWith(".jar") && !f.getName.contains("sources") && !f.getName.contains("javadoc"))
          List(f)
        else Nil
      }
    search(dir).sortBy(_.getName).lastOption
      .map(_.getAbsolutePath)
      .getOrElse(throw new IllegalStateException(s"Could not locate $artifactPrefix*.jar under $dir"))
  }

  /** Compiles `source` with `dotc -usejavacp -experimental -Werror`. Returns (exitCode, combined
    * stdout+stderr). `-experimental` mirrors this module's own `scalacOptions` (build.mill), since
    * `RouteBinding`/`RouteBindingMacros` are themselves `@experimental`.
    */
  def compileScala3(source: String): (Int, String) = {
    val root          = coursierRoot()
    val compilerJars  = Seq(
      findJar(root, "org/scala-lang", "scala3-compiler_3-"),
      findJar(root, "org/scala-lang", "scala3-interfaces-"),
      findJar(root, "org/scala-lang", "tasty-core_3-"),
      findJar(root, "org/scala-lang/modules", "scala-asm-"),
      // dotc's `CompilationUnit` statically references `xsbti.UseScope` (the sbt/Zinc compiler
      // bridge API) even in plain standalone-driver mode - confirmed via a real
      // `NoClassDefFoundError: xsbti/UseScope` without this jar.
      findJar(root, "org/scala-sbt", "compiler-interface-"),
    )
    val fullClasspath = (compilerJars :+ sys.props("java.class.path")).mkString(java.io.File.pathSeparator)

    val workDir = java.nio.file.Files.createTempDirectory("fatal-warnings-proof")
    val srcFile = workDir.resolve("Scratch.scala")
    val outDir  = java.nio.file.Files.createDirectory(workDir.resolve("out"))
    java.nio.file.Files.writeString(srcFile, source)

    val cmd = Seq(
      "java",
      "-cp",
      fullClasspath,
      "dotty.tools.dotc.Main",
      "-usejavacp",
      "-experimental",
      "-Werror",
      "-d",
      outDir.toString,
      srcFile.toString,
    )

    import scala.sys.process._
    val buffer = new StringBuilder
    val logger = ProcessLogger(line => buffer.append(line).append('\n'), line => buffer.append(line).append('\n'))
    val exit   = cmd.!(logger)
    (exit, buffer.toString)
  }
}
