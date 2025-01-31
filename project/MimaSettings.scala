import com.typesafe.tools.mima.core.*
import com.typesafe.tools.mima.core.ProblemFilters.*
import com.typesafe.tools.mima.plugin.MimaKeys.*
import sbt.{Def, *}
import sbt.Keys.{name, organization}
import sbtdynver.DynVerPlugin.autoImport.*

object MimaSettings {
  def mimaSettings(failOnProblem: Boolean): Seq[Def.Setting[?]] =
    Seq(
      mimaPreviousArtifacts ++= previousStableVersion.value.map(organization.value %% name.value % _).toSet,
      mimaBinaryIssueFilters ++= Seq(
        exclude[Problem]("zio.http.internal.*"),
        exclude[Problem]("zio.http.codec.internal.*"),
        exclude[Problem]("zio.http.codec.HttpCodec$Query$QueryType$Record$"),
        exclude[Problem]("zio.http.codec.HttpCodec$Query$QueryType$Record"),
        exclude[Problem]("zio.http.codec.HttpCodec$Query$QueryType$Primitive$"),
        exclude[Problem]("zio.http.codec.HttpCodec$Query$QueryType$Primitive"),
        exclude[Problem]("zio.http.codec.HttpCodec$Query$QueryType$Collection$"),
        exclude[Problem]("zio.http.codec.HttpCodec$Query$QueryType$Collection"),
        exclude[Problem]("zio.http.codec.HttpCodec$Query$QueryType$"),
        exclude[Problem]("zio.http.codec.HttpCodec$Query$QueryType"),
        exclude[Problem]("zio.http.endpoint.openapi.OpenAPIGen#AtomizedMetaCodecs.apply"),
        exclude[Problem]("zio.http.endpoint.openapi.OpenAPIGen#AtomizedMetaCodecs.this"),
        exclude[Problem]("zio.http.endpoint.openapi.OpenAPIGen#AtomizedMetaCodecs.copy"),
      ),
      mimaFailOnProblem := failOnProblem
    )
}
