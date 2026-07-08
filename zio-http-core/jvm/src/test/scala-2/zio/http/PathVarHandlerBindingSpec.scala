/*
 * Copyright 2026 the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http

import java.util.UUID

import zio.test._

import zio.blocks.context.Context
import zio.blocks.endpoint.PathCodec._
import zio.blocks.endpoint.RoutePattern.MethodSyntax
import zio.blocks.endpoint.SegmentCodec
import zio.blocks.scope.Scope
import zio.http.Method.{GET, POST}
import zio.http.PathVarHandler.handler
import zio.http.RouteBinding._

final case class BasketId(value: String)

/**
 * Reproduces every Worked Example from
 * `.omo/drafts/route-pattern-typed-vars.md`'s "Worked examples" section, on
 * Scala 2.13, through the final `pattern -> handler(fn)` two-phase mechanism
 * (D9), using the natural `GET / int("userId") / string("postId")`-style
 * route-pattern syntax directly - the same syntax real Scala 2.13 zio-http
 * users write. Each test invokes the resulting `Route`'s handler directly
 * (bypassing the HTTP server, per Todo 5's own acceptance criteria - full
 * end-to-end TestServer wiring is Final Wave F3's job, not this todo's) and
 * asserts on the real `Response` body.
 */
object PathVarHandlerBindingSpec extends ZIOSpecDefault {

  private val scope = Scope.global
  private val req   = Request.get(URL.root)

