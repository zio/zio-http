package zio.http.model.headers.values

import java.net.URI
import scala.util.Try

//scalafmt: { maxColumn = 180 }
sealed trait ContentSecurityPolicy

// TODO: Should we make deprecated types deprecated in code?
object ContentSecurityPolicy {
  final case class SourcePolicy(srcType: SourcePolicyType, src: Source) extends ContentSecurityPolicy
  case object BlockAllMixedContent                                      extends ContentSecurityPolicy
  // TODO: Deprecated and only Safari supports this and it is non-standard. Should we remove it?
  final case class PluginTypes(value: String)                           extends ContentSecurityPolicy
  // TODO: no modern browser supports this. Should we remove it?
  final case class Referrer(referrer: ReferrerPolicy)                   extends ContentSecurityPolicy
  final case class ReportTo(groupName: String)                          extends ContentSecurityPolicy
  final case class ReportUri(uri: URI)                                  extends ContentSecurityPolicy
  final case class RequireSriFor(requirement: RequireSriForValue)       extends ContentSecurityPolicy
  final case class Sandbox(value: SandboxValue)                         extends ContentSecurityPolicy
  final case class TrustedTypes(value: TrustedTypesValue)               extends ContentSecurityPolicy
  case object UpgradeInsecureRequests                                   extends ContentSecurityPolicy
  case object InvalidContentSecurityPolicy                              extends ContentSecurityPolicy

  sealed trait SourcePolicyType
  object SourcePolicyType {
    case object `base-uri`                  extends SourcePolicyType
    case object `child-src`                 extends SourcePolicyType
    case object `connect-src`               extends SourcePolicyType
    case object `default-src`               extends SourcePolicyType
    case object `font-src`                  extends SourcePolicyType
    case object `form-action`               extends SourcePolicyType
    case object `frame-ancestors`           extends SourcePolicyType
    case object `frame-src`                 extends SourcePolicyType
    case object `img-src`                   extends SourcePolicyType
    case object `manifest-src`              extends SourcePolicyType
    case object `media-src`                 extends SourcePolicyType
    case object `object-src`                extends SourcePolicyType
    case object `prefetch-src`              extends SourcePolicyType
    case object `script-src`                extends SourcePolicyType
    case object `script-src-attr`           extends SourcePolicyType
    case object `script-src-elem`           extends SourcePolicyType
    case object `style-src`                 extends SourcePolicyType
    case object `style-src-attr`            extends SourcePolicyType
    case object `style-src-elem`            extends SourcePolicyType
    case object `upgrade-insecure-requests` extends SourcePolicyType
    case object `worker-src`                extends SourcePolicyType

    def fromString(s: String): Option[SourcePolicyType] = s match {
      case "base-uri"                  => Some(`base-uri`)
      case "child-src"                 => Some(`child-src`)
      case "connect-src"               => Some(`connect-src`)
      case "default-src"               => Some(`default-src`)
      case "font-src"                  => Some(`font-src`)
      case "form-action"               => Some(`form-action`)
      case "frame-ancestors"           => Some(`frame-ancestors`)
      case "frame-src"                 => Some(`frame-src`)
      case "img-src"                   => Some(`img-src`)
      case "manifest-src"              => Some(`manifest-src`)
      case "media-src"                 => Some(`media-src`)
      case "object-src"                => Some(`object-src`)
      case "prefetch-src"              => Some(`prefetch-src`)
      case "script-src"                => Some(`script-src`)
      case "script-src-attr"           => Some(`script-src-attr`)
      case "script-src-elem"           => Some(`script-src-elem`)
      case "style-src"                 => Some(`style-src`)
      case "style-src-attr"            => Some(`style-src-attr`)
      case "style-src-elem"            => Some(`style-src-elem`)
      case "upgrade-insecure-requests" => Some(`upgrade-insecure-requests`)
      case "worker-src"                => Some(`worker-src`)
      case _                           => None
    }

