// DEMO SERVER — showcasing real zio-http v4 Loom engine with zio-blocks APIs
package zio.http.demo

import scala.annotation.experimental

import zio.blocks.chunk.Chunk
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.blocks.html._
import zio.blocks.schema.Schema

import zio.http._
import zio.http.ResultType._

@experimental
object DemoLoomTeaHouseMain {

  final case class LoomStatus(
    engine: String,
    virtualThreadsSpun: Long,
    yarnSpooledMeters: Double,
    caffeineLevel: String,
    fortune: String,
    timestamp: String,
  )
  object LoomStatus {
    implicit val schema: Schema[LoomStatus] = Schema.derived[LoomStatus]
  }

  def main(args: Array[String]): Unit = {
    val port = 18473

    val fortuneRoute = Route(
      RoutePattern(Method.GET, "/api/loom/fortune"),
      handler { (_: Request) =>
        val status = LoomStatus(
          engine = "zio-http v4 custom HTTP/2 Loom engine",
          virtualThreadsSpun = 108642L,
          yarnSpooledMeters = 3.14159265 * 42,
          caffeineLevel = "dangerously optimal",
          fortune = "A virtual thread you spawn today will park on an I/O you forgot about tomorrow.",
          timestamp = java.time.Instant.now().toString,
        )
        responseAsResult(Response.json(LoomStatus.schema.jsonCodec.encodeToString(status)))
      },
    )

    val teaHouseRoute = Route(
      RoutePattern(Method.GET, "/loom/tea-house"),
      handler { (_: Request) =>
        responseAsResult(
          Response(
            Status.Ok,
            Headers("content-type" -> "text/html"),
            Body.fromString("<!DOCTYPE html>" + teaHousePage.render),
          ),
        )
      },
    )

    val routes  = Routes(fortuneRoute, teaHouseRoute)
    val context = Context.empty.add(LoomServer(Connector(bind = BindAddress.localhost(port))))
    val _       = Server.serve(routes, context)

    println(s"DEMO_SERVER_STARTED port=$port pid=${java.lang.ProcessHandle.current().pid()}")

    Thread.sleep(Long.MaxValue)
  }