  def spec: Spec[Any, Nothing] = suite("PathVarHandlerBinding (Scala 2.13)")(
    test("1. single named var, full use") {
      val route  = GET / int("id") -> handler((id: Int) => Response.text(s"user $id"))
      val result = route.handler.handle(req, Context.empty, 1, scope)
      assertTrue(result == ResultType.responseAsResult(Response.text("user 1")))
    },
    test("2/3. multiple vars, any declared order in the handler fn") {
      val route  = GET / int("userId") / string("postId") ->
        handler((postId: String, userId: Int) => Response.text(s"post=$postId user=$userId"))
      val result = route.handler.handle(req, Context.empty, (7, "abc"), scope)
      assertTrue(result == ResultType.responseAsResult(Response.text("post=abc user=7")))
    },
    test("6/7. same-type collision disambiguated purely by name") {
      val route  = GET / int("page") / int("limit") ->
        handler((limit: Int, page: Int) => Response.text(s"page=$page limit=$limit"))
      val result = route.handler.handle(req, Context.empty, (2, 50), scope)
      assertTrue(result == ResultType.responseAsResult(Response.text("page=2 limit=50")))
    },
    test("8. PathVar name+type miss falls through to Context by type") {
      val route =
        GET / uuid("customerId") ->
          handler((customerId: UUID, basketId: BasketId) => Response.text(s"customer=$customerId basket=$basketId"))
      val cid     = UUID.randomUUID()
      val context = Context(BasketId("basket-1"))
      val result  = route.handler.handle(req, context, cid, scope)
      assertTrue(result == ResultType.responseAsResult(Response.text(s"customer=$cid basket=BasketId(basket-1)")))
    },
    test("9. Request/Scope combine freely with PathVar and Context params, any order") {
      val route =
        GET / int("id") ->
          handler((id: Int, request: Request, basketId: BasketId) =>
            Response.text(s"${request.method} user=$id basket=$basketId"),
          )
      val context = Context(BasketId("basket-2"))
      val result  = route.handler.handle(req, context, 42, scope)
      assertTrue(result == ResultType.responseAsResult(Response.text(s"GET user=42 basket=BasketId(basket-2)")))
    },
    test("10. composed/prefixed patterns accumulate the PathVar registry across `/`, in order") {
      val userPrefix  = GET / "users" / int("userId")
      val fullPattern = userPrefix / "posts" / string("postId")
      val route  = fullPattern -> handler((userId: Int, postId: String) => Response.text(s"user=$userId post=$postId"))
      val result = route.handler.handle(req, Context.empty, (3, "hello"), scope)
      assertTrue(result == ResultType.responseAsResult(Response.text("user=3 post=hello")))
    },
    test("11. same RoutePattern reused by two handlers with independently-checked usage") {
      val pattern = GET / int("userId") / string("postId")
      val route1  = pattern -> handler((userId: Int, postId: String) => Response.text(s"full:$userId:$postId"))
      val route2  = pattern -> handler((userId: Int) => Response.text(s"partial:$userId"))
      val r1      = route1.handler.handle(req, Context.empty, (9, "x"), scope)
      val r2      = route2.handler.handle(req, Context.empty, (9, "x"), scope)
      assertTrue(
        r1 == ResultType.responseAsResult(Response.text("full:9:x")),
        r2 == ResultType.responseAsResult(Response.text("partial:9")),
      )
    },
    test("pre-built Handler value works via the separate -> overload, no macro involved") {
      val route  = GET / int("id") -> Handler.succeed(Response.ok)
      val result = route.handler.handle(req, Context.empty, 123, scope)
      assertTrue(result == ResultType.responseAsResult(Response.ok))
    },
    test("zero-arg handler (existing thunk shape) resolves through the same handler(...) entry point") {
      val route  = POST / "logout" -> handler(() => Response.ok)
      val result = route.handler.handle(req, Context.empty, (), scope)
      assertTrue(result == ResultType.responseAsResult(Response.ok))
    },
    test("Routes(...) @@ mw middleware idiom compiles unchanged with the new pattern -> handler(fn) routes") {
      val routes = Routes(
        GET / int("id") -> handler((id: Int) => Response.text(s"user $id")),
        POST / "logout" -> handler(() => Response.ok),
      ) @@ Middleware.identity[Any]
      assertTrue(routes.size == 2)
    },
    test("negative: handler param whose name+type matches no PathVar fails to compile with a clear diagnostic") {
      assertZIO(
        typeCheck("""
          import zio.blocks.endpoint.PathCodec._
          import zio.blocks.endpoint.RoutePattern.MethodSyntax
          import zio.http.Method.GET
          import zio.http.PathVarHandler.handler
          import zio.http.RouteBinding._

          GET / int("id") -> handler((wrongName: String) => Response.text(wrongName))
        """),
      )(Assertion.isLeft)
    },
    test(
      "12. multiple unused vars in a 3-segment pattern still run correctly (separateness of the warnings is verified below via a real -Xfatal-warnings compile)",
    ) {
      val route  = GET / int("userId") / string("postId") / string("tag") ->
        handler((userId: Int) => Response.text(s"user=$userId"))
      val result = route.handler.handle(req, Context.empty, (3, "ignored-post", "ignored-tag"), scope)
      assertTrue(result == ResultType.responseAsResult(Response.text("user=3")))
    },
    test("13. a Context capability declared-but-body-unused never triggers the path-var warning") {
      // `basketId` must be DECLARED to be resolved from Context at all (D7 tier 2) - "unused" here
      // means its VALUE is never referenced in the function body, which is ordinary Scala and must
      // NOT trigger the macro's "was defined in the path" warning (reserved for PathVar entries the
      // ROUTE PATTERN declares, never for Context capabilities). Confirmed at the real
      // compiler-diagnostic level (not just observed absence) by test 16 below.
      val route  = GET / int("id") -> handler((id: Int, basketId: BasketId) => Response.text(s"user=$id"))
      val result = route.handler.handle(req, Context(BasketId("unused-basket")), 7, scope)
      assertTrue(result == ResultType.responseAsResult(Response.text("user=7")))
    },
    test(
      "14 (-Xfatal-warnings build-level proof). a 3-segment pattern with only 1 var consumed FAILS to compile under fatal-warnings with exactly 2 distinct warnings naming the other 2 vars",
    ) {
      val code               =
        """package zio.http.scratch14
          |import zio.blocks.endpoint.PathCodec._
          |import zio.blocks.endpoint.RoutePattern.MethodSyntax
          |import zio.http.Method.GET
          |import zio.http.PathVarHandler.handler
          |import zio.http.RouteBinding._
          |import zio.http.Response
          |object Scratch14 {
          |  val route = GET / int("userId") / string("postId") / string("tag") ->
          |    handler((userId: Int) => Response.text(s"user=$userId"))
          |}
          |""".stripMargin
      val (exitCode, output) = FatalWarningsProof.compileScala2(code)
      val postIdWarnings = "Variable postId:String was defined in the path but is never used".r.findAllIn(output).length
      val tagWarnings    = "Variable tag:String was defined in the path but is never used".r.findAllIn(output).length
      assertTrue(exitCode != 0, postIdWarnings == 1, tagWarnings == 1)
    },
    test(
      "15 (-Xfatal-warnings build-level proof). the SAME pattern with full use compiles cleanly (zero warnings) under fatal-warnings",
    ) {
      val code               =
        """package zio.http.scratch15
          |import zio.blocks.endpoint.PathCodec._
          |import zio.blocks.endpoint.RoutePattern.MethodSyntax
          |import zio.http.Method.GET
          |import zio.http.PathVarHandler.handler
          |import zio.http.RouteBinding._
          |import zio.http.Response
          |object Scratch15 {
          |  val route = GET / int("userId") / string("postId") / string("tag") ->
          |    handler((userId: Int, postId: String, tag: String) => Response.text(s"$userId $postId $tag"))
          |}
          |""".stripMargin
      val (exitCode, output) = FatalWarningsProof.compileScala2(code)
      assertTrue(exitCode == 0, !output.contains("was defined in the path"))
    },
    test(
      "16 (-Xfatal-warnings build-level proof). a Context capability declared-but-body-unused does NOT fail under fatal-warnings (D7 exemption is a real compiler-diagnostic guarantee)",
    ) {
      val code               =
        """package zio.http.scratch16
          |import zio.blocks.endpoint.PathCodec._
          |import zio.blocks.endpoint.RoutePattern.MethodSyntax
          |import zio.http.Method.GET
          |import zio.http.PathVarHandler.handler
          |import zio.http.RouteBinding._
          |import zio.http.Response
          |final case class ScratchBasketId(value: String)
          |object Scratch16 {
          |  val route = GET / int("id") ->
          |    handler((id: Int, basketId: ScratchBasketId) => Response.text(s"user=$id"))
          |}
          |""".stripMargin
      val (exitCode, output) = FatalWarningsProof.compileScala2(code)
      assertTrue(exitCode == 0, !output.contains("was defined in the path"))
    },
    test("17. an .unused segment sandwiched between two normal vars is silently skipped, positions stay correct") {
      // Proves the positional runtime-access logic (`arrowImpl`'s `positions`/`accessAt`) is
      // unaffected by an `Ignored` entry sitting in the MIDDLE of the pattern's real value tuple:
      // `c`'s runtime position must be `_3` (not `_2`), since `b` still occupies a real slot in
      // `pvEntries` and must never be filtered out (task requirement (d)).
      val route  = GET / int("a") / SegmentCodec.string("b").unused / int("c") ->
        handler((a: Int, c: Int) => Response.text(s"a=$a c=$c"))
      val result = route.handler.handle(req, Context.empty, (1, "ignored-b", 3), scope)
      assertTrue(result == ResultType.responseAsResult(Response.text("a=1 c=3")))
    },
    test(
      "18. a handler referencing an .unused segment still binds its real decoded value (a lint, not a functional change)",
    ) {
      val route  = GET / int("a") / SegmentCodec.string("b").unused / int("c") ->
        handler((a: Int, b: String, c: Int) => Response.text(s"a=$a b=$b c=$c"))
      val result = route.handler.handle(req, Context.empty, (1, "hello", 3), scope)
      assertTrue(result == ResultType.responseAsResult(Response.text("a=1 b=hello c=3")))
    },
    test(
      "19 (-Xfatal-warnings build-level proof). an .unused segment never referenced by the handler compiles cleanly (zero warnings) - the whole point of .unused",
    ) {
      val code               =
        """package zio.http.scratch19
          |import zio.blocks.endpoint.PathCodec._
          |import zio.blocks.endpoint.RoutePattern.MethodSyntax
          |import zio.blocks.endpoint.SegmentCodec
          |import zio.http.Method.GET
          |import zio.http.PathVarHandler.handler
          |import zio.http.RouteBinding._
          |import zio.http.Response
          |object Scratch19 {
          |  val route = GET / int("userId") / SegmentCodec.string("postId").unused ->
          |    handler((userId: Int) => Response.text(s"user=$userId"))
          |}
          |""".stripMargin
      val (exitCode, output) = FatalWarningsProof.compileScala2(code)
      assertTrue(exitCode == 0, !output.contains("was defined in the path"), !output.contains("was marked .unused"))
    },
    test(
      "20 (-Xfatal-warnings build-level proof). an .unused segment referenced by the handler emits the new 'marked .unused but referenced' warning (a lint, not a compile failure of its own accord - it only fails here because -Xfatal-warnings escalates EVERY warning to an error, exactly like test 14's plain-unused case)",
    ) {
      val code               =
        """package zio.http.scratch20
          |import zio.blocks.endpoint.PathCodec._
          |import zio.blocks.endpoint.RoutePattern.MethodSyntax
          |import zio.blocks.endpoint.SegmentCodec
          |import zio.http.Method.GET
          |import zio.http.PathVarHandler.handler
          |import zio.http.RouteBinding._
          |import zio.http.Response
          |object Scratch20 {
          |  val route = GET / int("userId") / SegmentCodec.string("postId").unused ->
          |    handler((userId: Int, postId: String) => Response.text(s"user=$userId post=$postId"))
          |}
          |""".stripMargin
      val (exitCode, output) = FatalWarningsProof.compileScala2(code)
      val postIdWarnings     =
        "Variable postId:String was marked .unused but is referenced by the handler".r.findAllIn(output).length
      assertTrue(exitCode != 0, postIdWarnings == 1, !output.contains("was defined in the path"))
    },
    test(
      "21 (-Xfatal-warnings build-level proof). a plain unconsumed var and a referenced .unused var in the same pattern fire BOTH warnings independently, with correct distinct text each",
    ) {
      val code               =
        """package zio.http.scratch21
          |import zio.blocks.endpoint.PathCodec._
          |import zio.blocks.endpoint.RoutePattern.MethodSyntax
          |import zio.blocks.endpoint.SegmentCodec
          |import zio.http.Method.GET
          |import zio.http.PathVarHandler.handler
          |import zio.http.RouteBinding._
          |import zio.http.Response
          |object Scratch21 {
          |  val route = GET / int("userId") / SegmentCodec.string("postId").unused / string("tag") ->
          |    handler((userId: Int, postId: String) => Response.text(s"user=$userId post=$postId"))
          |}
          |""".stripMargin
      val (exitCode, output) = FatalWarningsProof.compileScala2(code)
      val postIdWarnings     =
        "Variable postId:String was marked .unused but is referenced by the handler".r.findAllIn(output).length
      val tagWarnings = "Variable tag:String was defined in the path but is never used".r.findAllIn(output).length
      assertTrue(exitCode != 0, postIdWarnings == 1, tagWarnings == 1)
    },
  )
}

