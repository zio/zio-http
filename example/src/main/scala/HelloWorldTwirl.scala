import zhttp.endpoint.{*, _}
import zhttp.http.Method.GET
import zhttp.http._
import zhttp.nav.Navigation
import zhttp.service.Server
import zio.{ExitCode, URIO}

object HelloWorldTwirl extends zio.App {
  def h2: HttpApp[Any, Nothing] = GET / "a" / *[Int] / "b" / *[Boolean] to { pathParams =>
    val (a, b)              = pathParams.params
    val response: UResponse = Response.text(advanced.html.index(a, b).toString())
    response
      .asInstanceOf[UHttpResponse]
      .copy(headers = List(Header.custom("content-type", "text/html")))
  }

  val h1: HttpApp[Any, Nothing] = HttpApp.collect {
    case Method.GET -> !!         => {
      val response: UResponse = Response.text(html.index("John Doe").toString())

      response
        .asInstanceOf[UHttpResponse]
        .copy(headers = List(Header.custom("content-type", "text/html")))
    }
    case Method.GET -> !! / "nav" => {
      val nav                 = Seq(
        Navigation("link-1", "http://google.com", Some("bi-alarm-fill")),
        Navigation("link-2", "http://google.com", Some("bi-award")),
        Navigation("link-3", "http://google.com", Some("bi-bank")),
        Navigation(
          "link-4",
          "http://google.com",
          Some("bi-bank"),
          Seq(
            Navigation("link-1", "http://google.com", None),
            Navigation("link-2", "http://google.com", None),
            Navigation("link-3", "http://google.com", None),
          ),
        ),
      )
      val response: UResponse = Response.text(advanced.html.template(1, false, nav).toString())
      response
        .asInstanceOf[UHttpResponse]
        .copy(headers = List(Header.custom("content-type", "text/html")))
    }
  }

  def app: HttpApp[Any, Throwable] = h1 +++ h2

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
