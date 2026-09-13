package zio.http

import zio.blocks.schema.DynamicValue
import zio.blocks.schema.Schema

/**
 * Validated application-protocol identities served by a [[Connector]].
 *
 * These are wire identities only — no codec or engine configuration lives here.
 * `H2C` is cleartext prior-knowledge/preface HTTP/2 (no ALPN id); `H2` is TLS
 * HTTP/2 (`h2`); `Http1` is HTTP/1.1 (`http/1.1`, cleartext or TLS). There is
 * deliberately no H3/QUIC member: future UDP transport is registered through
 * [[TransportKind.Udp]], but production configuration must not advertise or run
 * H3 (see [[ConnectorFailure.H3NotAdvertised]] and
 * [[ConnectorValidation.validateTransport]]).
 */
sealed trait AppProtocol {

  /** ALPN wire id, or `None` for cleartext-only protocols (H2C). */
  def alpnId: Option[String]

  /** True when the protocol is only legal on a TLS endpoint (H2). */
  def requiresTls: Boolean

  /** True when the protocol is only legal on a cleartext endpoint (H2C). */
  def forbidsTls: Boolean
}

object AppProtocol {

  /** HTTP/1.1: negotiable as `http/1.1` over TLS or served on cleartext. */
  case object Http1 extends AppProtocol {
    val alpnId: Option[String] = Some("http/1.1")
    val requiresTls: Boolean   = false
    val forbidsTls: Boolean    = false
  }

  /** Cleartext HTTP/2: prior-knowledge or H2-preface detection, never ALPN. */
  case object H2C extends AppProtocol {
    val alpnId: Option[String] = None
    val requiresTls: Boolean   = false
    val forbidsTls: Boolean    = true
  }

  /** TLS HTTP/2: negotiable as `h2`, never cleartext. */
  case object H2 extends AppProtocol {
    val alpnId: Option[String] = Some("h2")
    val requiresTls: Boolean   = true
    val forbidsTls: Boolean    = false
  }

  /** Canonical member order for stable schema encoding. */
  def index(protocol: AppProtocol): Int =
    protocol match {
      case Http1 => 0
      case H2C   => 1
      case H2    => 2
    }

  /**
   * Looks up a protocol by ALPN wire id. Cleartext-only `H2C` has no ALPN id
   * and is never returned here — it is selected by cleartext preface detection
   * (see [[Negotiation.selectForPreface]]), not by ALPN.
   */
  def fromAlpnId(id: String): Option[AppProtocol] =
    if (id == "http/1.1") Some(Http1)
    else if (id == "h2") Some(H2)
    else None

  implicit val http1Schema: Schema[Http1.type] = Schema.derived[Http1.type]
  implicit val h2cSchema: Schema[H2C.type]     = Schema.derived[H2C.type]
  implicit val h2Schema: Schema[H2.type]       = Schema.derived[H2.type]
  implicit val schema: Schema[AppProtocol]     = Schema.derived[AppProtocol]
}

/**
 * Network transport family of a [[Connector]] binding and of a
 * [[ProtocolEngine]].
 *
 * This is the single transport-kind contract shared by the connector model and
 * the engine model: `Tcp` serves the H1/H2C/H2 family, `Udp` is registered for
 * the future QUIC/H3 seam only (no engine is installed, so
 * [[ConnectorValidation.validateTransport]] rejects every UDP binding with
 * [[ConnectorFailure.TcpUdpMismatch]]), and `Unix` names Unix-domain-socket
 * engines. TCP and UDP draw numeric ports from independent OS namespaces, so a
 * TCP connector/engine and a UDP connector/engine may share a numeric port (see
 * [[TransportKind.sharesPortNamespace]] and [[Connector.bindConflicts]]).
 * Production configuration must not advertise or run H3.
 */
sealed trait TransportKind

object TransportKind {

  case object Tcp  extends TransportKind
  case object Udp  extends TransportKind
  case object Unix extends TransportKind

  /**
   * Parses a transport kind by case-object name. Unknown names are rejected
   * with [[IllegalArgumentException]] — never defaulted silently.
   */
  def fromString(name: String): TransportKind =
    name match {
      case "Tcp"  => Tcp
      case "Udp"  => Udp
      case "Unix" => Unix
      case other  =>
        throw new IllegalArgumentException(
          s"Unknown TransportKind: '$other'. Expected one of: Tcp, Udp, Unix",
        )
    }

