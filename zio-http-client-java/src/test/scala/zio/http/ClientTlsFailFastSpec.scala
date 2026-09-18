package zio.http

import java.nio.file.Files
import java.security.KeyStore

import zio.test._

/**
 * MINOR-4: TLS material fails fast at client construction with actionable
 * errors.
 *
 * Pins the timing shift (construction vs first-request): a bad trust-store
 * path, an unparseable private key (including PKCS#1 / EC-traditional, which
 * the JDK cannot parse), or a wrong store password must throw
 * [[IllegalArgumentException]] from the client factory itself - naming the
 * file/kind expected - instead of surfacing late on the first request. A
 * correct PEM trust+key config still builds every driver.
 */
object ClientTlsFailFastSpec extends ZIOSpecDefault {

  /** Test cert/key pair (PKCS#8 RSA, CN=localhost, expires 2027-06-30). */
  private val TestCert =
    """-----BEGIN CERTIFICATE-----
MIIDXTCCAkWgAwIBAgIIFmlxlymbftowDQYJKoZIhvcNAQEMBQAwXTELMAkGA1UE
BhMCVVMxDTALBgNVBAgTBFRlc3QxDTALBgNVBAcTBFRlc3QxDTALBgNVBAoTBFRl
c3QxDTALBgNVBAsTBFRlc3QxEjAQBgNVBAMTCWxvY2FsaG9zdDAeFw0yNjA2MzAy
MjA1NTlaFw0yNzA2MzAyMjA1NTlaMF0xCzAJBgNVBAYTAlVTMQ0wCwYDVQQIEwRU
ZXN0MQ0wCwYDVQQHEwRUZXN0MQ0wCwYDVQQKEwRUZXN0MQ0wCwYDVQQLEwRUZXN0
MRIwEAYDVQQDEwlsb2NhbGhvc3QwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEK
AoIBAQDihzLu4ln5ta1Rgac4J3GsWMLWVjMoud5NiZczB7RLHQx+yt1uYhDqc8HT
gzkEBU8lel1IdEMP+m4Y/tVZLrjMaH6lvjbLSQLjdgIsvtqeHmTfMBcCTr9E+r4k
Lhtc1utAOpL18DPBxXEQ7ib2MAtxjLXJQIU/Zh4GJNfbJ69IjFF/PTZUZsIWmJxB
zR9M+2NN1y0gtH6FpdQepQeFaeCJ43652NIKGAuM/w4G2DYSBUsHb/WsMc5QZm0M
DQ6Gy9E76jyghywdkUPw7dnioqzUhbCIZ8eXiL4YbJ6n9eeWvVrGyAWGBYstYEJq
OrR2KbGcd57R3ZvAkPcHIdIy+WO9AgMBAAGjITAfMB0GA1UdDgQWBBT8SbyNOu1I
6jQLbe4/D888GtPHeTANBgkqhkiG9w0BAQwFAAOCAQEAoa31bUJ541BZn321u3K8
XYfdFlTy3zLF4E7OlC9ygepgMKFmntQOnfg19rKgZO+VkQ8kBusgo/jiavjrQIDw
2tTwKel+kN1STaLt5xEWMQsGbGvT7iSejin3wFSxMIrbWKtSOc3Li0AmbdERJ37L
QAYSxLK+vU4BTT/whdI223xeFLQGYFhMyTag09Osw1WLUUZRvLh1FPeV5P5dWpzc
dpBrxWvWGRl2+Nle0zAQNznhfn8ydP/7K3Lv/f3qQ48EgXmSDdhvSUfX24CLVLXk
mETcQb+xmaWObOxkaK0iWGoQWPs2UFKVHPDmUlsYSt0ePGsiu1uEbgWrfr+6vBdx
Wg==
-----END CERTIFICATE-----"""

