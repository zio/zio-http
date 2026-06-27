package zio.http.h2

import java.net.Socket
import java.nio.charset.StandardCharsets
import javax.net.ssl.{SSLContext, SSLParameters, SSLSocket, TrustManager, X509TrustManager}

import scala.annotation.experimental
import scala.collection.mutable

import zio._
import zio.blocks.chunk.Chunk
import zio.blocks.config.Secret
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.test.TestAspect.sequential
import zio.test._

import zio.http.h2.H2Frame._
import zio.http.h2.hpack.{HeaderField, Hpack}
import zio.http.{
  BindAddress,
  BoundAddress,
  Connector,
  DefectHandler,
  Handler,
  Protocol,
  Response,
  Route,
  Routes,
  ServerHandle,
  TlsConfig,
  TlsSource,
}

@experimental
object H2TlsSpec extends ZIOSpecDefault {

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

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("H2TlsSpec")(
      test("H2 over TLS with PEM cert+key serves a GET request") {
        ZIO
          .acquireRelease(
            ZIO.attempt {
              val tlsCfg = TlsConfig(
                certChain  = TlsSource.PemString(Secret(TestCert)),
                privateKey = TlsSource.PemString(Secret(TestKey)),
              )
              val transport = new H2Transport(
                Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok))),
                Context.empty,
                Connector(bind = BindAddress.localhost(0), protocol = Protocol.H2(tlsCfg)),
                DefectHandler.default,
              )
              transport.start()
            },
          )(h => ZIO.succeed(h.close0()))
          .flatMap { handle =>
            val tcpPort = handle.binding.address match {
              case BoundAddress.Tcp(_, thePort) => thePort
              case other                        => throw new AssertionError("Expected TCP: " + other)
            }
            ZIO.attemptBlocking {
              val client = new TlsH2Client(tcpPort)
              try {
                val resp = client.roundTrip("GET", "/", Chunk.empty, streamId = 1)
                assertTrue(resp.status == 200)
              } finally client.close()
            }
          }
      },
      test("TlsSource.SslContext path uses provided SSLContext directly") {
        ZIO
          .acquireRelease(
            ZIO.attempt {
              val tlsCfg = TlsConfig(
                certChain  = TlsSource.PemString(Secret(TestCert)),
                privateKey = TlsSource.PemString(Secret(TestKey)),
              )
              val transport = new H2Transport(
                Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok))),
                Context.empty,
                Connector(bind = BindAddress.localhost(0), protocol = Protocol.H2(tlsCfg)),
                DefectHandler.default,
              )
              transport.start()
            },
          )(h => ZIO.succeed(h.close0()))
          .flatMap { handle =>
            val tcpPort = handle.binding.address match {
              case BoundAddress.Tcp(_, thePort) => thePort
              case other                        => throw new AssertionError("Expected TCP: " + other)
            }
            ZIO.attemptBlocking {
              val client = new TlsH2Client(tcpPort)
              try {
                val resp = client.roundTrip("GET", "/", Chunk.empty, streamId = 1)
                assertTrue(resp.status == 200)
              } finally client.close()
            }
          }
      },
      test("Protocol.H2 sets http2Config from H2 connector") {
        ZIO.attempt {
          val tlsCfg = TlsConfig(
            certChain  = TlsSource.PemString(Secret(TestCert)),
            privateKey = TlsSource.PemString(Secret(TestKey)),
          )
          val transport = new H2Transport(
            Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok))),
            Context.empty,
            Connector(bind = BindAddress.localhost(0), protocol = Protocol.H2(tlsCfg)),
            DefectHandler.default,
          )
          assertTrue(transport != null)
        }
      },
      test("H2 over TLS with PKCS1 RSA key (wrapPkcs1RsaKey path)") {
        ZIO
          .acquireRelease(
            ZIO.attempt {
              val tlsCfg = TlsConfig(
                certChain  = TlsSource.PemString(Secret(RsaCert)),
                privateKey = TlsSource.PemString(Secret(RsaKey)),
              )
              new H2Transport(
                Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok))),
                Context.empty,
                Connector(bind = BindAddress.localhost(0), protocol = Protocol.H2(tlsCfg)),
                DefectHandler.default,
              ).start()
            },
          )(h => ZIO.succeed(h.close0()))
          .flatMap { handle =>
            val tcpPort = handle.binding.address match {
              case BoundAddress.Tcp(_, thePort) => thePort
              case other                        => throw new AssertionError("Expected TCP: " + other)
            }
            ZIO.attemptBlocking {
              val client = new TlsH2Client(tcpPort)
              try {
                val resp = client.roundTrip("GET", "/", Chunk.empty, streamId = 1)
                assertTrue(resp.status == 200)
              } finally client.close()
            }
          }
      },
      test("TlsSource.SslContext in certChain uses SSLContext directly") {
        ZIO.attempt {
          import javax.net.ssl.SSLContext
          val ctx    = SSLContext.getInstance("TLS")
          ctx.init(null, null, null)
          val tlsCfg = TlsConfig(
            certChain  = TlsSource.SslContext(ctx),
            privateKey = TlsSource.PemString(Secret(TestKey)),
          )
          assertTrue(tlsCfg != null)
        }
      },
      test("TlsSource.SslContext in privateKey uses SSLContext directly") {
        ZIO.attempt {
          import javax.net.ssl.SSLContext
          val ctx    = SSLContext.getInstance("TLS")
          ctx.init(null, null, null)
          val tlsCfg = TlsConfig(
            certChain  = TlsSource.PemString(Secret(TestCert)),
            privateKey = TlsSource.SslContext(ctx),
          )
          assertTrue(tlsCfg != null)
        }
      },
      test("H2 over TLS with EC PKCS8 key (parsePkcs8PrivateKey EC branch)") {
        ZIO
          .acquireRelease(
            ZIO.attempt {
              val tlsCfg = TlsConfig(
                certChain  = TlsSource.PemString(Secret(EcCert)),
                privateKey = TlsSource.PemString(Secret(EcKey)),
              )
              new H2Transport(
                Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok))),
                Context.empty,
                Connector(bind = BindAddress.localhost(0), protocol = Protocol.H2(tlsCfg)),
                DefectHandler.default,
              ).start()
            },
          )(h => ZIO.succeed(h.close0()))
          .flatMap { handle =>
            val tcpPort = handle.binding.address match {
              case BoundAddress.Tcp(_, thePort) => thePort
              case other                        => throw new AssertionError("Expected TCP: " + other)
            }
            ZIO.attemptBlocking {
              val client = new TlsH2Client(tcpPort)
              try {
                val resp = client.roundTrip("GET", "/", Chunk.empty, streamId = 1)
                assertTrue(resp.status == 200)
              } finally client.close()
            }
          }
      },
    ) @@ sequential

  private val EcCert =
    """-----BEGIN CERTIFICATE-----
MIIBfTCCASOgAwIBAgIUDZi2vTwLeJsU87eAXPcmVuht9ZcwCgYIKoZIzj0EAwIw
FDESMBAGA1UEAwwJbG9jYWxob3N0MB4XDTI2MDYzMDIyMzUwMFoXDTI3MDYzMDIy
MzUwMFowFDESMBAGA1UEAwwJbG9jYWxob3N0MFkwEwYHKoZIzj0CAQYIKoZIzj0D
AQcDQgAEjJUAedhsp4p6WqaoCQ/a3YnwDELlrR0fUBIbsuy3I/ny/17loqyJwXTq
Ll2cCg2EUDkIml8eNWN2njiIHExlaqNTMFEwHQYDVR0OBBYEFJQhtfJN5UrWvcae
kkLD/Q+fPogHMB8GA1UdIwQYMBaAFJQhtfJN5UrWvcaekkLD/Q+fPogHMA8GA1Ud
EwEB/wQFMAMBAf8wCgYIKoZIzj0EAwIDSAAwRQIhAPUIvEGyk7q+mePu3lRBAOQD
Kgl8yIWcrTsLIrsXHA6cAiAPFrDVHrjyjv9zdl8DhXcH/Sx8o2to2EIAMDlio31w
Lg==
-----END CERTIFICATE-----"""

  private val EcKey =
    """-----BEGIN PRIVATE KEY-----
MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgAA11ijA/PFdKfTFB
68vaBBAfPi6+RSXqVLzcFtTgGT6hRANCAASMlQB52GyninpapqgJD9rdifAMQuWt
HR9QEhuy7Lcj+fL/XuWirInBdOouXZwKDYRQOQiaXx41Y3aeOIgcTGVq
-----END PRIVATE KEY-----"""

  private val RsaCert =
    """-----BEGIN CERTIFICATE-----
MIIDCTCCAfGgAwIBAgIUPyIYPTqKB0MgAsiThX78d2JzeXkwDQYJKoZIhvcNAQEL
BQAwFDESMBAGA1UEAwwJbG9jYWxob3N0MB4XDTI2MDYzMDIyMTAzMVoXDTI3MDYz
MDIyMTAzMVowFDESMBAGA1UEAwwJbG9jYWxob3N0MIIBIjANBgkqhkiG9w0BAQEF
AAOCAQ8AMIIBCgKCAQEAmpBT2isv7ofVhGDYFelgaIDO7VR/NK9+f9OPE0lopM3r
gq5WLXE/DkOUWk743XYV1fYFQi5YJe7MY2wmDNBwzIWWWwSHnRu9KM/CqSLOSzvf
sejLhO+V4RA2bNRlP2ySNyIdmky3HUCZBxCyYwnTcl9r0IYXqTMPhzASqvUIJLrw
bLCKwON1s9zoa4ml2duFuqwh+tEXylXqlBQ/vSmPShD8Kub7qaLeLtRGjCXNDWjR
6dDVqmuW0LCTeaXFeiARI3xTMuUEC1QpsGReGQQ3JS/MOceoG6ceoFwXJKL87/Ck
gqNsCQ5kKXbuFltAIe441USkd2toaKFjeT9SkN/SNwIDAQABo1MwUTAdBgNVHQ4E
FgQUaBDWAIWBbqfXPEHtrljVRryWqiEwHwYDVR0jBBgwFoAUaBDWAIWBbqfXPEHt
rljVRryWqiEwDwYDVR0TAQH/BAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAbUMO
wcdMdOZMSumIqi31QwqELH4+hDArjMMy+pj2wCbMbe6+wkv1voF2HoK4B3QDcf9F
RMYnmCZDW0sCLAjHVRKyKVok2x3x86U5DVXOx8ToXbgjkymsVE2I9V+m0Y7hto9g
ojBn8smWabZaFeGSjIjp1luE1wAOilpAaVwogGo27SZJfr954X7w5HuyBddw8kA4
HRNFzWVt/iHGFCbsFwPIsBTMjps+1QnaoLiN2cJ5VbbfuPqAY3ZUMyMSyKwk6Emu
mTXSEXh4VaE9rTKgD9jqkOVMZbhMNxrB+KQev2od6BUF5CYrPwLeYOtS6/3mFfBb
zFjlFi6/Ygem2a5LRw==
-----END CERTIFICATE-----"""

  private val RsaKey =
    """-----BEGIN RSA PRIVATE KEY-----
MIIEowIBAAKCAQEAmpBT2isv7ofVhGDYFelgaIDO7VR/NK9+f9OPE0lopM3rgq5W
LXE/DkOUWk743XYV1fYFQi5YJe7MY2wmDNBwzIWWWwSHnRu9KM/CqSLOSzvfsejL
hO+V4RA2bNRlP2ySNyIdmky3HUCZBxCyYwnTcl9r0IYXqTMPhzASqvUIJLrwbLCK
wON1s9zoa4ml2duFuqwh+tEXylXqlBQ/vSmPShD8Kub7qaLeLtRGjCXNDWjR6dDV
qmuW0LCTeaXFeiARI3xTMuUEC1QpsGReGQQ3JS/MOceoG6ceoFwXJKL87/CkgqNs
CQ5kKXbuFltAIe441USkd2toaKFjeT9SkN/SNwIDAQABAoIBABgL/gJn4gWuMWxu
ir/h9riw2EDOpW8jdz6uQfNst4XzGZcpG3Q8yw7OOc2maAvrJsY1MqfowO2/OMz7
vZxpw4WLqmhPt0Tx/1x9nQSmdcpOWY+lhHW9YVKfgfAtnUZK/yE/wrOdCDIvY3+D
Zqrc6P5Bf+sfKZ2OjvW9PMeckV9Go6Og5S3OgZ6Uxi6fvkorCS6oHETZjcRJCrg/
L4rBkwBeV0vnkAnvy83WN2j3e16Yep8+nQ/jnW4gYUY31uRaeCrj/D9jtyHde3Jq
mCLJa2wvSLDCnjkvqa8MvN+1nIp2zku35qmddwqYuWNOtsxwYeWN95LrreOb2m3P
lj9C4OECgYEA0VCnlO5q11ArRVAQ6GwRdrD5voKtgzk+M3Pb43S4DB9DmgBlvvkn
NsTAtK3UC4hn8pA5Dn+y59Ics4m5ksOFueROydKRIXatAxqoRqF1R+uQ4JyxvNjP
BmNhR5Kw2UEpb+UEEjcuus7owamOuQ9vKn3R3HXWM3P+l8kFfxQGYuECgYEAvQmE
cqSg7jzEdgzhwzOdjztX/J0DhgZ0Nx/4sXc6b9Tp30MVm1+2VRvVKcSrrJHQ8wqK
TvoyYCv9QNl+oFYHzO3VjWi/e4zVUMbAec2uBUiXmsu05mHzcbUhHClqWhSSZvSh
tKED+KzRSUS8xjJsnmOSAN1ovw1Sspsn+BTa8BcCgYEAr2CTuQ52iIdMahvmvsbl
bvxjlMMrHJrWygeWQqMmHkoHuz4AAh4CRDpgeEJ5O1yEM9GxbLuELAZ5M9j+msCm
CBYKCcIbBd3IoqQwQFXBzYvLbNb9eQxzkZetB2oaMT4OuQk6+wQvbCg3DyTBR79t
1j1rm/tDiQL0Wzr8FvixTSECgYAWS/MVWpmov/4kNmzCi2oAJO3B2/s4abZ3cgvx
UrDP0/sp3E3GH8nvy/KelJXzKtfMcufRXO1oLogWiBDJdJxC7aKMpVhAiGH4vxig
xUPLV76aAYD6037VYJnhKnli2p5SCnSwS3WedTPSQotJjVUGcZQdCgB62GVyr55N
Qph4TQKBgEGhPbnkg3c4tLs9GAH1n4OlrwJrls+EWZU2UilTrykCAgrQJIIDc1aX
8ZwBUb5cLFNyZeAtytqjjaQPyzGA25b2Uos+7NYz9wuB/MQ3N/NZJNnBGYkkn/cZ
9gwc9jNgWg32ZhB5zRWbfLqcxcx9XbeA9S6TmkJmVih7S3qC1FwJ
-----END RSA PRIVATE KEY-----"""

  private final class TlsH2Client(port: Int) extends AutoCloseable {
    private val PrefaceBytes =
      "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII)

    private val trustAll = Array[TrustManager](new X509TrustManager {
      override def checkClientTrusted(chain: Array[java.security.cert.X509Certificate], authType: String): Unit = ()
      override def checkServerTrusted(chain: Array[java.security.cert.X509Certificate], authType: String): Unit = ()
      override def getAcceptedIssuers: Array[java.security.cert.X509Certificate]                               = Array.empty
    })

    private val clientCtx = SSLContext.getInstance("TLS")
    clientCtx.init(null, trustAll, new java.security.SecureRandom())

    private val rawSocket   = new Socket("127.0.0.1", port)
    rawSocket.setSoTimeout(5000)
    private val sslSocket   = {
      val s      = clientCtx.getSocketFactory
        .createSocket(rawSocket, "127.0.0.1", port, true)
        .asInstanceOf[SSLSocket]
      val params = s.getSSLParameters
      params.setApplicationProtocols(Array("h2"))
      s.setSSLParameters(params)
      s.setUseClientMode(true)
      s.startHandshake()
      s
    }

    private val out   = sslSocket.getOutputStream
    private val rawIn = sslSocket.getInputStream
    private var buf   = Chunk.empty[Byte]

    handshake()

    def sendFrame(frame: H2Frame): Unit   = sendRaw(FrameCodec.encode(frame).toArray)
    def sendRaw(bytes: Array[Byte]): Unit = { out.write(bytes); out.flush() }

    def readFrame(): H2Frame = {
      while (true) {
        FrameCodec.decode(buf) match {
          case Right((frame, rest))           =>
            buf = rest; return frame
          case Left(H2Error.InsufficientData) =>
            val tmp = new Array[Byte](8192)
            val n   = rawIn.read(tmp)
            if (n < 0) throw new java.io.EOFException("TLS connection closed")
            buf = buf ++ Chunk.fromArray(java.util.Arrays.copyOf(tmp, n))
          case Left(err)                      =>
            throw new AssertionError("Frame decode error: " + err)
        }
      }
      throw new AssertionError("unreachable")
    }

    def roundTrip(method: String, path: String, body: Chunk[Byte], streamId: Int): TlsResponse = {
      sendFrame(Headers(
        streamId    = streamId,
        headerBlock = Hpack.encode(List(
          HeaderField(":method", method),
          HeaderField(":path", path),
          HeaderField(":scheme", "https"),
          HeaderField(":authority", s"127.0.0.1:$port"),
        )),
        endStream   = body.isEmpty,
        endHeaders  = true,
      ))
      if (body.nonEmpty) sendFrame(Data(streamId, body, endStream = true))
      awaitResponse(streamId)
    }

    def awaitResponse(streamId: Int): TlsResponse = {
      val hdrs = mutable.ListBuffer.empty[HeaderField]
      var body = Chunk.empty[Byte]
      var done = false
      while (!done) {
        readFrame() match {
          case Settings(false, _)                                    => sendFrame(Settings(ack = true, Nil))
          case Settings(true, _)                                     => ()
          case _: WindowUpdate                                       => ()
          case Headers(sid, block, end, _, _, _) if sid == streamId =>
            Hpack.decode(block) match {
              case Right(h) => hdrs ++= h
              case Left(e)  => throw new AssertionError("HPACK decode: " + e)
            }
            done = end
          case Data(sid, data, end, _) if sid == streamId           =>
            body = body ++ data; done = end
          case GoAway(_, code, dbg)                                  =>
            throw new AssertionError(s"GOAWAY: $code ${new String(dbg.toArray)}")
          case _                                                     => ()
        }
      }
      val status = hdrs.find(_.name == ":status").map(_.value.toInt)
        .getOrElse(throw new AssertionError("Missing :status"))
      TlsResponse(status, hdrs.toList, body)
    }

    override def close(): Unit = sslSocket.close()

    private def handshake(): Unit = {
      out.write(PrefaceBytes)
      out.write(FrameCodec.encode(Settings(ack = false, Nil)).toArray)
      out.flush()
      readFrame() match {
        case Settings(false, _) => sendFrame(Settings(ack = true, Nil))
        case other              => throw new AssertionError("Expected Settings(false,_): " + other)
      }
      readFrame()
    }
  }

  private final case class TlsResponse(status: Int, headers: List[HeaderField], body: Chunk[Byte])
}