  /**
   * True when `first` and `second` draw numeric ports from the same OS
   * namespace: only same-family socket transports share one. In particular a
   * TCP binding and a UDP binding never conflict, so a future UDP engine can
   * coexist with TCP engines on one numeric port. Unix-domain sockets bind
   * paths, not ports, and share no port namespace with anything.
   */
  def sharesPortNamespace(first: TransportKind, second: TransportKind): Boolean =
    (first, second) match {
      case (Tcp, Tcp) => true
      case (Udp, Udp) => true
      case _          => false
    }

  implicit val tcpSchema: Schema[Tcp.type]   = Schema.derived[Tcp.type]
  implicit val udpSchema: Schema[Udp.type]   = Schema.derived[Udp.type]
  implicit val unixSchema: Schema[Unix.type] = Schema.derived[Unix.type]
  implicit val schema: Schema[TransportKind] = Schema.derived[TransportKind]
}

/**
 * A validated non-empty set of [[AppProtocol]] identities served by one
 * [[Connector]].
 *
 * Named constants cover the supported shapes: [[h1Only]], [[h2cOnly]],
 * [[h2Only]], shared-TLS [[h1h2]], and shared-cleartext [[h1h2c]]. Construction
 * is total through [[fromSet]]/[[fromSeq]]/[[fromLegacy]], which report
 * [[ConnectorFailure.DuplicateProtocol]] and
 * [[ConnectorFailure.EmptyProtocolSet]] instead of throwing.
 */
final class ProtocolSet private (val members: Set[AppProtocol]) {

  def contains(protocol: AppProtocol): Boolean = members.contains(protocol)

  def size: Int = members.size

  def isSingle: Boolean = members.size == 1

  /** Members in canonical [[AppProtocol.index]] order. */
  def toList: List[AppProtocol] = members.toList.sortBy(AppProtocol.index)

  override def equals(other: Any): Boolean =
    other match {
      case that: ProtocolSet => members == that.members
      case _                 => false
    }

  override def hashCode(): Int = members.hashCode()

  override def toString: String = "ProtocolSet(" + toList.mkString(", ") + ")"
}

object ProtocolSet {

  /** H1-only endpoint (cleartext or TLS). */
  val h1Only: ProtocolSet = unsafeWrap(Set(AppProtocol.Http1))

  /** H2-only cleartext endpoint (prior-knowledge / preface). */
  val h2cOnly: ProtocolSet = unsafeWrap(Set(AppProtocol.H2C))

  /** H2-only TLS endpoint. */
  val h2Only: ProtocolSet = unsafeWrap(Set(AppProtocol.H2))

  /** Shared H1+H2 TLS endpoint (TLS ALPN selects). */
  val h1h2: ProtocolSet = unsafeWrap(Set(AppProtocol.Http1, AppProtocol.H2))

  /** Shared H1+H2C cleartext endpoint (preface detection selects). */
  val h1h2c: ProtocolSet = unsafeWrap(Set(AppProtocol.Http1, AppProtocol.H2C))

  /**
   * Validated construction from a set. Reports
   * [[ConnectorFailure.EmptyProtocolSet]] for the empty set; duplicates are
   * impossible in a `Set` by construction.
   */
  def fromSet(members: Set[AppProtocol]): Either[ConnectorFailure, ProtocolSet] =
    if (members.isEmpty) Left(ConnectorFailure.EmptyProtocolSet)
    else Right(unsafeWrap(members))

  /**
   * Validated construction from a sequence. Reports
   * [[ConnectorFailure.DuplicateProtocol]] for the first repeated member and
   * [[ConnectorFailure.EmptyProtocolSet]] for the empty sequence.
   */
  def fromSeq(members: Seq[AppProtocol]): Either[ConnectorFailure, ProtocolSet] = {
    var seen                             = Set.empty[AppProtocol]
    var index                            = 0
    var failed: Option[ConnectorFailure] = None
    while (index < members.length && failed.isEmpty) {
      val member = members(index)
      if (seen.contains(member)) failed = Some(ConnectorFailure.DuplicateProtocol(member))
      else seen = seen + member
      index += 1
    }
    failed match {
      case Some(failure) => Left(failure)
      case None          => fromSet(seen)
    }
  }

  /**
   * Migration from the prior unreleased single-`protocol` shape. `H2C` and `H2`
   * map to their singleton sets; `H3` has no mapping and reports
   * [[ConnectorFailure.H3NotAdvertised]] — H3 is not advertised or runnable.
   */
  def fromLegacy(protocol: Protocol): Either[ConnectorFailure, ProtocolSet] =
    protocol match {
      case Protocol.H2C(_)      => Right(h2cOnly)
      case Protocol.H2(_, _)    => Right(h2Only)
      case Protocol.H3(_, _, _) => Left(ConnectorFailure.H3NotAdvertised)
    }

