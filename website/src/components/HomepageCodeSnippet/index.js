import React, { useState } from 'react';
import CodeBlock from '@theme/CodeBlock';
import Link from '@docusaurus/Link';
import clsx from 'clsx';
import styles from './styles.module.css';

const TABS = [
  {
    label: 'Create an HTTP Server',
    fileName: 'GreetingServer.scala',
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
    fileName: 'UserEndpoints.scala',
    code: `import zio._
import zio.http._
import zio.http.endpoint._
import zio.schema._

case class User(id: Int, name: String)
object User {
  implicit val schema: Schema[User] = DeriveSchema.gen[User]
}

// one description: server, client and OpenAPI
val getUser =
  Endpoint(Method.GET / "users" / int("id"))
    .out[User]

val routes = Routes(
  getUser.implement(id => ZIO.succeed(User(id, "Ada")))
)`,
  },
  {
    label: 'Add Middleware',
    fileName: 'Middleware.scala',
    code: `import zio._
import zio.http._

val routes =
  Routes(
    Method.GET / "hello" -> handler(Response.text("Hello!"))
  )

// middleware composes with @@
val app =
  routes @@
    Middleware.requestLogging() @@
    Middleware.cors @@
    Middleware.timeout(5.seconds)

def run = Server.serve(app).provide(Server.default)`,
  },
  {
    label: 'HTTP Client',
    fileName: 'ClientExample.scala',
    code: `import zio._
import zio.http._

val program =
  for {
    client <- ZIO.service[Client]
    res    <- client.batched(Request.get("https://api.example.com/users"))
    body   <- res.body.asString
  } yield body

def run = program.provide(Client.default)`,
  },
  {
    label: 'WebSocket',
    fileName: 'WebSocketEcho.scala',
    code: `import zio._
import zio.http._
import zio.http.ChannelEvent.Read

val echo = Handler.webSocket { channel =>
  channel.receiveAll {
    case Read(WebSocketFrame.Text(text)) =>
      channel.send(Read(WebSocketFrame.text(s"echo: $text")))
    case _ => ZIO.unit
  }
}

val routes = Routes(
  Method.GET / "ws" -> handler(echo.toResponse)
)`,
  },
];

export default function HomepageCodeSnippet() {
  const [activeTab, setActiveTab] = useState(0);
  const { fileName, code } = TABS[activeTab];

  return (
    <section className="hp-band">
      <div className={clsx('hp-inner', styles.inner)}>
        <div className={styles.copy}>
          <span className="hp-eyebrow">In action</span>
          <h2 className="hp-h2">ZIO HTTP in Action</h2>
          <p className={clsx('hp-lead', styles.lead)}>
            Explore idiomatic Scala patterns for building high-performance,
            type-safe HTTP servers and clients. From routing and endpoints to
            middleware and real-time communication.
          </p>
          <div className={styles.tabs} role="tablist" aria-orientation="vertical">
            {TABS.map((tab, idx) => (
              <button
                key={tab.label}
                id={`tab-${idx}`}
                className={clsx(styles.tab, activeTab === idx && styles.tabActive)}
                onClick={() => setActiveTab(idx)}
                aria-selected={activeTab === idx}
                aria-controls="tabpanel"
                type="button"
                role="tab">
                <span className={styles.tabNumber}>
                  {String(idx + 1).padStart(2, '0')}
                </span>
                <span>{tab.label}</span>
              </button>
            ))}
          </div>
          <Link className={clsx('hp-link', styles.docsLink)} to="/">
            Explore the Docs &rarr;
          </Link>
        </div>

        <div className={clsx('hp-code', styles.panel)}>
          <div className={styles.panelHeader}>
            <span className={styles.fileName}>{fileName}</span>
            <span className={styles.language}>SCALA</span>
          </div>
          {/* Docusaurus CodeBlock highlights Scala and adds the copy button. */}
          <div
            id="tabpanel"
            role="tabpanel"
            aria-labelledby={`tab-${activeTab}`}>
            <CodeBlock key={activeTab} language="scala">
              {code}
            </CodeBlock>
          </div>
        </div>
      </div>
    </section>
  );
}
