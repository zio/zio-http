package example

import zhttp.http.{HttpData, Method, Response, _}
import zhttp.service.Server
import zio.stream.ZStream
import zio.{App, ExitCode, URIO}

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

object FileStreaming extends App {
  // Read the file as ZStream
  val content          = HttpData.fromStream {
    ZStream.fromFile(Paths.get("README.md"))
  }
  val filePathAsString = File.createTempFile("example/blahFile", ".avi").getAbsolutePath

  def testFile = {
    val tempFileName = File.createTempFile("example/blahTextFile", ".txt").getAbsolutePath
    Files.write(
      Paths.get(tempFileName),
      "This is a test file created only for testing.\n".getBytes(StandardCharsets.UTF_8),
    )
  }

  // Create HTTP route
  val app = Http.collect[Request] {
    case Method.GET -> !! / "health"   => Response.ok
    case Method.GET -> !! / "file"     => Response(data = content)
    case Method.GET -> !! / "withMime" => Response(data = HttpData.fromFile(Paths.get(filePathAsString)))
    case Method.GET -> !! / "test"     => Response(data = HttpData.fromFile(testFile))
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app.silent).exitCode
}