    def toString(policyType: SourcePolicyType) =
      policyType match {
        case `base-uri`                  => "base-uri"
        case `child-src`                 => "child-src"
        case `connect-src`               => "connect-src"
        case `default-src`               => "default-src"
        case `font-src`                  => "font-src"
        case `form-action`               => "form-action"
        case `frame-ancestors`           => "frame-ancestors"
        case `frame-src`                 => "frame-src"
        case `img-src`                   => "img-src"
        case `manifest-src`              => "manifest-src"
        case `media-src`                 => "media-src"
        case `object-src`                => "object-src"
        case `prefetch-src`              => "prefetch-src"
        case `script-src`                => "script-src"
        case `script-src-attr`           => "script-src-attr"
        case `script-src-elem`           => "script-src-elem"
        case `style-src`                 => "style-src"
        case `style-src-attr`            => "style-src-attr"
        case `style-src-elem`            => "style-src-elem"
        case `upgrade-insecure-requests` => "upgrade-insecure-requests"
        case `worker-src`                => "worker-src"
      }
  }

  sealed trait Source { self =>
    def &&(other: Source): Source =
      if (other == Source.none) self else Source.Sequence(self, other)
  }
  object Source       {
    case object none                                               extends Source {
      override def &&(other: Source): Source = other
    }
    final case class Host(uri: URI)                                extends Source
    final case class Scheme(scheme: String)                        extends Source
    case object Self                                               extends Source
    case object UnsafeEval                                         extends Source
    case object WasmUnsafeEval                                     extends Source
    case object UnsafeHashes                                       extends Source
    case object UnsafeInline                                       extends Source
    final case class Nonce(value: String)                          extends Source
    final case class Hash(algorithm: HashAlgorithm, value: String) extends Source
    case object StrictDynamic                                      extends Source
    case object ReportSample                                       extends Source
    final case class Sequence(left: Source, right: Source)         extends Source

    sealed trait HashAlgorithm
    object HashAlgorithm {
      case object Sha256 extends HashAlgorithm
      case object Sha384 extends HashAlgorithm
      case object Sha512 extends HashAlgorithm

      def fromString(s: String): Option[HashAlgorithm] = s match {
        case "sha256" => Some(Sha256)
        case "sha384" => Some(Sha384)
        case "sha512" => Some(Sha512)
        case _        => None
      }
    }

    def fromString(s: String): Option[Source] = s match {
      case "'none'"           => Some(none)
      case "'self'"           => Some(Self)
      case "'unsafe-eval'"    => Some(UnsafeEval)
      case "'wasm-eval'"      => Some(WasmUnsafeEval)
      case "'unsafe-hashes'"  => Some(UnsafeHashes)
      case "'unsafe-inline'"  => Some(UnsafeInline)
      case "'strict-dynamic'" => Some(StrictDynamic)
      case "'report-sample'"  => Some(ReportSample)
      case s"'nonce-$nonce'"  => Some(Nonce(nonce))
      case s"'sha256-$hash'"  => Some(Hash(HashAlgorithm.Sha256, hash))
      case s"'sha384-$hash'"  => Some(Hash(HashAlgorithm.Sha384, hash))
      case s"'sha512-$hash'"  => Some(Hash(HashAlgorithm.Sha512, hash))
      case s                  => Try(URI.create(s)).map(Host).toOption
    }

    def toString(source: Source): String = source match {
      case Source.none           => "'none'"
      case Self                  => "'self'"
      case UnsafeEval            => "'unsafe-eval'"
      case WasmUnsafeEval        => "'wasm-eval'"
      case UnsafeHashes          => "'unsafe-hashes'"
      case UnsafeInline          => "'unsafe-inline'"
      case StrictDynamic         => "'strict-dynamic'"
      case ReportSample          => "'report-sample'"
      case Nonce(nonce)          => s"'nonce-$nonce'"
      case Hash(algorithm, hash) => s"'$algorithm-$hash'"
      case Sequence(left, right) => s"${toString(left)} ${toString(right)}"
      case Host(uri)             => uri.toString
      case Scheme(scheme)        => s"$scheme:"
    }

