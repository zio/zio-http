package zio.http.security

import java.io.File
import java.nio.file
import java.nio.file.{Files, Path => JPath, Paths}

import zio._
import zio.test.Assertion._
import zio.test._

import zio.schema._

import zio.http._
import zio.http.codec._
import zio.http.endpoint._

object StaticFileServingSpec extends ZIOSpecDefault {
  // tests Middleware.serveResources, Middleware.serveDirectory

  def mkDir: ZIO[Any, Throwable, JPath] = ZIO.attempt {
    val root = Files.createTempDirectory(null)

    val symlinks       = Files.createDirectory(root.resolve("symlinks"))
    val symlinkContent = Files.createFile(symlinks.resolve("symlinkContent"))
    Files.write(symlinkContent, "something".getBytes())

    val local = Files.createDirectory(root.resolve("local"))
    val dir   = Files.createDirectory(local.resolve("dir"))
    val file  = Files.createFile(dir.resolve("file"))
    Files.write(file, "something".getBytes())
    Files.createSymbolicLink(dir.resolve("symlink"), symlinkContent)

    val src       = Files.createDirectory(root.resolve("src"))
    val main      = Files.createDirectory(src.resolve("main"))
    val resources = Files.createDirectory(main.resolve("resources"))
    val content   = Files.createFile(resources.resolve("content"))
    Files.write(content, "something".getBytes())
    Files.createSymbolicLink(resources.resolve("symlink"), symlinkContent)

    root

  }.orDie

  def deleteDir(dir: JPath) = ZIO.attempt {
    for (file <- dir.toFile.listFiles()) {
      file.delete()
    }
  }.orDie

  def serveDirectory(path: JPath) =
    Routes.serveDirectory(Path.root / "static", path.toFile)
  val serveResources              = Routes.serveResources(Path.root / "resources")

  val serveDirectoryPaths = Gen
    .fromIterable(
      List(
        URL(Path.root / "static" / ".." / "dir" / "file"),
        URL(Path.root / "static" / "file" / ".." / "file"),
        URL(Path.root / "static" / ".." / ".." / "local" / "dir"),
        URL(Path.root / "static" / ".." / ".." / ".." / ".." / "secrets" / "secret"),
        URL(Path.root / "static" / ".." / ".." / ".." / ".." / "secrets"),
        URL(Path.root / "static" / "..\\..\\local\\dir"),
        URL(Path.root / "static" / "symlink" / "symlinkContent"),
      ),
    )
    .zip(Gen.const(Status.BadRequest))

  val serveResourcesBadRequest = Gen
    .fromIterable(
      List(
        URL(Path.root / "resources" / ".." / ".." / "secrets"),
        URL(Path.root / "resources" / ".." / ".." / "secrets" / "secret"),
        URL(Path.root / "resources" / "content" / ".." / ".." / ".." / "secrets"),
        URL(Path.root / "resources" / "content" / ".." / ".." / ".." / "secrets" / "secret"),
        URL(Path.root / "resources" / "symlink" / ".." / ".." / "local" / "dir"),
        URL(Path.root / "resources" / "symlink" / ".." / ".." / "local" / "dir"),
        URL(Path.root / "resources" / "..\\..\\local\\dir"),
      ),
    )
    .zip(Gen.const(Status.BadRequest))

  val serveResourcesNotFound = Gen
    .fromIterable(
      List(
        URL(Path.root / "resources" / "symlink" / "symlinkContent"),
      ),
    )
    .zip(Gen.const(Status.NotFound))
  val serveResourcesPaths    = serveResourcesBadRequest ++ serveResourcesNotFound

  def spec =
    suite("StaticFileServingSpec")(
      test("Middleware.serveDirectory can't escape sandbox") {
        ZIO.acquireRelease(mkDir)(deleteDir).flatMap { tempDir =>
          val routes = serveDirectory(tempDir.resolve("local/dir"))
          check(serveDirectoryPaths) { case (path, expectedStatus) =>
            for {
              response <- routes.runZIO(Request.get(path))
              status = response.status
              body   = response.body
              testResult <- assertZIO(body.asString)(equalTo(""))
            } yield assertTrue(status == expectedStatus) && testResult
          }

        }
      },
      test("Middleware.serveResources can't escape sandbox") {
        ZIO.acquireRelease(mkDir)(deleteDir).flatMap { _ =>
          val routes = serveResources
          check(serveResourcesPaths) { case (path, expectedStatus) =>
            for {
              response <- routes.runZIO(Request.get(path))
              status = response.status
              body   = response.body
              testResult <- assertZIO(body.asString)(equalTo(""))
            } yield assertTrue(status == expectedStatus) && testResult
          }

        }
      },
    )
}