  private def teaHousePage: Dom.Element =
    html(
      lang := "en",
      head(
        meta(charset := "utf-8"),
        meta(name    := "viewport", content := "width=device-width, initial-scale=1"),
        title("The Loom Weaver's Midnight Tea House"),
        style(
          Css.Raw(
            """
              |@keyframes hue-drift { 0% { background-position: 0% 50%; } 100% { background-position: 200% 50%; } }
              |@keyframes rise { 0% { transform: translateY(0) scaleX(1); opacity: 0.85; }
              |  100% { transform: translateY(-70px) scaleX(1.6); opacity: 0; } }
              |@keyframes glow { 0%,100% { text-shadow: 0 0 6px #7CFFCB, 0 0 18px #46E0FF, 0 0 32px #B57CFF; }
              |  50% { text-shadow: 0 0 14px #B57CFF, 0 0 26px #46E0FF, 0 0 44px #7CFFCB; } }
              |@keyframes spin-slow { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
              |* { box-sizing: border-box; }
              |body {
              |  margin: 0; min-height: 100vh; display: flex; flex-direction: column; align-items: center;
              |  justify-content: center; gap: 1.25rem; padding: 3rem 1rem; font-family: 'Courier New', monospace;
              |  color: #EAFBFF; background: linear-gradient(120deg, #0d0221, #1b0e3d, #0a2740, #0d0221);
              |  background-size: 400% 400%; animation: hue-drift 12s ease-in-out infinite alternate;
              |}
              |h1 { font-size: 1.9rem; letter-spacing: 0.06em; animation: glow 2.4s ease-in-out infinite; margin: 0; }
              |.subtitle { opacity: 0.75; font-size: 0.95rem; margin: -0.5rem 0 0.5rem; }
              |.cup-wrap { position: relative; width: 220px; height: 200px; }
              |.steam { position: absolute; bottom: 118px; width: 6px; height: 40px; border-radius: 6px;
              |  background: rgba(234,251,255,0.55); filter: blur(1px); animation: rise 2.6s ease-in infinite; }
              |.steam.s1 { left: 78px; animation-delay: 0s; }
              |.steam.s2 { left: 104px; height: 52px; animation-delay: 0.6s; }
              |.steam.s3 { left: 130px; animation-delay: 1.2s; }
              |.wheel { animation: spin-slow 9s linear infinite; transform-origin: 40px 40px; }
              |.ascii-cat { background: rgba(255,255,255,0.04); border: 1px solid rgba(124,255,203,0.35);
              |  border-radius: 10px; padding: 0.9rem 1.2rem; line-height: 1.15; font-size: 0.82rem;
              |  color: #7CFFCB; text-shadow: 0 0 6px rgba(124,255,203,0.5); white-space: pre; }
              |.easter-egg { font-size: 0.72rem; opacity: 0.45; max-width: 32rem; text-align: center; }
              |.badge { border: 1px dashed #46E0FF; border-radius: 999px; padding: 0.3rem 0.9rem;
              |  font-size: 0.78rem; color: #46E0FF; }
              |a.fortune-link { color: #B57CFF; text-decoration: none; border-bottom: 1px dotted #B57CFF; }
              |""".stripMargin,
          ),
        ),
      ),
      body(
        h1("🧵 The Loom Weaver's Midnight Tea House"),
        div(cls("subtitle"), "est. whenever the H2 frames stop misbehaving"),
        div(
          cls("cup-wrap"),
          svg(
            attr("viewBox") := "0 0 220 160",
            attr("width")   := 220,
            attr("height")  := 160,
            element("defs")(
              element("linearGradient")(
                id         := "tea",
                attr("x1") := "0",
                attr("y1") := "0",
                attr("x2") := "0",
                attr("y2") := "1",
                element("stop")(attr("offset") := "0%", attr("stop-color")   := "#B57CFF"),
                element("stop")(attr("offset") := "100%", attr("stop-color") := "#46E0FF"),
              ),
            ),
            element("g")(
              attr("class")        := "wheel",
              element("circle")(
                attr("cx")                 := 40,
                attr("cy")                 := 118,
                attr("r")                  := 26,
                attr("fill")               := "none",
                attr("stroke")             := "#7CFFCB",
                attr("stroke-width")       := 2,
                attr("stroke-dasharray")   := "4 6",
              ),
              element("circle")(attr("cx") := 40, attr("cy") := 118, attr("r") := 4, attr("fill") := "#7CFFCB"),
            ),
            element("path")(
              attr("d")            := "M60 100 h100 l-10 40 a10 10 0 0 1 -10 8 h-60 a10 10 0 0 1 -10 -8 Z",
              attr("fill")         := "url(#tea)",
              attr("stroke")       := "#EAFBFF",
              attr("stroke-width") := 2,
            ),
            element("path")(
              attr("d")            := "M160 108 q22 0 22 18 q0 18 -22 14",
              attr("fill")         := "none",
              attr("stroke")       := "#EAFBFF",
              attr("stroke-width") := 3,
            ),
            element("ellipse")(
              attr("cx")           := 110,
              attr("cy")           := 100,
              attr("rx")           := 50,
              attr("ry")           := 7,
              attr("fill")         := "#EAFBFF",
            ),
          ),
          div(cls("steam", "s1")),
          div(cls("steam", "s2")),
          div(cls("steam", "s3")),
        ),
        div(
          cls("ascii-cat"),
          """ /\_/\      "your virtual threads
| o.o |       are steeping nicely."
 > ^ <
""",
        ),
        div(
          cls("badge"),
          a(href := "/api/loom/fortune", cls("fortune-link"), "GET /api/loom/fortune"),
          " → today's tea-leaf JSON reading",
        ),
        div(
          cls("easter-egg"),
          "psst — you found the raw HTML. No client-side JS was harmed rendering this page; ",
          "it was woven server-side, one virtual thread at a time, by zio-http's custom H2 Loom engine. ",
          "🐈 Congrats on curling all the way down here.",
        ),
        script().inlineJs(
          Js(
            """console.log("%c🧵 loom tea house says hi", "color:#7CFFCB;font-weight:bold;font-size:14px");""",
          ),
        ),
      ),
    )

  private def cls(names: String*): Dom.Attribute = `class`(names)
}
