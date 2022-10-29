package zio.http.model.headers.values

sealed trait ContentLanguage

object ContentLanguage {
  case object Arabic     extends ContentLanguage
  case object Bulgarian  extends ContentLanguage
  case object Catalan    extends ContentLanguage
  case object Chinese    extends ContentLanguage
  case object Croatian   extends ContentLanguage
  case object Czech      extends ContentLanguage
  case object Danish     extends ContentLanguage
  case object Dutch      extends ContentLanguage
  case object English    extends ContentLanguage
  case object Estonian   extends ContentLanguage
  case object Finnish    extends ContentLanguage
  case object French     extends ContentLanguage
  case object German     extends ContentLanguage
  case object Greek      extends ContentLanguage
  case object Hebrew     extends ContentLanguage
  case object Hindi      extends ContentLanguage
  case object Hungarian  extends ContentLanguage
  case object Icelandic  extends ContentLanguage
  case object Indonesian extends ContentLanguage
  case object Italian    extends ContentLanguage
  case object Japanese   extends ContentLanguage
  case object Korean     extends ContentLanguage
  case object Latvian    extends ContentLanguage
  case object Lithuanian extends ContentLanguage
  case object Norwegian  extends ContentLanguage
  case object Polish     extends ContentLanguage
  case object Portuguese extends ContentLanguage
  case object Romanian   extends ContentLanguage
  case object Russian    extends ContentLanguage
  case object Serbian    extends ContentLanguage
  case object Slovak     extends ContentLanguage
  case object Slovenian  extends ContentLanguage
  case object Spanish    extends ContentLanguage
  case object Swedish    extends ContentLanguage
  case object Thai       extends ContentLanguage
  case object Turkish    extends ContentLanguage
  case object Ukrainian  extends ContentLanguage
  case object Vietnamese extends ContentLanguage

  private val ArRegex = "ar.*".r
  private val BgRegex = "bg.*".r
  private val CaRegex = "ca.*".r
  private val ZhRegex = "zh.*".r
  private val HrRegex = "hr.*".r
  private val CsRegex = "cs.*".r
  private val DaRegex = "da.*".r
  private val NlRegex = "nl.*".r
  private val EnRegex = "en.*".r
  private val EtRegex = "et.*".r
  private val FiRegex = "fi.*".r
  private val FrRegex = "fr.*".r
  private val DeRegex = "de.*".r
  private val ElRegex = "el.*".r
  private val HeRegex = "he.*".r
  private val HiRegex = "hi.*".r
  private val HuRegex = "hu.*".r
  private val IsRegex = "is.*".r
  private val IdRegex = "id.*".r
  private val ItRegex = "it.*".r
  private val JaRegex = "ja.*".r
  private val KoRegex = "ko.*".r
  private val LvRegex = "lv.*".r
  private val LtRegex = "lt.*".r
  private val NbRegex = "nb.*".r
  private val PlRegex = "pl.*".r
  private val PtRegex = "pt.*".r
  private val RoRegex = "ro.*".r
  private val RuRegex = "ru.*".r
  private val SrRegex = "sr.*".r
  private val SkRegex = "sk.*".r
  private val SlRegex = "sl.*".r
  private val EsRegex = "es.*".r
  private val SvRegex = "sv.*".r
  private val ThRegex = "th.*".r
  private val TrRegex = "tr.*".r
  private val UkRegex = "uk.*".r
  private val ViRegex = "vi.*".r

  def toContentLanguage(value: CharSequence): ContentLanguage =
    value.toString.toLowerCase match {
      case ArRegex() => Arabic
      case BgRegex() => Bulgarian
      case CaRegex() => Catalan
      case ZhRegex() => Chinese
      case HrRegex() => Croatian
      case CsRegex() => Czech
      case DaRegex() => Danish
      case NlRegex() => Dutch
      case EnRegex() => English
      case EtRegex() => Estonian
      case FiRegex() => Finnish
      case FrRegex() => French
      case DeRegex() => German
      case ElRegex() => Greek
      case HeRegex() => Hebrew
      case HiRegex() => Hindi
      case HuRegex() => Hungarian
      case IsRegex() => Icelandic
      case IdRegex() => Indonesian
      case ItRegex() => Italian
      case JaRegex() => Japanese
      case KoRegex() => Korean
      case LvRegex() => Latvian
      case LtRegex() => Lithuanian
      case NbRegex() => Norwegian
      case PlRegex() => Polish
      case PtRegex() => Portuguese
      case RoRegex() => Romanian
      case RuRegex() => Russian
      case SrRegex() => Serbian
      case SkRegex() => Slovak
      case SlRegex() => Slovenian
      case EsRegex() => Spanish
      case SvRegex() => Swedish
      case ThRegex() => Thai
      case TrRegex() => Turkish
      case UkRegex() => Ukrainian
      case ViRegex() => Vietnamese
    }

  def fromContentLanguage(contentLanguage: ContentLanguage): String =
    contentLanguage match {
      case Arabic     => "ar"
      case Bulgarian  => "bg"
      case Catalan    => "ca"
      case Chinese    => "zh"
      case Croatian   => "hr"
      case Czech      => "cs"
      case Danish     => "da"
      case Dutch      => "nl"
      case English    => "en"
      case Estonian   => "et"
      case Finnish    => "fi"
      case French     => "fr"
      case German     => "de"
      case Greek      => "el"
      case Hebrew     => "he"
      case Hindi      => "hi"
      case Hungarian  => "hu"
      case Icelandic  => "is"
      case Indonesian => "id"
      case Italian    => "it"
      case Japanese   => "ja"
      case Korean     => "ko"
      case Latvian    => "lv"
      case Lithuanian => "lt"
      case Norwegian  => "no"
      case Polish     => "pl"
      case Portuguese => "pt"
      case Romanian   => "ro"
      case Russian    => "ru"
      case Serbian    => "sr"
      case Slovak     => "sk"
      case Slovenian  => "sl"
      case Spanish    => "es"
      case Swedish    => "sv"
      case Thai       => "th"
      case Turkish    => "tr"
      case Ukrainian  => "uk"
      case Vietnamese => "vi"
    }
}