  /**
   * Explicit Blocks Schema encoding: members as a canonically ordered list so
   * the encoded form is stable regardless of `Set` iteration order. Decoding
   * re-validates through [[fromSeq]], so an empty encoded sequence fails
   * instead of decoding silently.
   */
  implicit val schema: Schema[ProtocolSet] =
    Schema[List[AppProtocol]].transform(
      list =>
        fromSeq(list) match {
          case Right(set)    => set
          case Left(failure) => throw new IllegalArgumentException(failure.message)
        },
      set => set.toList,
    )

  private def unsafeWrap(members: Set[AppProtocol]): ProtocolSet =
    new ProtocolSet(members)
}

/**
 * How a [[Connector]] selects one [[AppProtocol]] per connection.
 *
 *   - [[Single]]: exactly one protocol is configured; no negotiation happens.
 *   - [[TlsAlpn]]: the TLS ALPN offer selects among the configured set (see
 *     [[Negotiation.negotiateAlpn]]); unknown or absent offers fail typed.
 *   - [[CleartextPreface]]: exact H2-preface detection on a cleartext endpoint
 *     selects between H1 and H2C (see [[Negotiation.selectForPreface]]); no h2c
 *     Upgrade is involved.
 */
sealed trait NegotiationPolicy

object NegotiationPolicy {

  case object Single           extends NegotiationPolicy
  case object TlsAlpn          extends NegotiationPolicy
  case object CleartextPreface extends NegotiationPolicy

  implicit val singleSchema: Schema[Single.type]                     = Schema.derived[Single.type]
  implicit val tlsAlpnSchema: Schema[TlsAlpn.type]                   = Schema.derived[TlsAlpn.type]
  implicit val cleartextPrefaceSchema: Schema[CleartextPreface.type] = Schema.derived[CleartextPreface.type]
  implicit val schema: Schema[NegotiationPolicy]                     = Schema.derived[NegotiationPolicy]
}

/**
 * Typed connector configuration failures.
 *
 * Validation reports these as `Left` values instead of throwing, so callers can
 * distinguish a duplicate protocol from an empty set, a TCP/UDP family
 * mismatch, an unknown ALPN id, or an unadvertised H3 without parsing exception
 * messages.
 */
sealed trait ConnectorFailure {

  /** Stable human-readable description; never parses back into a failure. */
  def message: String
}

object ConnectorFailure {

  final case class DuplicateProtocol(protocol: AppProtocol) extends ConnectorFailure {
    val message: String = "duplicate protocol in set: " + protocol
  }

  case object EmptyProtocolSet extends ConnectorFailure {
    val message: String = "protocol set must not be empty"
  }

  final case class TcpUdpMismatch(detail: String) extends ConnectorFailure {
    val message: String = "transport/protocol family mismatch: " + detail
  }

  final case class UnknownAlpnProtocol(alpnId: String) extends ConnectorFailure {
    val message: String = "unknown ALPN protocol: '" + alpnId + "'"
  }

  case object NoAlpnOffered extends ConnectorFailure {
    val message: String = "TLS client offered no ALPN protocols"
  }

  final case class MissingTls(detail: String) extends ConnectorFailure {
    val message: String = "TLS required but absent: " + detail
  }

  final case class UnexpectedTls(detail: String) extends ConnectorFailure {
    val message: String = "TLS present but forbidden: " + detail
  }

  final case class PolicyMismatch(detail: String) extends ConnectorFailure {
    val message: String = "negotiation policy mismatch: " + detail
  }

  final case class NoProtocolForPreface(isH2Preface: Boolean) extends ConnectorFailure {
    val message: String =
      if (isH2Preface) "H2 preface received but no H2C member is configured"
      else "non-preface bytes received but no H1 member is configured"
  }

  /**
   * The legacy `Protocol.H3` shape (and any H3 advertisement) is rejected:
   * H3/QUIC has no installed engine, so production configuration must neither
   * advertise nor run it.
   */
  case object H3NotAdvertised extends ConnectorFailure {
    val message: String = "H3/QUIC is not advertised: no H3 engine is installed"
  }
}