    def host(uri: URI): Source                                = Host(uri)
    def scheme(scheme: String): Source                        = Scheme(scheme)
    def nonce(value: String): Source                          = Nonce(value)
    def hash(algorithm: HashAlgorithm, value: String): Source = Hash(algorithm, value)
  }

  sealed trait SandboxValue { self =>
    def &&(other: SandboxValue): SandboxValue =
      if (other == SandboxValue.Empty) self else SandboxValue.Sequence(self, other)
  }
  object SandboxValue       {
    case object Empty                                                  extends SandboxValue {
      override def &&(other: SandboxValue): SandboxValue = other
    }
    case object AllowForms                                             extends SandboxValue
    case object AllowSameOrigin                                        extends SandboxValue
    case object AllowScripts                                           extends SandboxValue
    case object AllowPopups                                            extends SandboxValue
    case object AllowModals                                            extends SandboxValue
    case object AllowOrientationLock                                   extends SandboxValue
    case object AllowPointerLock                                       extends SandboxValue
    case object AllowPresentation                                      extends SandboxValue
    case object AllowPopupsToEscapeSandbox                             extends SandboxValue
    case object AllowTopNavigation                                     extends SandboxValue
    final case class Sequence(left: SandboxValue, right: SandboxValue) extends SandboxValue

    def fromString(value: String): Option[SandboxValue] = {
      def parseOne: String => Option[SandboxValue] = {
        case "allow-forms"                    => Some(AllowForms)
        case "allow-same-origin"              => Some(AllowSameOrigin)
        case "allow-scripts"                  => Some(AllowScripts)
        case "allow-popups"                   => Some(AllowPopups)
        case "allow-modals"                   => Some(AllowModals)
        case "allow-orientation-lock"         => Some(AllowOrientationLock)
        case "allow-pointer-lock"             => Some(AllowPointerLock)
        case "allow-presentation"             => Some(AllowPresentation)
        case "allow-popups-to-escape-sandbox" => Some(AllowPopupsToEscapeSandbox)
        case "allow-top-navigation"           => Some(AllowTopNavigation)
        case _                                => None
      }

      value match {
        case "" => Some(Empty)
        case s  =>
          s.split(' ').toList.foldLeft(Option(Empty): Option[SandboxValue]) {
            case (Some(acc), v) => parseOne(v).map(acc && _)
            case (None, _)      => None
          }
      }
    }

    def toString(value: SandboxValue): String = {
      def toStringOne: SandboxValue => String = {
        case AllowForms                 => "allow-forms"
        case AllowSameOrigin            => "allow-same-origin"
        case AllowScripts               => "allow-scripts"
        case AllowPopups                => "allow-popups"
        case AllowModals                => "allow-modals"
        case AllowOrientationLock       => "allow-orientation-lock"
        case AllowPointerLock           => "allow-pointer-lock"
        case AllowPresentation          => "allow-presentation"
        case AllowPopupsToEscapeSandbox => "allow-popups-to-escape-sandbox"
        case AllowTopNavigation         => "allow-top-navigation"
        case Empty                      => ""
        case Sequence(left, right)      => toStringOne(left) + " " + toStringOne(right)
      }
      toStringOne(value)
    }
  }

  sealed trait TrustedTypesValue extends scala.Product with Serializable { self =>
    def &&(other: TrustedTypesValue): TrustedTypesValue =
      if (other == TrustedTypesValue.none) self else TrustedTypesValue.Sequence(self, other)
  }
  object TrustedTypesValue {
    case object none                                                             extends TrustedTypesValue {
      override def &&(other: TrustedTypesValue): TrustedTypesValue = other
    }
    final case class PolicyName(value: String)                                   extends TrustedTypesValue
    case object `allow-duplicates`                                               extends TrustedTypesValue
    case object Wildcard                                                         extends TrustedTypesValue
    final case class Sequence(left: TrustedTypesValue, right: TrustedTypesValue) extends TrustedTypesValue

