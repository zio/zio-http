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

  def toContentLanguage(value: CharSequence): ContentLanguage =
    value.toString.toLowerCase match {
      case s"ar$_" => Arabic
      case s"bg$_" => Bulgarian
      case s"ca$_" => Catalan
      case s"zh$_" => Chinese
      case s"hr$_" => Croatian
      case s"cs$_" => Czech
      case s"da$_" => Danish
      case s"nl$_" => Dutch
      case s"en$_" => English
      case s"et$_" => Estonian
      case s"fi$_" => Finnish
      case s"fr$_" => French
      case s"de$_" => German
      case s"el$_" => Greek
      case s"he$_" => Hebrew
      case s"hi$_" => Hindi
      case s"hu$_" => Hungarian
      case s"is$_" => Icelandic
      case s"id$_" => Indonesian
      case s"it$_" => Italian
      case s"ja$_" => Japanese
      case s"ko$_" => Korean
      case s"lv$_" => Latvian
      case s"lt$_" => Lithuanian
      case s"no$_" => Norwegian
      case s"pl$_" => Polish
      case s"pt$_" => Portuguese
      case s"ro$_" => Romanian
      case s"ru$_" => Russian
      case s"sr$_" => Serbian
      case s"sk$_" => Slovak
      case s"sl$_" => Slovenian
      case s"es$_" => Spanish
      case s"sv$_" => Swedish
      case s"th$_" => Thai
      case s"tr$_" => Turkish
      case s"uk$_" => Ukrainian
      case s"vi$_" => Vietnamese
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
