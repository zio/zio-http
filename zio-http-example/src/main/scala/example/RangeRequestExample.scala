//> using dep "dev.zio::zio-http:3.4.0"

package example

import zio._
import zio.http._
import zio.http.template._
import java.io.File
import java.nio.file.Files

/**
 * Example demonstrating HTTP Range request support for partial content delivery.
 * 
 * This example shows how to serve files with support for:
 * - Single byte ranges (e.g., bytes=0-499)
 * - Multiple byte ranges (e.g., bytes=0-499,1000-1499)
 * - Suffix ranges (e.g., bytes=-500 for last 500 bytes)
 * - Open-ended ranges (e.g., bytes=500- for everything from byte 500)
 * 
 * Test with curl:
 * - Full file: curl http://localhost:8080/video.mp4
 * - First 1MB: curl -H "Range: bytes=0-1048575" http://localhost:8080/video.mp4
 * - Last 1MB: curl -H "Range: bytes=-1048576" http://localhost:8080/video.mp4
 * - Multiple ranges: curl -H "Range: bytes=0-999,2000-2999" http://localhost:8080/video.mp4
 */
object RangeRequestExample extends ZIOAppDefault {

  // Create a sample file for testing
  def createSampleFile(): Task[File] = ZIO.attemptBlocking {
    val file = Files.createTempFile("sample", ".txt").toFile
    file.deleteOnExit()
    
    // Create content with clear sections for testing ranges
    val content = new StringBuilder()
    for (i <- 0 until 10) {
      content.append(s"=== Section $i ===\n")
      content.append(s"Content for section $i. " * 10)
      content.append("\n\n")
    }
    
    Files.write(file.toPath, content.toString.getBytes("UTF-8"))
    println(s"Created sample file: ${file.getAbsolutePath}")
    println(s"File size: ${file.length()} bytes")
    file
  }

  val app = for {
    sampleFile <- createSampleFile()
    staticDir <- ZIO.attemptBlocking {
      val dir = Files.createTempDirectory("static-files").toFile
      dir.deleteOnExit()
      val testFile = new File(dir, "test.txt")
      Files.write(testFile.toPath, "Static file content with Range support!".getBytes("UTF-8"))
      testFile.deleteOnExit()
      dir
    }
    routes = Routes(
      // Serve file with automatic Range support
      Method.GET / "download" -> Handler.fromFile(sampleFile),
      
      // Example with a video file (if exists)
      Method.GET / "video.mp4" -> Handler.fromFile(
        new File("src/main/resources/sample-video.mp4")
      ).catchAll { _ =>
        Handler.fail(Response.status(Status.NotFound))
      },
      
      // Info endpoint showing how to make range requests
      Method.GET / "info" -> Handler.html(
        html(
          head(
            title("Range Request Example"),
            style("""
              body { font-family: monospace; padding: 20px; }
              pre { background: #f0f0f0; padding: 10px; }
              .section { margin: 20px 0; }
            """)
          ),
          body(
            h1("HTTP Range Request Example"),
            
            div(
              css := "section",
              h2("Sample File"),
              p("Download full file: "),
              a(href := "/download", "/download"),
              p(s"File size: ${sampleFile.length()} bytes")
            ),
            
            div(
              css := "section",
              h2("Test with curl:"),
              pre("""
                |# Download full file
                |curl http://localhost:8080/download
                |
                |# Download first 100 bytes
                |curl -H "Range: bytes=0-99" http://localhost:8080/download
                |
                |# Download bytes 100-199
                |curl -H "Range: bytes=100-199" http://localhost:8080/download
                |
                |# Download last 100 bytes
                |curl -H "Range: bytes=-100" http://localhost:8080/download
                |
                |# Download from byte 500 to end
                |curl -H "Range: bytes=500-" http://localhost:8080/download
                |
                |# Download multiple ranges (multipart response)
                |curl -H "Range: bytes=0-99,200-299,400-499" http://localhost:8080/download
                |
                |# Check with verbose output to see headers
                |curl -v -H "Range: bytes=0-99" http://localhost:8080/download
              """.stripMargin)
            ),
            
            div(
              css := "section",
              h2("JavaScript Example:"),
              pre("""
                |fetch('/download', {
                |  headers: {
                |    'Range': 'bytes=0-999'
                |  }
                |})
                |.then(response => {
                |  console.log('Status:', response.status); // Should be 206
                |  console.log('Content-Range:', response.headers.get('Content-Range'));
                |  return response.text();
                |})
                |.then(data => console.log('Partial content:', data));
              """.stripMargin)
            ),
            
            div(
              css := "section",
              h2("Video Streaming Example:"),
              p("HTML5 video elements automatically use Range requests for streaming:"),
              pre("""
                |<video controls>
                |  <source src="/video.mp4" type="video/mp4">
                |</video>
              """.stripMargin),
              p("The browser will automatically make Range requests to stream the video efficiently.")
            )
          )
        )
      )
    ).handleError(e => Response.text(s"Error: ${e.getMessage}").status(Status.InternalServerError)) @@ 
    Middleware.serveDirectory(Path.root / "static", staticDir)
  } yield routes

  override def run = 
    app.flatMap { routes =>
      Console.printLine("Server starting on http://localhost:8080") *>
      Console.printLine("Visit http://localhost:8080/info for examples") *>
      Server.serve(routes).provide(Server.default)
    }
}