/**
 * Deterministic serve-time refusal of an invalid [[Connector]].
 *
 * Thrown by serve before any socket is bound when [[Connector.validate]]
 * reports a [[ConnectorFailure]] (unadvertised H3, UDP transport without a
 * QUIC-family protocol, TLS/policy mismatches). Carries the typed failure so
 * callers can distinguish the cause without parsing exception messages.
 */
final case class InvalidConnector(failure: ConnectorFailure) extends Exception("Invalid connector: " + failure.message)

/**
 * Pure validation of protocol sets against endpoint properties.
 *
 * Every rule reports a [[ConnectorFailure]]; nothing here binds sockets,
 * performs handshakes, or dispatches bytes — runtime dispatch belongs to the
 * engine and listener work that follows this model.
 */
object ConnectorValidation {

  /**
   * Transport/protocol family check. TCP serves the H1/H2C/H2 family; UDP is
   * registered for the future QUIC seam only and every UDP binding fails with
   * [[ConnectorFailure.TcpUdpMismatch]] because no QUIC-family protocol is
   * advertised. Unix-domain-socket transports carry no TCP/UDP protocol family
   * and fail the same way.
   */
  def validateTransport(transport: TransportKind, set: ProtocolSet): Either[ConnectorFailure, Unit] =
    transport match {
      case TransportKind.Tcp  => Right(())
      case TransportKind.Udp  =>
        Left(
          ConnectorFailure.TcpUdpMismatch(
            "UDP transport requires a QUIC-family protocol, but the set holds " + set +
              "; H3/QUIC is not advertised in this build",
          ),
        )
      case TransportKind.Unix =>
        Left(
          ConnectorFailure.TcpUdpMismatch(
            "Unix transport carries no TCP/UDP protocol family, but the set holds " + set,
          ),
        )
    }

  /**
   * TLS presence check per member requirement: `H2` members require TLS, a lone
   * `H2C` member forbids it, `Http1` is agnostic.
   */
  def validateTls(set: ProtocolSet, tlsPresent: Boolean): Either[ConnectorFailure, Unit] =
    if (!tlsPresent && set.contains(AppProtocol.H2))
      Left(ConnectorFailure.MissingTls("H2 requires a TLS endpoint"))
    else if (tlsPresent && set.isSingle && set.contains(AppProtocol.H2C))
      Left(ConnectorFailure.UnexpectedTls("H2C is cleartext-only and cannot serve a TLS endpoint alone"))
    else Right(())

  /**
   * Negotiation-policy coherence check: `Single` needs a singleton set,
   * `TlsAlpn` needs TLS plus an ALPN-capable member, `CleartextPreface` needs a
   * cleartext endpoint.
   */
  def validatePolicy(
    policy: NegotiationPolicy,
    set: ProtocolSet,
    tlsPresent: Boolean,
  ): Either[ConnectorFailure, Unit] =
    policy match {
      case NegotiationPolicy.Single           =>
        if (set.isSingle) Right(())
        else Left(ConnectorFailure.PolicyMismatch("Single policy holds " + set.size + " protocols: " + set))
      case NegotiationPolicy.TlsAlpn          =>
        if (!tlsPresent)
          Left(ConnectorFailure.MissingTls("TlsAlpn policy requires a TLS endpoint"))
        else if (!set.members.exists(_.alpnId.isDefined))
          Left(ConnectorFailure.PolicyMismatch("TlsAlpn policy needs an ALPN-capable member, but the set is " + set))
        else Right(())
      case NegotiationPolicy.CleartextPreface =>
        if (tlsPresent)
          Left(ConnectorFailure.UnexpectedTls("CleartextPreface policy requires a cleartext endpoint"))
        else if (!set.contains(AppProtocol.Http1) && !set.contains(AppProtocol.H2C))
          Left(
            ConnectorFailure.PolicyMismatch("CleartextPreface policy needs an H1 or H2C member, but the set is " + set),
          )
        else Right(())
    }
}

/**
 * Pure per-connection protocol selection for each [[NegotiationPolicy]].
 *
 * These decide only — they never touch sockets. TLS ALPN dispatch, exact
 * H2-preface sniffing, and the replay-buffer mechanics that execute these
 * decisions belong to the listener/engine work that follows this model.
 */
object Negotiation {

  /**
   * [[NegotiationPolicy.Single]] selection: the lone configured protocol, or
   * [[ConnectorFailure.PolicyMismatch]] for a shared set.
   */
  def single(set: ProtocolSet): Either[ConnectorFailure, AppProtocol] =
    if (set.isSingle) Right(set.toList.head)
    else Left(ConnectorFailure.PolicyMismatch("Single policy holds " + set.size + " protocols: " + set))