    private val PolicyNameRegex = """\*|[a-zA-Z0-9-#=_/@.%]+|'allow-duplicates'|'none'""".r

    def fromString(value: String): Option[TrustedTypesValue]    = {
      val allValues = PolicyNameRegex.findAllIn(value).toList
      if (allValues.isEmpty) None
      else {
        Some {
          allValues.map {
            case "*"                  => TrustedTypesValue.Wildcard
            case "'none'"             => TrustedTypesValue.none
            case "'allow-duplicates'" => TrustedTypesValue.`allow-duplicates`
            case policyName           => TrustedTypesValue.PolicyName(policyName)
          }.reduce(_ && _)
        }
      }
    }
    def fromTrustedTypesValue(value: TrustedTypesValue): String =
      value match {
        case TrustedTypesValue.none                   => "'none'"
        case TrustedTypesValue.Wildcard               => "*"
        case TrustedTypesValue.`allow-duplicates`     => "'allow-duplicates'"
        case TrustedTypesValue.PolicyName(policyName) => policyName
        case TrustedTypesValue.Sequence(left, right)  =>
          fromTrustedTypesValue(left) + " " + fromTrustedTypesValue(right)
      }
  }

  sealed trait ReferrerPolicy extends scala.Product with Serializable
  object ReferrerPolicy {

    case object `no-referrer`              extends ReferrerPolicy
    case object `none-when-downgrade`      extends ReferrerPolicy
    case object `origin`                   extends ReferrerPolicy
    case object `origin-when-cross-origin` extends ReferrerPolicy
    case object `unsafe-url`               extends ReferrerPolicy

    def fromString(referrer: String): Option[ReferrerPolicy] =
      referrer match {
        case "no-referrer"              => Some(`no-referrer`)
        case "none-when-downgrade"      => Some(`none-when-downgrade`)
        case "origin"                   => Some(`origin`)
        case "origin-when-cross-origin" => Some(`origin-when-cross-origin`)
        case "unsafe-url"               => Some(`unsafe-url`)
        case _                          => None
      }

    def toString(referrer: ReferrerPolicy): String = referrer.productPrefix
  }

  sealed trait RequireSriForValue extends scala.Product with Serializable
  object RequireSriForValue {
    case object Script      extends RequireSriForValue
    case object Style       extends RequireSriForValue
    case object ScriptStyle extends RequireSriForValue

    def fromString(value: String): Option[RequireSriForValue]     =
      value match {
        case "script"       => Some(Script)
        case "style"        => Some(Style)
        case "script style" => Some(ScriptStyle)
        case _              => None
      }
    def fromRequireSriForValue(value: RequireSriForValue): String =
      value match {
        case Script      => "script"
        case Style       => "style"
        case ScriptStyle => "script style"
      }
  }

  def defaultSrc(src: Source*): SourcePolicy =
    SourcePolicy(SourcePolicyType.`default-src`, src.foldLeft[Source](Source.none)(_ && _))
  def scriptSrc(src: Source*): SourcePolicy  =
    SourcePolicy(SourcePolicyType.`script-src`, src.foldLeft[Source](Source.none)(_ && _))
  def styleSrc(src: Source*): SourcePolicy   =
    SourcePolicy(SourcePolicyType.`style-src`, src.foldLeft[Source](Source.none)(_ && _))
  def imgSrc(src: Source*): SourcePolicy     =
    SourcePolicy(SourcePolicyType.`img-src`, src.foldLeft[Source](Source.none)(_ && _))
  def mediaSrc(src: Source*): SourcePolicy   =
    SourcePolicy(SourcePolicyType.`media-src`, src.foldLeft[Source](Source.none)(_ && _))
  def frameSrc(src: Source*): SourcePolicy   =
    SourcePolicy(SourcePolicyType.`frame-src`, src.foldLeft[Source](Source.none)(_ && _))
  def fontSrc(src: Source*): SourcePolicy    =
    SourcePolicy(SourcePolicyType.`font-src`, src.foldLeft[Source](Source.none)(_ && _))
  def connectSrc(src: Source*): SourcePolicy =
    SourcePolicy(SourcePolicyType.`connect-src`, src.foldLeft[Source](Source.none)(_ && _))
  def objectSrc(src: Source*): SourcePolicy  =
    SourcePolicy(SourcePolicyType.`object-src`, src.foldLeft[Source](Source.none)(_ && _))