  private val TestKey =
    """-----BEGIN PRIVATE KEY-----
MIIEvAIBADANBgkqhkiG9w0BAQEFAASCBKYwggSiAgEAAoIBAQDihzLu4ln5ta1R
gac4J3GsWMLWVjMoud5NiZczB7RLHQx+yt1uYhDqc8HTgzkEBU8lel1IdEMP+m4Y
/tVZLrjMaH6lvjbLSQLjdgIsvtqeHmTfMBcCTr9E+r4kLhtc1utAOpL18DPBxXEQ
7ib2MAtxjLXJQIU/Zh4GJNfbJ69IjFF/PTZUZsIWmJxBzR9M+2NN1y0gtH6FpdQe
pQeFaeCJ43652NIKGAuM/w4G2DYSBUsHb/WsMc5QZm0MDQ6Gy9E76jyghywdkUPw
7dnioqzUhbCIZ8eXiL4YbJ6n9eeWvVrGyAWGBYstYEJqOrR2KbGcd57R3ZvAkPcH
IdIy+WO9AgMBAAECggEABLbzpG0pmjzhwpSEOnL3trKSO4vHvM1BhzOZ5gH/CqEs
JWdrfGSmHXsTSaethBvoLcuCLYPd8XMw32xOXHDQf9Cc8i4nTcvTN5C5Mt02B5xy
VQLXN8ET0ge19WLQRvpiIxAVBvFc4meNluCeBvmxA0f+cJXbMBqb/Vy+8Vy+FTBs
a3lthG4BVP7/q2SAUbxFQnajSGYHIW7bMKQUThKEPztffiv3pvws2nmSj3A/Ge99
eBRySh9fE2N46QcfAZ7TRMrU+nR6UpH7aBTL0h3T8qTVn/1HdYQyJBtIqhEVFHYZ
q93JkaZJP8Plhvq5gcnrjG1kLBF+w5Uh5d1l5/RqsQKBgQDzgQmCqYIDxWNE1R/d
zL43DOWc0/xA04vVR9NoFr22Yydzv17u34a21LG/TfH3byDSUqd9luKaNy0pykSD
79Vsycg2Bejk5LiZX/5rrX/OGs17Zi2KrQE6DvXdTQOkSaveU7zVKZKyck7mgreW
wXivdVKfqaTrizx68/y94WsY3wKBgQDuJyVAdO4sHJhQOBfp/wCNVfu/BqfQMJpA
/37dVt7HeiMh9hKx0IMY6iVTCXOoFIR9SpH4uiiw8/WkAfcUld4zuE68ymRTaZY7
EgX4i+ltRrXnp1Ac3FSHu972Z2GIFCqcXJ+Qj4aShw1c3bSSuU2pIDYmrAmcfKtK
tu/SAApq4wKBgCBnLm3Nwrhfvur89WWdhj5rH+7zoqC5xeTWzwIN7KbloO1dLPPa
mOGhghmz9Jv5lMOILjOfLX5aE095VA6+jocQfuz5cllrOklmpcOMbfJuTKO8IBlR
FlW0gfE1+2MUTqOiPwGaq6PFZEx2XpnYGwg2M419lK2ndJ/j8eEOqyK/AoGAGDG9
5Rh8Ads91hh8xXb0lWdA1h1U+x+U7DmIp+/lXhqYayDWsV3fk65l8FOrfk3nT9s9
jSlMbP273NeeRGcdVd/JkAB3xMmbS5D/Lkr4gfOHE2u6BdSUed2qPxotnGeAFLaM
N2F9aHFz+BVF/Qn6S85L8g3URCOeO07uekUqycUCgYBsnu7/9m00ZqP9GUyvVzRr
Br3/KmT2lozjcl/DpalWSKCZIW0lYgKYaiWEc165D4vj/ZBhHO7OPGeT2jqHk4/W
YdZ72W2776kWb4YTEdmJPNwgZIVFzcSZXzn8TKnCnwbHEilz51GFrMH1ZJNvLK3Q
tylLU8iZnM9E7+/GSVghdQ==
-----END PRIVATE KEY-----"""