  /**
   * [[NegotiationPolicy.TlsAlpn]] selection: the first offered ALPN id that a
   * configured member advertises, following client offer order. An empty offer
   * fails with [[ConnectorFailure.NoAlpnOffered]]; no match fails with
   * [[ConnectorFailure.UnknownAlpnProtocol]] carrying the first offered id.
   * Cleartext-only `H2C` has no ALPN id and is never selected here.
   */
  def negotiateAlpn(set: ProtocolSet, offered: List[String]): Either[ConnectorFailure, AppProtocol] =
    offered match {
      case Nil        => Left(ConnectorFailure.NoAlpnOffered)
      case first :: _ =>
        var remaining: List[String]       = offered
        var selected: Option[AppProtocol] = None
        while (remaining.nonEmpty && selected.isEmpty) {
          selected = AppProtocol.fromAlpnId(remaining.head).filter(set.contains)
          remaining = remaining.tail
        }
        selected match {
          case Some(protocol) => Right(protocol)
          case None           => Left(ConnectorFailure.UnknownAlpnProtocol(first))
        }
    }

  /**
   * [[NegotiationPolicy.CleartextPreface]] selection: an exact H2-preface match
   * selects `H2C`, any other opening bytes select `H1`. A missing matching
   * member fails with [[ConnectorFailure.NoProtocolForPreface]] instead of
   * downgrading silently.
   */
  def selectForPreface(set: ProtocolSet, isH2Preface: Boolean): Either[ConnectorFailure, AppProtocol] =
    if (isH2Preface)
      if (set.contains(AppProtocol.H2C)) Right(AppProtocol.H2C)
      else Left(ConnectorFailure.NoProtocolForPreface(isH2Preface = true))
    else if (set.contains(AppProtocol.Http1)) Right(AppProtocol.Http1)
    else Left(ConnectorFailure.NoProtocolForPreface(isH2Preface = false))
}

/**
 * Encoded-form migration from the prior unreleased single-`protocol`
 * [[Connector]] shape to the protocol-set model.
 *
 * Compatibility decision (explicit, not assumed): the case-class/binary shape
 * intentionally changed — two fields were added and new types were introduced —
 * so source and binary compatibility with the prior unreleased shape is NOT
 * claimed. Encoded configuration migrates forward only: [[toLegacyRecord]]
 * reproduces the exact pre-change encoded shape (all fields except `transport`
 * and `negotiation`), and [[migrateLegacyRecord]] decodes such a record into
 * the current [[Connector]] by injecting the current defaults. A migrated
 * legacy `H3` connector decodes structurally but never validates (see
 * [[ConnectorFailure.H3NotAdvertised]]).
 */
object ConnectorMigration {

  /**
   * Current defaults injected for records written before these fields existed.
   */
  val defaultTransport: TransportKind       = TransportKind.Tcp
  val defaultNegotiation: NegotiationPolicy = NegotiationPolicy.Single

  /**
   * Reproduces the exact pre-change encoded shape of `connector`: every field
   * the prior shape wrote, minus `transport`/`negotiation`.
   */
  def toLegacyRecord(connector: Connector): DynamicValue =
    Connector.schema.toDynamicValue(connector) match {
      case DynamicValue.Record(fields) =>
        DynamicValue.Record(fields.filter { case (name, _) => name != "transport" && name != "negotiation" })
      case other                       =>
        throw new IllegalStateException("Connector must encode as a record but encoded as " + other)
    }

  /**
   * Migrates a pre-change encoded record into the current [[Connector]].
   * Records that already carry the new fields decode directly; records that
   * predate them receive the current defaults. Structurally invalid records
   * fail with the decoder message — never with a silent default.
   */
  def migrateLegacyRecord(record: DynamicValue): Either[String, Connector] =
    record match {
      case DynamicValue.Record(fields) =>
        val names   = fields.map(_._1).toSet
        val patched =
          if (names.contains("transport") && names.contains("negotiation")) record
          else {
            var extended = fields
            if (!names.contains("transport"))
              extended = extended :+ (("transport", TransportKind.schema.toDynamicValue(defaultTransport)))
            if (!names.contains("negotiation"))
              extended = extended :+ (("negotiation", NegotiationPolicy.schema.toDynamicValue(defaultNegotiation)))
            DynamicValue.Record(extended)
          }
        Connector.schema.fromDynamicValue(patched).left.map(_.toString)
      case other                       =>
        Left("legacy connector record must be a record but was " + other)
    }
}
