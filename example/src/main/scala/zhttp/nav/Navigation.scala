package zhttp.nav

case class Navigation(linkText: String, link: String, icon: Option[String], subNav: Seq[Navigation] = Seq.empty)