  /** PKCS#1 RSA key: valid key material the JDK cannot parse directly. */
  private val Pkcs1Key =
    """-----BEGIN RSA PRIVATE KEY-----
MIIEpAIBAAKCAQEApvOiX3gSbluR7vG7EZPXu8n8T3V+mGmNNNN3Wpu2kSttiMYD
tYrvvCzfMkhHPJCblQH/CW0ly0Rv6HGGFVpdB6zx2YCJVXINybabrqPin87asv+e
ujAA0BDAm2sUYmAuwBpmN0fAE+xNjCpHBFqP8Q6oLpkGfY4EX653eFuCrlffWPAx
lCsPLfP62lDJW0HmLyQrzhbNnlqldfsuJnPJDiBdMHuVQWw2+Unp0UArwvppTNEk
/gKIeDkM9CV1QvZ1tbdXANtJRqdpnkk1orK302tuTYNh4Xh8vNf4fjAWUycfEGit
NH7XuOV/zIb1rkA3FN+AuuzOgVqSGshUhbfEpQIDAQABAoIBADmkvVeBObvo2gZK
aHC1PzOTlg5JXyB4sUygBwG96ddNy1ACLp2sDwJF6/qMgiwjMdTND6XNjdMVGh6D
s+wDe0N/LzIN810RODmBV1eBNmo/HbvYDpHyqRUt85K18h/VXc7uToSfvW4jlNLk
dgjGzBRLHz6Xj2oictgmgRGJZyGSnrkZt9e/mQgXlHcAojkDgZZO7tkINdvzl8K3
YHg8TrAM4PeZvz/Y/E324ydeiIJd67DvmDXDcFYrw+/Z3tSgj7hfNu8ZRDpxcGxv
ZrakFQO745ZTALG1mFqDpFKM4yoWegNU5yqNIle+Dw8BCPU8L6k0FPaNoHSXDwrt
JDooSzECgYEA0+VinVqvt4i3kes9ua3ScQw38ixcOBd6Y3R6PdAs3ZvyQKzvad2R
FdOM6gPqbWqPanzW1WDdrKTHyMVnZg940mKOLtRYyvakFOy9hlQYMqqi9GScoaCb
9o1ReXDtO3RMsnHTREDdAYy/QdklXFJMiUIjBWYHzVUOKownFZyqI0cCgYEAybNy
PDCHJQ4sHL4kj4Ad7SDs+zoy+K0CJ/V519vylDz3eoupmO5f8oC1ETMANymZIX1a
R/NbL0FoX238kdH52vnuvgcgwMshHt2f7D2aEk69n32TXA2+f2V3wbscr8z1nuA2
tgZdIaVJ9fCqO0vftkUYXhFkXBAFOYL/Ygj/FrMCgYA+mgxydLJpRMkHITrROptr
rrJwp85u+/C6pVTgIjq/Fi2SEgWBf2Y3zpJZKOL/hHXufgdybvXO5bfohvmvW27U
qS1chHvfKtL7I54yq3GitmsCTR9BWRP62XFysXxFDm0CY0KJbahdptlyeNbi2aWm
/5UdKTGw5ioTQ+jgJ7LUTQKBgQCJvBlpErZmznu/EPUEbTeCY3aQxBhkijgrs0yF
5DONmOhibZbd0QICJnP/D7W49ZYVMXWCJqOA7Ihqij/sD9gv+XZXm6R5Iv02B5+a
giKBF/YTQHxtYxFQC6kwySZ4wlyEJpYVzNiyDh4obTTCEzjsTdiq+/Ntjp8Su+rL
NluDUwKBgQCIddKNxXtUvTAJOgtxF2Tu3GWxix7Bp/YaOe/KmjwExoEmx1AkV0I3
MOQdjsIonwPEzMwlqIUALQ7j7dNA4xXmFWP7b+qKD6Km6U1f6pZdBRCg+4k3KPuT
ybpJYlczdmklAFa9GEu3rDrorx8xqhi5k+UlR1ePoRlcvCbQTFq9iA==
-----END RSA PRIVATE KEY-----"""

  /**
   * SEC1/EC-traditional key: valid key material the JDK cannot parse directly.
   */
  private val EcTraditionalKey =
    """-----BEGIN EC PRIVATE KEY-----
MHcCAQEEIKhurQf9oamEy+s746Rq9SX0NCe3lePXTNezJ19PsNTroAoGCCqGSM49
AwEHoUQDQgAEwwSVUqF4Pz8xWlwKCZ9HwSXwbze7Uyzx0PYk/PD2RPYr09B8Fn8e
i9OaRLAAeYbotSZ+m/vNZZKoN8VH4n1nWA==
-----END EC PRIVATE KEY-----"""

  /**
   * Fails with the [[IllegalArgumentException]] message, or `None` when
   * construction succeeds.
   */
  private def constructionError(thunk: => Any): Option[String] =
    try {
      thunk
      None
    } catch {
      case e: IllegalArgumentException => Some(Option(e.getMessage).getOrElse(""))
    }

  private def tlsWithTrustStore(path: String): ClientConfig =
    ClientConfig(tls = Some(ClientTlsConfig(trust = ClientTrustSource.TrustStore(path, Some("s3cret")))))

