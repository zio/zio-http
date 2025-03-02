package zio.http.docs

import scala.language.reflectiveCalls

import zio._

import zio.config.generateDocs

object ConfigReference {
  private type ObjectWithConfig = Object { def config: Config[Any] }

  def referencePageFor(obj: ObjectWithConfig): String = {
    val name            = obj.getClass.getName.stripSuffix("$").replace("$", ".")
    val pageId          =
      "configs-" +
        name.toLowerCase
          .replace("zio.http.", "")
          .replaceAll("[ .]", "-")
    val yamlFrontMatter = s"---\nid: $pageId\ntitle: ${name}\n---\n"

    yamlFrontMatter +
      generateDocs(obj.config).toTable.toGithubFlavouredMarkdown
  }
}
