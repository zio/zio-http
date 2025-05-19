import React from 'react';
import CodeBlock from '@theme/CodeBlock';
import styles from './styles.module.css';

export default function HomepageShowcases() {
  return (
    <section className={styles.showcases}>
      <div className="container">
        <div className="row">
          <div className="col col--6">
            <h2>Server-side Example</h2>
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
            <h2>Client-side Example</h2>
            <CodeBlock language="scala">
{
`object ClientServerExample extends ZIOAppDefault {
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
}`
}
            </CodeBlock>
          </div>
        </div>
      </div>
    </section>
  );
}