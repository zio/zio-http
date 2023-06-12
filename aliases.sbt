addCommandAlias("fmt", "scalafmt; Test / scalafmt; sFix;")
addCommandAlias("fmtCheck", "scalafmtCheck; Test / scalafmtCheck; sFixCheck")
addCommandAlias(
  "sFix",
  "scalafix dependency:OrganizeImports@com.github.liancheng::organize-imports:0.6.0; Test / scalafix dependency:OrganizeImports@com.github.liancheng::organize-imports:0.6.0",
)
addCommandAlias(
  "sFixCheck",
  "scalafix --check dependency:OrganizeImports@com.github.liancheng::organize-imports:0.6.0; Test / scalafix --check dependency:OrganizeImports@com.github.liancheng::organize-imports:0.6.0",
)

onLoadMessage := {
  import scala.Console._

  def header(text: String): String  = s"${RED}$text${RESET}"
  def item(text: String): String    = s"${GREEN}> ${CYAN}$text${RESET}"
  def subItem(text: String): String = s"  ${YELLOW}> ${CYAN}$text${RESET}"

  s"""|
      |${header(" ____  ___    ___          _  _   _____   _____   ___")}
      |${header("|_  / |_ _|  / _ \\   ___  | || | |_   _| |_   _| | _ \\")}
      |${header(" / /   | |  | (_) | |___| | __ |   | |     | |   |  _/")}
      |${header("/___| |___|  \\___/        |_||_|   |_|     |_|   |_|")}
      |
      |Useful sbt tasks:
      |${item("fmt")}: Prepares source files using scalafix and scalafmt.
      |${item("sFix")}: Fixes sources files using scalafix.
      |${item("fmtCheck")}: Checks sources by applying both scalafix and scalafmt.
      |${item("sFixCheck")}: Checks sources by applying both scalafix.
      |
      |${subItem("Need help? Send us a message on discord: https://discord.gg/7KBzr3SRsh")}
      """.stripMargin
}
