//> using scala "2.13.18"
//> using dep "dev.zio::zio-http-client-java:4.0.0-SNAPSHOT"
//> using repo "https://central.sonatype.com/repository/maven-snapshots/"

package example

import java.nio.charset.StandardCharsets

import zio.http._

object HttpsClient {

  // NOTE: v3 loaded the truststore as a classpath *resource*
  // (ClientSSLConfig.FromTrustStoreResource); v4 ClientTrustSource.TrustStore
  // resolves a filesystem path (Paths.get), so this now points at a file.
  val tls = ClientTlsConfig(
    trust = ClientTrustSource.TrustStore("truststore.jks", Some("changeit"), "JKS"),
  )

  def main(args: Array[String]): Unit = {
    val url = URL.parse("https://jsonplaceholder.typicode.com/todos/1").fold(
      err => throw new IllegalArgumentException("Invalid URL: " + err),
      identity,
    )
    val client   = LoomH2ClientDriver(ClientConfig(tls = Some(tls)))
    val request  = Request.get(url).addHeader("Host", "jsonplaceholder.typicode.com")
    val response = client.send(request)
    println(new String(response.body.toArray, StandardCharsets.UTF_8))
  }

}