  def toContentSecurityPolicy(value: CharSequence): ContentSecurityPolicy =
    value.toString match {
      case "block-all-mixed-content"         => ContentSecurityPolicy.BlockAllMixedContent
      case s"plugin-types $types"            => ContentSecurityPolicy.PluginTypes(types)
      case s"referrer $referrer"             => ReferrerPolicy.fromString(referrer).map(ContentSecurityPolicy.Referrer).getOrElse(InvalidContentSecurityPolicy)
      case s"report-to $reportTo"            => ContentSecurityPolicy.ReportTo(reportTo)
      case s"report-uri $uri"                => Try(new URI(uri)).map(ContentSecurityPolicy.ReportUri).getOrElse(InvalidContentSecurityPolicy)
      case s"require-sri-for $requireSriFor" => RequireSriForValue.fromString(requireSriFor).map(ContentSecurityPolicy.RequireSriFor).getOrElse(InvalidContentSecurityPolicy)
      case s"trusted-types $trustedTypes"    => TrustedTypesValue.fromString(trustedTypes).map(ContentSecurityPolicy.TrustedTypes).getOrElse(InvalidContentSecurityPolicy)
      case s"sandbox $sandbox"               => SandboxValue.fromString(sandbox).map(ContentSecurityPolicy.Sandbox).getOrElse(InvalidContentSecurityPolicy)
      case s"upgrade-insecure-requests"      => ContentSecurityPolicy.UpgradeInsecureRequests
      case s"$policyType $policy"            => ContentSecurityPolicy.fromTypeAndPolicy(policyType, policy)
    }
  def fromContentSecurityPolicy(csp: ContentSecurityPolicy): String       =
    csp match {
      case ContentSecurityPolicy.BlockAllMixedContent    => "block-all-mixed-content"
      case ContentSecurityPolicy.PluginTypes(types)      => s"plugin-types $types"
      case ContentSecurityPolicy.Referrer(referrer)      => s"referrer ${ReferrerPolicy.toString(referrer)}"
      case ContentSecurityPolicy.ReportTo(reportTo)      => s"report-to $reportTo"
      case ContentSecurityPolicy.ReportUri(uri)          => s"report-uri $uri"
      case ContentSecurityPolicy.RequireSriFor(value)    => s"require-sri-for ${RequireSriForValue.fromRequireSriForValue(value)}"
      case ContentSecurityPolicy.TrustedTypes(value)     => s"trusted-types ${TrustedTypesValue.fromTrustedTypesValue(value)}"
      case ContentSecurityPolicy.Sandbox(value)          => s"sandbox ${SandboxValue.toString(value)}"
      case ContentSecurityPolicy.UpgradeInsecureRequests => "upgrade-insecure-requests"
      case SourcePolicy(policyType, policy)              => s"${SourcePolicyType.toString(policyType)} ${Source.toString(policy)}"
      case InvalidContentSecurityPolicy                  => ""
    }

  def fromTypeAndPolicy(policyType: String, policy: String): ContentSecurityPolicy =
    SourcePolicyType
      .fromString(policyType)
      .flatMap(policyType => Source.fromString(policy).map(SourcePolicy(policyType, _)))
      .getOrElse(InvalidContentSecurityPolicy)

}
