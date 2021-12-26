package example

import zhttp.html._
import zhttp.http._
import zhttp.service.Server
import zio._

object HtmlTemplating extends App {

  def app: HttpApp[Any, Nothing] = Http.html {
    html(
      head(
        title("ZIO Http"),
      ),
      body(
        div(
          css := "container" :: Nil,
          h1("Hello World"),
          ul(
            styles := Seq("list-style" -> "none"),
            li(
              a(href := "/hello/world", "Hello World"),
            ),
            li(
              a(href := "/hello/world/again", "Hello World Again"),
            ),
            (2 to 10) map { i =>
              li(
                a(href := s"/hello/world/i", s"Hello World $i"),
              )
            },
          ),
        ),
      ),
    )
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
