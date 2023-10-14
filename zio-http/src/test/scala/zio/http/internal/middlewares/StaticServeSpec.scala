package zio.http.internal.middlewares

import java.io.File
import java.nio.file.{Files, Path, Paths}

import zio._
import zio.test.Assertion._
import zio.test._

import zio.http._
import zio.http.internal.HttpAppTestExtensions

import zio.http

object StaticServeSpec extends ZIOHttpSpec with HttpAppTestExtensions {

  // Helper function for creating a mock directory structure for testing
  def createMockDirectoryStructure(root: String): Path = {
    val tempDir = Files.createTempDirectory(Paths.get("").toAbsolutePath(), root)

    val dir1       = Files.createDirectory(tempDir.resolve("etc"))
    val passwdFile = Files.createFile(dir1.resolve("passwd"))
    Files.writeString(passwdFile, "root:x:0:0:root:/root:/bin/bash")

    val dir2  = Files.createDirectory(tempDir.resolve("home"))
    val dir3  = Files.createDirectory(dir2.resolve("src"))
    val file1 = Files.createFile(dir3.resolve("file1.json"))
    Files.writeString(file1, """{"name": "zio"}""")

    val dir4  = Files.createDirectory(dir2.resolve("symlinkTest"))
    val file2 = Files.createFile(dir4.resolve("file1.json"))
    Files.writeString(file2, """{"path": "/home/src/symblinkTest/file1.json"}""")

    Files.createSymbolicLink(dir3.resolve("symblinkTest"), dir4)

    val dir5  = Files.createDirectory(dir3.resolve("cleanFolder"))
    val file3 = Files.createFile(dir5.resolve("file1.json"))
    Files.writeString(file3, """{"name": "zio1"}""")

    /* Mock Directory Structure
            ├── etc
            │   └── passwd
            └── home
                ├── src
                │   ├── cleanFolder
                │   │   └── file1.json
                │   ├── file1.json
                │   └── symblinkTest -> /workspaces/zio-http/zio-http/test9035643202693353291/home/symlinkTest
                └── symlinkTest
                    └── file1.json
     */

    tempDir
  }

  // Helper function for deleting a mock directory structure for testing
  def deleteMockDirectoryStructure(root: Path): UIO[Unit] = {
    val tempDir = root.getParent()
    ZIO.attempt(Files.walk(root).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete(_))).ignore *>
      ZIO.attempt(Files.deleteIfExists(tempDir)).ignore
  }

  // Initializing static server (Hosting files from the mock directory structure)
  val root: Path             = createMockDirectoryStructure("test")
  val path: String           = root.resolve("home").resolve("src").toString()
  val staticServerMiddleware = Middleware.serveDirectory(http.Path("files"), new File(path))
  val app                    = (Method.GET / "files" -> Handler.ok).toHttpApp @@ staticServerMiddleware

  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("StaticServeSpec")(
      test("File access check") {
        for {
          r1 <- app.runZIO(Request.get(URL(Root / "files" / "file1.json")))
          r2 <- app.runZIO(Request.get(URL(Root / "files")))
          r3 <- app.runZIO(Request.get(URL(Root / "files" / "cleanFolder" / "file1.json")))

          result1 <- r1.body.asString
          result2 = r2.status == Status.Ok
          result3 = r3.status == Status.Ok

        } yield assert(result1)(equalTo("""{"name": "zio"}""")) &&
          assertTrue(result2) &&
          assertTrue(result3)
      },

      // test for directory traversal vulnerability using dot dot slash attacks
      test("DotDotSlash") {
        for {
          r1 <- app.runZIO(Request.get(URL(Root / "files" / ".." / "src")))
          r2 <- app.runZIO(Request.get(URL(Root / "files" / "file1.json" / ".." / "file1.json")))
          r3 <- app.runZIO(Request.get(URL(Root / "files" / ".." / ".." / "etc" / "passwd")))
          r4 <- app.runZIO(Request.get(URL(Root / "files" / ".." / ".." / ".." / ".." / "etc" / "passwd")))
          r5 <- app.runZIO(Request.get(URL(Root / "files" / ".." / ".." / ".." / ".." / ".." / "etc" / "passwd")))
          r6 <- app.runZIO(Request.get(URL(Root / "files" / "....//....//etc//passwd")))

          // windows
          r7 <- app.runZIO(Request.get(URL(Root / "files" / "..\\..\\etc\\passwd")))

          result = r1.status == Status.BadRequest &&
            r2.status == Status.BadRequest &&
            r3.status == Status.BadRequest &&
            r4.status == Status.BadRequest &&
            r5.status == Status.BadRequest &&
            r6.status == Status.BadRequest

        } yield assertTrue(result)
      },
      test("DotDotSlash with symlink") {
        for {
          r1 <- app.runZIO(Request.get(URL(Root / "files" / "symblinkTest" / "file1.json")))
          r2 <- app.runZIO(Request.get(URL(Root / "files" / "symlinkTest" / ".." / "file1.json")))

          result = r1.status == Status.BadRequest &&
            r2.status == Status.BadRequest

        } yield assertTrue(result)
      },
      test("Encoded url") {
        println("URL ------> " + URL(Root / "files" / "%2e%2e%2f%2e%2e%2fetc%2fpasswd"))
        for {
          r1 <- app.runZIO(Request.get(URL(Root / "files" / "%2e%2e%2f%2e%2e%2fetc%2fpasswd")))
          r2 <- app.runZIO(Request.get(URL(Root / "files" / "%uff0e%uff0e%u2215%uff0e%uff0e%u2215etc%u2215passwd")))
          r3 <- app.runZIO(Request.get(URL(Root / "files" / "%252e%252e%252f%252e%252e%252fetc%252fpasswd")))

          result = r1.status == Status.NotFound &&
            r2.status == Status.NotFound &&
            r3.status == Status.NotFound

        } yield assertTrue(result)
      },
    ) @@ TestAspect.afterAll(deleteMockDirectoryStructure(root))
}