/**
 * Drives the REAL `scalac` compiler (out-of-process, `-Xfatal-warnings`)
 * against a scratch snippet, using this test JVM's own `java.class.path` (the
 * exact classpath mill resolved for `core.jvm[2.13.18].test`) plus the Scala
 * 2.13 compiler's own jar (located under the same coursier cache the running
 * JVM's `scala-library` jar came from). This proves the unused-PathVar warning
 * is a REAL compiler diagnostic participating in a warnings-as-errors build
 * (Todo 6's deliverable 3) - not an informational side effect - since a genuine
 * external `scalac` process, given the real production
 * `RouteBindingMacros.scala`, either fails or succeeds to compile based on it.
 */
private object FatalWarningsProof {

  private def coursierRoot(): java.io.File = {
    val cp       = sys.props("java.class.path").split(java.io.File.pathSeparatorChar)
    val libEntry = cp
      .find(_.replace('\\', '/').contains("/org/scala-lang/scala-library/"))
      .getOrElse(
        throw new IllegalStateException(s"scala-library not found on java.class.path: ${cp.mkString(", ")}"),
      )
    // .../org/scala-lang/scala-library/<version>/scala-library-<version>.jar -> cache root is 4 dirs up
    new java.io.File(libEntry).getParentFile.getParentFile.getParentFile.getParentFile.getParentFile
  }

