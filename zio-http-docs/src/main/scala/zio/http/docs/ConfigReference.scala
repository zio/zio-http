package zio.http.docs

import scala.language.reflectiveCalls

import zio._

import zio.config.generateDocs

object ConfigReference {
  private type ObjectWithConfig = Object { def config: Config[Any] }

  /**
   * Renders the configuration reference table for a config object. The result
   * is spliced into a page that already declares its own front matter, so none
   * is emitted here.
   */
  def referencePageFor(obj: ObjectWithConfig): String =
    numberRepeatedHeadings(generateDocs(obj.config).toTable.toGithubFlavouredMarkdown)

  /**
   * zio-config emits one "Field Descriptions" heading per nested config node
   * and links to them using the `-1`, `-2`, ... suffixes that GitHub and
   * Docusaurus derive from repeated heading text. Those links resolve
   * correctly, but the headings are indistinguishable in the table of contents,
   * and mdoc's link checker — which does not replicate that de-duplication —
   * reports each one as broken.
   *
   * Numbering the repeats leaves the generated anchors byte-for-byte identical,
   * since "Field Descriptions (1)" slugifies to `field-descriptions-1`, while
   * making every heading unique.
   */
  private def numberRepeatedHeadings(markdown: String): String = {
    val heading = """^(#{2,6})\s+(.*\S)\s*$""".r
    val seen    = scala.collection.mutable.Map.empty[String, Int]

    val numbered = markdown.linesIterator.map {
      case line @ heading(hashes, text) =>
        val occurrence = seen.getOrElse(text, 0)
        seen.update(text, occurrence + 1)
        if (occurrence == 0) line else s"$hashes $text ($occurrence)"
      case line                         => line
    }

    numbered.mkString("\n") + (if (markdown.endsWith("\n")) "\n" else "")
  }
}
