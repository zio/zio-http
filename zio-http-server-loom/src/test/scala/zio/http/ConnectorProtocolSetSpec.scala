package zio.http

import zio.blocks.config.Secret
import zio.blocks.schema.DynamicValue
import zio.test._

/**
 * Connector protocol sets and negotiation policy.
 *
 * Failing-first boundary fixtures for the Scala 3.9 validated
 * protocol-set/negotiation model: H1-only, H2-only (TLS `H2` and cleartext
 * `H2C`), shared H1+H2 sets, typed duplicate/empty/TCP-UDP/unknown-ALPN
 * failures, `Single`/`TlsAlpn`/`CleartextPreface` policy validation, explicit
 * Blocks Schema encoding, and migration from the prior unreleased single-
 * `protocol` shape. UDP is registered as a transport kind but production use
 * fails validation: no H3 is advertised or runnable.
 */
object ConnectorProtocolSetSpec extends ZIOSpecDefault {

  private def tlsCfg: TlsConfig =
    TlsConfig(
      certChain = TlsSource.PemString(Secret("CERT")),
      privateKey = TlsSource.PemString(Secret("KEY")),
    )

  private def leftMessage(result: Either[ConnectorFailure, Any]): String =
    result match {
      case Left(failure) => failure.message
      case Right(value)  => "expected Left but got Right(" + value + ")"
    }

