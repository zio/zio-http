//> using scala "2.13.18"
//> using dep "dev.zio::zio-http-client-java:4.0.0-SNAPSHOT"
//> using repo "https://central.sonatype.com/repository/maven-snapshots/"

package example

import java.nio.charset.StandardCharsets

import zio.http._

object SimpleClient {

  def main(args: Array[String]): Unit = {
    val url = URL.parse("https://jsonplaceholder.typicode.com/todos").fold(
      err => throw new IllegalArgumentException("Invalid URL: " + err),
      identity,
    )
    val client   = LoomH2ClientDriver.default
    val response = client.send(Request.get(url))
    println(new String(response.body.toArray, StandardCharsets.UTF_8))
  }

}
