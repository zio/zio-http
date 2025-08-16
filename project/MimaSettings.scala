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
        exclude[IncompatibleMethTypeProblem]("zio.http.Middleware.addHeader"),
        exclude[IncompatibleMethTypeProblem]("zio.http.HandlerAspect.addHeader"),
        ProblemFilters.exclude[ReversedMissingMethodProblem]("zio.http.Server.installInternal"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("zio.http.Server.serve"),
        ProblemFilters.exclude[IncompatibleMethTypeProblem]("zio.http.Server.serve"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("zio.http.codec.CodecConfig.apply$default$1"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("zio.http.codec.CodecConfig.<init>$default$1"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("zio.http.codec.CodecConfig.this"),
        ProblemFilters.exclude[IncompatibleResultTypeProblem]("zio.http.codec.CodecConfig.copy$default$1"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("zio.http.codec.CodecConfig.copy"),
        ProblemFilters.exclude[MissingTypesProblem]("zio.http.netty.NettyBody$"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("zio.http.netty.NettyBody.fromCharSequence$default$2"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("zio.http.netty.CachedDateHeader.<init>$default$2"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("zio.http.netty.CachedDateHeader.this"),
        ProblemFilters.exclude[DirectMissingMethodProblem]("zio.http.netty.CachedDateHeader.<init>$default$2"),
        ProblemFilters.exclude[MissingClassProblem]("zio.http.netty.NettyDateEncoding"),
        ProblemFilters.exclude[MissingClassProblem]("zio.http.netty.NettyDateEncoding$"),
        ProblemFilters.exclude[MissingClassProblem]("zio.http.netty.NettyHeaderEncoding"),
        ProblemFilters.exclude[MissingClassProblem]("zio.http.netty.NettyHeaderEncoding$"),
      ),
      mimaFailOnProblem := failOnProblem,
    )
}