  private def findJar(root: java.io.File, groupPath: String, artifactPrefix: String): String = {
    val dir                                         = new java.io.File(root, groupPath)
    def search(d: java.io.File): List[java.io.File] =
      Option(d.listFiles()).toList.flatten.flatMap { f =>
        if (f.isDirectory) search(f)
        else if (
          f.getName.startsWith(artifactPrefix) && f.getName.endsWith(".jar") && !f.getName.contains("sources") &&
          !f.getName.contains("javadoc")
        )
          List(f)
        else Nil
      }
    search(dir)
      .sortBy(_.getName)
      .lastOption
      .map(_.getAbsolutePath)
      .getOrElse(throw new IllegalStateException(s"Could not locate $artifactPrefix*.jar under $dir"))
  }

  /**
   * Compiles `source` with `scalac -usejavacp -Xfatal-warnings`. Returns
   * (exitCode, combined stdout+stderr).
   */
  def compileScala2(source: String): (Int, String) = {
    val root          = coursierRoot()
    val compilerJar   = findJar(root, "org/scala-lang", "scala-compiler-")
    val fullClasspath = Seq(compilerJar, sys.props("java.class.path")).mkString(java.io.File.pathSeparator)

    val workDir = java.nio.file.Files.createTempDirectory("fatal-warnings-proof")
    val srcFile = workDir.resolve("Scratch.scala")
    val outDir  = java.nio.file.Files.createDirectory(workDir.resolve("out"))
    java.nio.file.Files.write(srcFile, source.getBytes("UTF-8"))

    val cmd = Seq(
      "java",
      "-cp",
      fullClasspath,
      "scala.tools.nsc.Main",
      "-usejavacp",
      "-Xfatal-warnings",
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
