//> using scala "2.13.18"
//> using dep "dev.zio::zio-http-client-java:4.0.0-SNAPSHOT"
//> using repo "https://central.sonatype.com/repository/maven-snapshots/"

package example

import java.nio.charset.StandardCharsets
import java.time.Duration

import zio.http._

object ClientWithConnectionPooling {

  // v3 dynamicConnectionPool(minimum = 10, maximum = 20, ttl = 5s) maps onto
  // the v4 pool surfaces: per-host cap 10, global cap 20, idle reclaim 5s.
  val pool: PooledLoomH2Client =
    PooledLoomH2Client(
      ClientConfig(
        pool = PoolConfig(maxPerHost = 10, maxTotal = 20, idleTimeout = Duration.ofSeconds(5)),
      ),
    )

  def main(args: Array[String]): Unit =
    try {
      val threads = (1 to 100).map { i =>
        Thread.ofVirtual().start(() => {
          val url = URL.parse("http://jsonplaceholder.typicode.com/posts/" + i).fold(
            err => throw new IllegalArgumentException("Invalid URL: " + err),
            identity,
          )
          val response = pool.send(Request.get(url))
          println(new String(response.body.toArray, StandardCharsets.UTF_8))
        })
      }
      threads.foreach(_.join())
    } finally pool.close()

}