  def spec = suite("ConnectorProtocolSetSpec")(
    suite("validated sets")(
      test("H1-only set validates") {
        assertTrue(ProtocolSet.h1Only.members == Set[AppProtocol](AppProtocol.Http1))
      },
      test("H2-only TLS set validates") {
        assertTrue(ProtocolSet.h2Only.members == Set[AppProtocol](AppProtocol.H2))
      },
      test("H2-only cleartext set validates") {
        assertTrue(
          ProtocolSet.h2cOnly.members == Set[AppProtocol](AppProtocol.H2C),
          ProtocolSet.h2cOnly.members != Set[AppProtocol](AppProtocol.Http1),
        )
      },
      test("shared H1+H2 TLS set validates") {
        assertTrue(ProtocolSet.h1h2.members == Set[AppProtocol](AppProtocol.Http1, AppProtocol.H2))
      },
      test("shared H1+H2C cleartext set validates") {
        assertTrue(ProtocolSet.h1h2c.members == Set[AppProtocol](AppProtocol.Http1, AppProtocol.H2C))
      },
      test("duplicate protocols fail typed") {
        val result = ProtocolSet.fromSeq(List(AppProtocol.Http1, AppProtocol.Http1))
        assertTrue(result == Left(ConnectorFailure.DuplicateProtocol(AppProtocol.Http1)))
      },
      test("empty set fails typed") {
        assertTrue(
          ProtocolSet.fromSet(Set.empty[AppProtocol]) == Left(ConnectorFailure.EmptyProtocolSet),
          ProtocolSet.fromSeq(Nil) == Left(ConnectorFailure.EmptyProtocolSet),
        )
      },
      test("legacy H2C maps to the H2C-only set") {
        assertTrue(ProtocolSet.fromLegacy(Protocol.H2C()) == Right(ProtocolSet.h2cOnly))
      },
      test("legacy H2 maps to the H2-only set") {
        assertTrue(ProtocolSet.fromLegacy(Protocol.H2(tlsCfg)) == Right(ProtocolSet.h2Only))
      },
      test("legacy H3 has no set mapping and fails typed") {
        assertTrue(ProtocolSet.fromLegacy(Protocol.H3(tlsCfg)) == Left(ConnectorFailure.H3NotAdvertised))
      },
    ),
    suite("transport validation")(
      test("TCP accepts every TCP-family set") {
        val sets =
          List(ProtocolSet.h1Only, ProtocolSet.h2cOnly, ProtocolSet.h2Only, ProtocolSet.h1h2, ProtocolSet.h1h2c)
        assertTrue(sets.forall(set => ConnectorValidation.validateTransport(TransportKind.Tcp, set).isRight))
      },
      test("UDP fails typed for every TCP-family set") {
        val sets =
          List(ProtocolSet.h1Only, ProtocolSet.h2cOnly, ProtocolSet.h2Only, ProtocolSet.h1h2, ProtocolSet.h1h2c)
        assertTrue(
          sets.forall { set =>
            ConnectorValidation.validateTransport(TransportKind.Udp, set) match {
              case Left(ConnectorFailure.TcpUdpMismatch(_)) => true
              case _                                        => false
            }
          },
        )
      },
      test("TransportKind.fromString resolves Tcp and Udp") {
        assertTrue(
          TransportKind.fromString("Tcp") == TransportKind.Tcp,
          TransportKind.fromString("Udp") == TransportKind.Udp,
        )
      },
      test("TransportKind.fromString rejects unknown names instead of defaulting") {
        var rejected = 0
        List("tcp", "UDP", "quic", "", "h3").foreach { name =>
          try TransportKind.fromString(name)
          catch {
            case _: IllegalArgumentException => rejected += 1
          }
        }
        assertTrue(rejected == 5)
      },
    ),
    suite("TLS presence validation")(
      test("H2 without TLS fails typed") {
        assertTrue(
          ConnectorValidation.validateTls(ProtocolSet.h2Only, tlsPresent = false).isLeft,
          ConnectorValidation.validateTls(ProtocolSet.h1h2, tlsPresent = false).isLeft,
        )
      },
      test("H2 with TLS passes") {
        assertTrue(ConnectorValidation.validateTls(ProtocolSet.h2Only, tlsPresent = true).isRight)
      },
      test("lone H2C over TLS fails typed") {
        assertTrue(ConnectorValidation.validateTls(ProtocolSet.h2cOnly, tlsPresent = true).isLeft)
      },
      test("H1 is TLS-agnostic") {
        assertTrue(
          ConnectorValidation.validateTls(ProtocolSet.h1Only, tlsPresent = false).isRight,
          ConnectorValidation.validateTls(ProtocolSet.h1Only, tlsPresent = true).isRight,
        )
      },
    ),
    suite("negotiation policy")(
      test("Single resolves a lone protocol") {
        assertTrue(
          Negotiation.single(ProtocolSet.h1Only) == Right(AppProtocol.Http1),
          Negotiation.single(ProtocolSet.h2cOnly) == Right(AppProtocol.H2C),
          Negotiation.single(ProtocolSet.h2Only) == Right(AppProtocol.H2),
        )
      },
      test("Single rejects a shared set with PolicyMismatch") {
        val isPolicyMismatch = Negotiation.single(ProtocolSet.h1h2) match {
          case Left(ConnectorFailure.PolicyMismatch(_)) => true
          case _                                        => false
        }
        assertTrue(isPolicyMismatch)
      },
      test("ALPN follows offer order across a shared set") {
        assertTrue(
          Negotiation.negotiateAlpn(ProtocolSet.h1h2, List("http/1.1", "h2")) == Right(AppProtocol.Http1),
          Negotiation.negotiateAlpn(ProtocolSet.h1h2, List("h2", "http/1.1")) == Right(AppProtocol.H2),
          Negotiation.negotiateAlpn(ProtocolSet.h2Only, List("h2")) == Right(AppProtocol.H2),
        )
      },
      test("unknown ALPN fails typed with the first offered id") {
        assertTrue(
          Negotiation.negotiateAlpn(ProtocolSet.h2Only, List("http/1.1")) ==
            Left(ConnectorFailure.UnknownAlpnProtocol("http/1.1")),
          Negotiation.negotiateAlpn(ProtocolSet.h1Only, List("h3")) ==
            Left(ConnectorFailure.UnknownAlpnProtocol("h3")),
        )
      },
      test("empty ALPN offer fails typed") {
        assertTrue(Negotiation.negotiateAlpn(ProtocolSet.h2Only, Nil) == Left(ConnectorFailure.NoAlpnOffered))
      },
      test("H2C is never selected through ALPN") {
        assertTrue(
          Negotiation.negotiateAlpn(ProtocolSet.h1h2c, List("http/1.1")) == Right(AppProtocol.Http1),
          Negotiation.negotiateAlpn(ProtocolSet.h2cOnly, List("h2")) ==
            Left(ConnectorFailure.UnknownAlpnProtocol("h2")),
        )
      },
      test("cleartext preface selects H2C on match and H1 otherwise") {
        assertTrue(
          Negotiation.selectForPreface(ProtocolSet.h1h2c, isH2Preface = true) == Right(AppProtocol.H2C),
          Negotiation.selectForPreface(ProtocolSet.h1h2c, isH2Preface = false) == Right(AppProtocol.Http1),
        )
      },
      test("cleartext preface without a matching member fails typed") {
        assertTrue(
          Negotiation.selectForPreface(ProtocolSet.h1Only, isH2Preface = true) ==
            Left(ConnectorFailure.NoProtocolForPreface(isH2Preface = true)),
          Negotiation.selectForPreface(ProtocolSet.h2Only, isH2Preface = false) ==
            Left(ConnectorFailure.NoProtocolForPreface(isH2Preface = false)),
        )
      },
      test("TlsAlpn policy requires TLS and an ALPN-capable member") {
        assertTrue(
          ConnectorValidation.validatePolicy(NegotiationPolicy.TlsAlpn, ProtocolSet.h2Only, tlsPresent = true).isRight,
          ConnectorValidation.validatePolicy(NegotiationPolicy.TlsAlpn, ProtocolSet.h2Only, tlsPresent = false).isLeft,
          ConnectorValidation
            .validatePolicy(NegotiationPolicy.TlsAlpn, ProtocolSet.h2cOnly, tlsPresent = false)
            .isLeft,
        )
      },
      test("CleartextPreface policy forbids TLS") {
        assertTrue(
          ConnectorValidation
            .validatePolicy(NegotiationPolicy.CleartextPreface, ProtocolSet.h1h2c, tlsPresent = false)
            .isRight,
          ConnectorValidation
            .validatePolicy(NegotiationPolicy.CleartextPreface, ProtocolSet.h1h2c, tlsPresent = true)
            .isLeft,
        )
      },
      test("Single policy requires a lone protocol") {
        assertTrue(
          ConnectorValidation.validatePolicy(NegotiationPolicy.Single, ProtocolSet.h2Only, tlsPresent = true).isRight,
          ConnectorValidation.validatePolicy(NegotiationPolicy.Single, ProtocolSet.h1h2, tlsPresent = true).isLeft,
        )
      },
    ),
    suite("connector validation")(
      test("default connector validates") {
        assertTrue(Connector.default.validate.isRight)
      },
      test("concise H2C construction still validates") {
        val c = Connector(bind = BindAddress.localhost(0), protocol = Protocol.H2C())
        assertTrue(c.validate.isRight, c.protocolSet == Right(ProtocolSet.h2cOnly))
      },
      test("concise H2 construction still validates") {
        val c = Connector(bind = BindAddress.localhost(0), protocol = Protocol.H2(tlsCfg))
        assertTrue(c.validate.isRight, c.protocolSet == Right(ProtocolSet.h2Only))
      },
      test("H3 connector validates to H3NotAdvertised, never silent support") {
        val c = Connector(bind = BindAddress.localhost(0), protocol = Protocol.H3(tlsCfg))
        assertTrue(c.validate == Left(ConnectorFailure.H3NotAdvertised))
      },
      test("UDP connector fails typed") {
        val isTcpUdpMismatch =
          Connector(bind = BindAddress.localhost(0), transport = TransportKind.Udp).validate match {
            case Left(ConnectorFailure.TcpUdpMismatch(_)) => true
            case _                                        => false
          }
        assertTrue(isTcpUdpMismatch)
      },
      test("ALPN policy without TLS fails typed") {
        val c = Connector(bind = BindAddress.localhost(0), negotiation = NegotiationPolicy.TlsAlpn)
        assertTrue(c.validate.isLeft, leftMessage(c.validate).nonEmpty)
      },
    ),
    suite("invalid configuration corpus")(
      test("every corpus entry fails with its typed failure") {
        val corpus: List[(String, () => Either[ConnectorFailure, Any], ConnectorFailure)] = List(
          (
            "duplicate-h1",
            () => ProtocolSet.fromSeq(List(AppProtocol.Http1, AppProtocol.Http1)),
            ConnectorFailure.DuplicateProtocol(AppProtocol.Http1),
          ),
          (
            "duplicate-h2",
            () => ProtocolSet.fromSeq(List(AppProtocol.H2, AppProtocol.H2, AppProtocol.H2)),
            ConnectorFailure.DuplicateProtocol(AppProtocol.H2),
          ),
          ("empty-seq", () => ProtocolSet.fromSeq(Nil), ConnectorFailure.EmptyProtocolSet),
          ("empty-set", () => ProtocolSet.fromSet(Set.empty[AppProtocol]), ConnectorFailure.EmptyProtocolSet),
          ("legacy-h3", () => ProtocolSet.fromLegacy(Protocol.H3(tlsCfg)), ConnectorFailure.H3NotAdvertised),
          (
            "udp-h1",
            () => ConnectorValidation.validateTransport(TransportKind.Udp, ProtocolSet.h1Only),
            ConnectorFailure.TcpUdpMismatch(""),
          ),
          (
            "udp-h2c",
            () => ConnectorValidation.validateTransport(TransportKind.Udp, ProtocolSet.h2cOnly),
            ConnectorFailure.TcpUdpMismatch(""),
          ),
          (
            "udp-h2",
            () => ConnectorValidation.validateTransport(TransportKind.Udp, ProtocolSet.h2Only),
            ConnectorFailure.TcpUdpMismatch(""),
          ),
          (
            "udp-h1h2",
            () => ConnectorValidation.validateTransport(TransportKind.Udp, ProtocolSet.h1h2),
            ConnectorFailure.TcpUdpMismatch(""),
          ),
          (
            "unknown-alpn",
            () => Negotiation.negotiateAlpn(ProtocolSet.h2Only, List("spdy/3")),
            ConnectorFailure.UnknownAlpnProtocol("spdy/3"),
          ),
          (
            "empty-alpn-offer",
            () => Negotiation.negotiateAlpn(ProtocolSet.h2Only, Nil),
            ConnectorFailure.NoAlpnOffered,
          ),
          (
            "h2-without-tls",
            () => ConnectorValidation.validateTls(ProtocolSet.h2Only, tlsPresent = false),
            ConnectorFailure.MissingTls(""),
          ),
          (
            "h2c-over-tls",
            () => ConnectorValidation.validateTls(ProtocolSet.h2cOnly, tlsPresent = true),
            ConnectorFailure.UnexpectedTls(""),
          ),
          ("single-over-shared", () => Negotiation.single(ProtocolSet.h1h2), ConnectorFailure.PolicyMismatch("")),
          (
            "preface-h1-only-with-h2-bytes",
            () => Negotiation.selectForPreface(ProtocolSet.h1Only, isH2Preface = true),
            ConnectorFailure.NoProtocolForPreface(isH2Preface = true),
          ),
          (
            "preface-h2-only-with-h1-bytes",
            () => Negotiation.selectForPreface(ProtocolSet.h2Only, isH2Preface = false),
            ConnectorFailure.NoProtocolForPreface(isH2Preface = false),
          ),
        )
        val failures = corpus.flatMap { case (name, run, expected) =>
          run() match {
            case Left(actual) if actual.getClass == expected.getClass => Nil
            case other                                                => List(name + " -> " + other)
          }
        }
        assertTrue(failures.mkString("; ").isEmpty)
      },
    ),
    suite("Blocks schema encoding")(
      test("every AppProtocol round-trips") {
        val all: List[AppProtocol] = List(AppProtocol.Http1, AppProtocol.H2C, AppProtocol.H2)
        assertTrue(
          all.forall(p => AppProtocol.schema.fromDynamicValue(AppProtocol.schema.toDynamicValue(p)) == Right(p)),
        )
      },
      test("every TransportKind round-trips") {
        val all: List[TransportKind] = List(TransportKind.Tcp, TransportKind.Udp)
        assertTrue(
          all.forall(k => TransportKind.schema.fromDynamicValue(TransportKind.schema.toDynamicValue(k)) == Right(k)),
        )
      },
      test("every NegotiationPolicy round-trips") {
        val all: List[NegotiationPolicy] =
          List(NegotiationPolicy.Single, NegotiationPolicy.TlsAlpn, NegotiationPolicy.CleartextPreface)
        assertTrue(
          all.forall(p =>
            NegotiationPolicy.schema.fromDynamicValue(NegotiationPolicy.schema.toDynamicValue(p)) == Right(p),
          ),
        )
      },
      test("shared sets round-trip with canonical member order") {
        val sets =
          List(ProtocolSet.h1Only, ProtocolSet.h2cOnly, ProtocolSet.h2Only, ProtocolSet.h1h2, ProtocolSet.h1h2c)
        assertTrue(
          sets.forall { set =>
            val back = ProtocolSet.schema.fromDynamicValue(ProtocolSet.schema.toDynamicValue(set))
            back == Right(set)
          },
        )
      },
      test("connector with new fields round-trips") {
        val c    = Connector(bind = BindAddress.localhost(0), protocol = Protocol.H2(tlsCfg))
        val back = Connector.schema.fromDynamicValue(Connector.schema.toDynamicValue(c))
        assertTrue(back == Right(c))
      },
      test("legacy encoded record migrates to the current connector") {
        val current = Connector(bind = BindAddress.localhost(0), protocol = Protocol.H2C())
        val legacy  = ConnectorMigration.toLegacyRecord(current)
        val back    = ConnectorMigration.migrateLegacyRecord(legacy)
        assertTrue(back == Right(current))
      },
      test("legacy H3 record migrates structurally but never validates") {
        val legacyH3 =
          ConnectorMigration.toLegacyRecord(Connector(bind = BindAddress.localhost(0), protocol = Protocol.H3(tlsCfg)))
        ConnectorMigration.migrateLegacyRecord(legacyH3) match {
          case Right(migrated) => assertTrue(migrated.validate == Left(ConnectorFailure.H3NotAdvertised))
          case Left(error)     => assertTrue(error.isEmpty)
        }
      },
      test("unknown protocol variant fails to decode") {
        val bad = DynamicValue.Variant("H9", DynamicValue.Record())
        assertTrue(AppProtocol.schema.fromDynamicValue(bad).isLeft)
      },
      test("unknown transport variant fails to decode") {
        val bad = DynamicValue.Variant("Quic", DynamicValue.Record())
        assertTrue(TransportKind.schema.fromDynamicValue(bad).isLeft)
      },
      test("empty encoded set fails to decode") {
        val bad = DynamicValue.Sequence()
        assertTrue(ProtocolSet.schema.fromDynamicValue(bad).isLeft)
      },
    ),
  )
}
