import React, { useState } from 'react';
import CodeBlock from '@theme/CodeBlock';
import Link from '@docusaurus/Link';
import clsx from 'clsx';
import styles from './styles.module.css';

const TABS = [
  {
    label: 'Create an HTTP Server',
    code: `import zio._
import zio.http._

object GreetingServer extends ZIOAppDefault {

  val routes =
    Routes(
      Method.GET / "greet" -> handler { (req: Request) =>
        val name = req.queryParamOrElse("name", "World")
        Response.text(s"Hello, $name!")
      },
      Method.GET / "health" -> handler {
        Response.ok
      }
    )

  def run = Server.serve(routes).provide(Server.default)
}`,
  },
  {
    label: 'Define Endpoints',
    code: `import zio._
import zio.http._
import zio.http.endpoint._
import zio.http.codec.Doc

val greetEndpoint =
  Endpoint(GET / "greet" ?? Doc.p("Greet an individual by name"))
    .query(
      HttpCodec.query[String]("name") ??
        Doc.p("The name of the person to greet")
    )
    .out[String]

val greetRoute: Route[Any, Nothing] =
  greetEndpoint.implementHandler(
    handler((name: String) => s"Hello, $name!")
  )

def run = Server
  .serve(Routes(greetRoute))
  .provide(Server.default)`,
  },
  {
    label: 'Add Middleware',
    code: `import zio._
import zio.http._
import zio.http.Middleware

val routes =
  Routes(
    Method.GET / "api" / "data" -> handler {
      Response.json("""{"status":"ok"}""")
    }
  )

// Compose middleware with the @@ operator
val app = routes
  @@ Middleware.timeout(10.seconds)
  @@ Middleware.requestLogging()
  @@ Middleware.cors()

def run = Server.serve(app).provide(Server.default)`,
  },
  {
    label: 'HTTP Client',
    code: `import zio._
import zio.http._

object ApiClient extends ZIOAppDefault {

  val program: ZIO[Client, Throwable, Unit] =
    for {
      url <- ZIO.fromEither(
               URL.decode("https://api.example.com/users")
             )
      response <- ZClient.batched(
                    Request
                      .get(url)
                      .addHeader(Header.Accept.json)
                  )
      body     <- response.bodyAs[String]
      _        <- ZIO.debug(s"Response: $body")
    } yield ()

  def run = program.provide(Client.default)
}`,
  },
  {
    label: 'WebSocket',
    code: `import zio._
import zio.http._
import zio.http.ChannelEvent._

val wsRoute =
  Method.GET / "ws" -> handler {
    Handler.webSocket { channel =>
      channel.receiveAll {
        case Read(WebSocketFrame.Text(msg)) =>
          channel.send(
            Read(WebSocketFrame.text(s"Echo: $msg"))
          )
        case ExceptionCaught(cause) =>
          ZIO.logError(s"Channel error: \${cause.getMessage}")
        case _ =>
          ZIO.unit
      }
    }.toResponse
  }

def run = Server
  .serve(Routes(wsRoute))
  .provide(Server.default)`,
  },
];

export default function HomepageCodeSnippet() {
  const [activeTab, setActiveTab] = useState(0);

  return (
    <section className={styles.codeSnippetSection}>
      <div className={styles.innerContainer}>
        {/* Left Column */}
        <div className={styles.leftColumn}>
          <h2 className="section-title">ZIO HTTP in Action</h2>
          <p>
            Explore idiomatic Scala patterns for building high-performance,
            type-safe HTTP servers and clients. From routing and endpoints to
            middleware and real-time communication.
          </p>
          <div>
            <Link
              className="button button--outline button--lg"
              to="/">
              Explore the Docs
            </Link>
          </div>
        </div>

        {/* Right Column */}
        <div className={styles.rightColumn}>
          <div className={styles.codePanel}>
            {/* Tab Bar */}
            <div className={styles.tabBar} role="tablist">
              {TABS.map((tab, idx) => (
                <button
                  key={idx}
                  id={`tab-${idx}`}
                  className={clsx(
                    styles.tab,
                    activeTab === idx && styles.tabActive
                  )}
                  onClick={() => setActiveTab(idx)}
                  aria-selected={activeTab === idx}
                  aria-controls={`tabpanel-${idx}`}
                  type="button"
                  role="tab">
                  {tab.label}
                </button>
              ))}
            </div>

            {/* Code Area — Docusaurus CodeBlock (highlights Scala + has copy) */}
            <div
              id={`tabpanel-${activeTab}`}
              className={styles.codeArea}
              role="tabpanel"
              aria-labelledby={`tab-${activeTab}`}>
              <CodeBlock key={activeTab} language="scala" showLineNumbers>
                {TABS[activeTab].code.trim()}
              </CodeBlock>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
