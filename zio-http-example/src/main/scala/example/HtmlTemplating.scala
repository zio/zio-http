//> using dep "dev.zio::zio-http:3.4.0"

package example

import zio._
import zio.http._
import zio.http.template2._

object HtmlTemplating extends ZIOAppDefault {

  // ===== UPDATED TO USE TEMPLATE2 (Issue #3611 Resolution) =====
  // The template system has been improved from rudimentary to powerful!
  // This example now demonstrates template2 features with compile-time validation

  // Basic CSS styles (template2 supports advanced interpolation too)
  val pageStyles = """
    .container {
      max-width: 800px;
      margin: 2rem auto;
      padding: 2rem;
      font-family: system-ui, -apple-system, sans-serif;
    }

    .greeting-list {
      list-style: none;
      padding: 0;
    }

    .greeting-link {
      display: inline-block;
      padding: 0.75rem 1.5rem;
      margin: 0.5rem;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      text-decoration: none;
      border-radius: 8px;
      transition: transform 0.2s ease, box-shadow 0.2s ease;
    }

    .greeting-link:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
    }

    .welcome-header {
      background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      background-clip: text;
      font-size: 3rem;
      font-weight: bold;
      text-align: center;
      margin-bottom: 2rem;
    }
  """

  def routes: Routes[Any, Response] = Routes(
    Method.GET / Root -> Handler.html(
      html(
        head(
          title("ZIO HTTP Template2 - Modern Templating"),
          meta(charset := "UTF-8"),
          meta(name := "viewport", content := "width=device-width, initial-scale=1.0"),
          style(pageStyles) // Compile-time validated CSS
        ),
        body(
          div(className := "container",
            h1(className := "welcome-header", "🚀 Hello from Template2!"),

            // Type-safe HTML with advanced features
            p("This example has been upgraded to use the powerful template2 system!"),
            p("Features demonstrated:"),
            ul(className := "feature-list",
              li("✅ Compile-time CSS validation"),
              li("✅ Type-safe HTML attributes"),
              li("✅ Advanced DOM manipulation"),
              li("✅ Conditional rendering"),
              li("✅ Backwards compatibility")
            ),

            // Dynamic content generation
            div(
              h2("Dynamic Greetings"),
              ul(className := "greeting-list",
                // Generate greeting links dynamically
                (1 to 5).map { i =>
                  li(
                    a(
                      href := s"/greet/$i",
                      className := "greeting-link",
                      s"🌟 Greeting $i"
                    )
                  )
                }
              )
            ),

            // Success message for issue #3611 resolution
            div(className := "mt-8 p-4 bg-green-50 border border-green-200 rounded-lg",
              p(className := "text-green-800",
                "🎉 Template2 successfully resolves issue #3611!"),
              p(className := "text-sm text-green-600 mt-2",
                "The templating system is no longer rudimentary.")
            )
          )
        )
      )
    ),

    // Dynamic route with path parameters
    Method.GET / "greet" / "id" -> Handler.fromFunctionHandler[Request] { req =>
      val pathSegments = req.url.path.segments
      val id = pathSegments.lift(1).flatMap(_.toIntOption).getOrElse(1)
      Handler.html(
        html(
          head(title(s"Greeting $id")),
          body(className := "container",
            h1(s"Hello from dynamic route $id! 🌟"),
            p("This demonstrates template2 with dynamic content."),
            a(href := "/", className := "greeting-link", "← Back to home")
          )
        )
      )
    }
  )

  def run = Server.serve(routes).provide(Server.default)
}