  private def tlsWithPemKey(key: String): ClientConfig =
    ClientConfig(
      tls = Some(ClientTlsConfig(key = Some(ClientKeySource.PemKey(TestCert, key)))),
    )

  /**
   * Builds an empty password-protected JKS at a temp path for the
   * wrong-password proof.
   */
  private def withPasswordStore(password: String)(use: String => TestResult): TestResult = {
    val tmp = Files.createTempFile("tls-failfast", ".jks")
    try {
      val ks  = KeyStore.getInstance("JKS")
      ks.load(null, password.toCharArray)
      val out = Files.newOutputStream(tmp)
      try ks.store(out, password.toCharArray)
      finally out.close()
      use(tmp.toString)
    } finally Files.deleteIfExists(tmp)
  }

  def spec = suite("ClientTlsFailFastSpec")(
    test("bad trust-store path fails at PooledLoomH2Client construction naming the path") {
      val error = constructionError(PooledLoomH2Client(tlsWithTrustStore("/no/such/trust.p12")))
      proof("bad-path pooled constructionError=" + error)
      assertTrue(
        error.exists(_.contains("/no/such/trust.p12")),
        error.exists(_.contains("ClientTrustSource.TrustStore")),
      )
    },
    test("bad trust-store path fails at LoomH2ClientDriver construction naming the path") {
      val error = constructionError(LoomH2ClientDriver(tlsWithTrustStore("/no/such/trust.p12")))
      proof("bad-path driver constructionError=" + error)
      assertTrue(
        error.exists(_.contains("/no/such/trust.p12")),
        error.exists(_.contains("ClientTrustSource.TrustStore")),
      )
    },
    test("bad trust-store path fails at JavaH2Client construction naming the path") {
      val error = constructionError(JavaH2Client(tlsWithTrustStore("/no/such/trust.p12")))
      proof("bad-path jdk constructionError=" + error)
      assertTrue(
        error.exists(_.contains("/no/such/trust.p12")),
        error.exists(_.contains("ClientTrustSource.TrustStore")),
      )
    },
    test("PKCS#1 RSA key fails at construction naming PKCS#1 with a conversion hint") {
      val error = constructionError(PooledLoomH2Client(tlsWithPemKey(Pkcs1Key)))
      proof("pkcs1 constructionError=" + error)
      assertTrue(
        error.exists(_.contains("PKCS#1")),
        error.exists(_.contains("openssl pkcs8 -topk8")),
      )
    },
    test("EC-traditional key fails at construction naming the format with a conversion hint") {
      val error = constructionError(PooledLoomH2Client(tlsWithPemKey(EcTraditionalKey)))
      proof("ec-trad constructionError=" + error)
      assertTrue(
        error.exists(_.contains("EC PRIVATE KEY")),
        error.exists(_.contains("openssl pkcs8 -topk8")),
      )
    },
    test("garbage private key fails at construction naming the key source") {
      val error = constructionError(PooledLoomH2Client(tlsWithPemKey("not-a-key")))
      proof("garbage-key constructionError=" + error)
      assertTrue(error.exists(_.contains("ClientKeySource.PemKey")))
    },
    test("wrong keystore password fails at construction naming the file") {
      withPasswordStore("correct") { path =>
        val config = ClientConfig(
          tls = Some(ClientTlsConfig(trust = ClientTrustSource.TrustStore(path, Some("wrong"), "JKS"))),
        )
        val error  = constructionError(PooledLoomH2Client(config))
        proof("wrong-password constructionError=" + error)
        assertTrue(
          error.exists(_.contains(path)),
          error.exists(_.contains("ClientTrustSource.TrustStore")),
        )
      }
    },
    test("correct PEM trust+key config still builds every driver") {
      val config = ClientConfig(
        tls = Some(
          ClientTlsConfig(
            trust = ClientTrustSource.PemBundle(TestCert),
            key = Some(ClientKeySource.PemKey(TestCert, TestKey)),
          ),
        ),
      )
      val pooled = PooledLoomH2Client(config)
      val driver = LoomH2ClientDriver(config)
      val jdk    = JavaH2Client(config)
      proof("correct-pem builds pooled=" + (pooled != null) + " driver=" + (driver != null) + " jdk=" + (jdk != null))
      pooled.close()
      assertTrue(pooled != null, driver != null, jdk != null)
    },
  )

  private def proof(line: String): Unit =
    println(s">> [ClientTlsFailFastSpec] $line")
}
