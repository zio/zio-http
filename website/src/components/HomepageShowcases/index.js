import React from "react";
import CodeBlock from "@theme/CodeBlock";
import styles from "./styles.module.css";

export default function HomepageShowcases() {
  return (
    <section className={styles.showcases}>
      <div className="container">
        <h2 className="text--center">Imperative API</h2>
        <div className="row" style={{ alignItems: 'center' }}>
          <div className="col col--6">
            <h3>Server-side Example</h3>
            <CodeBlock language="scala">
              {`import zio._
import zio.http._
object GreetingServer extends ZIOAppDefault {
  val routes =
    Routes(
      Method.GET / "greet" -> handler { (req: Request) =>
        val name = req.queryOrElse("name", "World")
        Response.text(s"Hello $name!")
      }
    )
  def run = Server.serve(routes).provide(Server.default)
}`}
            </CodeBlock>
          </div>
          <div className="col col--6">
            <h3>Client-side Example</h3>
            <CodeBlock language="scala">
              {`import zio._
import zio.http._
import zio.http.codec.TextBinaryCodec.fromSchema
object ClientServerExample extends ZIOAppDefault {
  val clientApp: ZIO[Client, Throwable, Unit] =
    for {
      url <- ZIO.fromEither(URL.decode("http://localhost:8080/greet"))
      res <- ZClient.batched(
        Request
          .get(url)
          .setQueryParams(
            Map("name" -> Chunk("ZIO HTTP"))
          )
      )
      body <- res.bodyAs[String]
      _ <- ZIO.debug("Received response: " +  body)
    }  yield ()
  val run = clientApp.provide(Client.default)
}`}
            </CodeBlock>
          </div>
        </div>
        <div className="col col--8 col--offset-2">
          <h2 className="text--center">Declarative API</h2>
          <CodeBlock language="scala">
            {`
// Endpoint Definition
val endpoint =
  Endpoint(GET / "greet" ?? Doc.p("Route for querying books"))
    .query(
      HttpCodec.query[String]("name") ?? Doc.p("Name of the person to greet"),
    )
    .out[String]
// Endpoint Implementation
val greetRoute: Route[Any, Nothing] =
  endpoint.implementHandler(handler((name: String) => s"Hello, $name!"))
`}
          </CodeBlock>
        </div>
      </div>
    </section>
  );
}