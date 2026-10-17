import ProjectInfo.*

import just.semver.SemVer
import extras.scala.io.syntax.color.*

ThisBuild / scalaVersion := props.ProjectScalaVersion
ThisBuild / organization := "io.kevinlee"
ThisBuild / organizationName := "Kevin's Code"
ThisBuild / crossScalaVersions := props.CrossScalaVersions

ThisBuild / developers := List(
  Developer(
    props.GitHubUsername,
    "Kevin Lee",
    "kevin.code@kevinlee.io",
    url(s"https://github.com/${props.GitHubUsername}"),
  )
)
ThisBuild / homepage := url(s"https://github.com/${props.GitHubUsername}/${props.RepoName}").some
ThisBuild / scmInfo :=
  ScmInfo(
    browseUrl = url(s"https://github.com/${props.GitHubUsername}/${props.RepoName}"),
    connection = s"scm:git:git@github.com:${props.GitHubUsername}/${props.RepoName}.git",
  ).some

ThisBuild / licenses := props.licenses

lazy val loggerFDocs = (project in file("."))
  .settings(
    name := prefixedProjectName(""),
    description := "Logger for F[_]",
  )
  .settings(noPublish)

lazy val docs = (project in file("docs-gen-tmp/docs"))
  .enablePlugins(MdocPlugin, DocusaurPlugin)
  .settings(
    name := "docs",
    mdocIn := file("docs/latest"),
    mdocOut := file("generated-docs/docs"),
    cleanFiles += ((ThisBuild / baseDirectory).value / "generated-docs" / "docs"),
    scalacOptions ~= (_.filter(opt => opt != "-Xfatal-warnings")),
    scalacOptions ++= Seq(
      "-Wconf:msg=unused value:s",
      "-Wconf:msg=never used:s",
    ),
    libraryDependencies ++= {
      val logger = sLog.value

      val latestTag =
        docsTools.getTheLatestTaggedVersion(props.ProjectOrgSlashRepo)(logger.info(_))

      val latestSbtLoggingVersion =
        docsTools.getTheLatestTaggedVersion(
          s"${props.ProjectOrgSlashRepo}-sbt-logging")(logger.error(_)
        )

      Seq(
        libs.effectieCore,
        libs.effectieSyntax,
        libs.effectieCatsEffect2,
        "io.kevinlee" %% "logger-f-core"        % latestTag,
        "io.kevinlee" %% "logger-f-cats"        % latestTag,
        "io.kevinlee" %% "logger-f-slf4j"       % latestTag,
        "io.kevinlee" %% "logger-f-log4j"       % latestTag,
        "io.kevinlee" %% "logger-f-log4s"       % latestTag,
        "io.kevinlee" %% "logger-f-sbt-logging" % latestSbtLoggingVersion,
        libs.slf4jApi,
        libs.logbackClassic,
      )
    },
    libraryDependencies := libraryDependenciesRemoveScala3Incompatible(
      scalaVersion.value,
      libraryDependencies.value,
    ),
    mdocVariables := {
      val logger = sLog.value

      val latestVersion = docsTools.getTheLatestTaggedVersion(props.ProjectOrgSlashRepo)(logger.error(_))

      val latestSbtLoggingVersion =
        docsTools.getTheLatestTaggedVersion(
          s"${props.ProjectOrgSlashRepo}-sbt-logging")(logger.error(_)
        )
      docsTools.createMdocVariables(
        latestVersion,
        "LOGGERF_SBT_LOGGING_VERSION" -> latestSbtLoggingVersion
      )
    },
    mdoc := {
      implicit val logger: Logger = sLog.value

      val latestVersion = docsTools.getTheLatestTaggedVersion(props.ProjectOrgSlashRepo)(logger.error(_))

      val envVarCi = sys.env.get("CI")
      val ciResult = s"""sys.env.get("CI")=${envVarCi}"""
      envVarCi match {
        case Some("true") =>
          logger.info(
            s">> ${ciResult.yellow} so ${"run".green} `${"writeLatestVersion".blue}` and `${"writeVersionsArchived".blue}`."
          )
          val websiteDir = docusaurDir.value
          docsTools.writeLatestVersion(websiteDir, latestVersion)
          docsTools.writeVersionsArchived(websiteDir, latestVersion)
        case Some(_) | None =>
          logger.info(
            s">> ${ciResult.yellow} so it will ${"not run".red} `${"writeLatestVersion".cyan}` and `${"writeVersionsArchived".cyan}`."
          )
      }
      mdoc.evaluated
    },
    docusaurDir := (ThisBuild / baseDirectory).value / "website",
    docusaurBuildDir := docusaurDir.value / "build",
  )
  .settings(noPublish)

lazy val docsV1 = (project in file("docs-gen-tmp/docs-v1"))
  .enablePlugins(MdocPlugin)
  .settings(
    name := "docsV1",
    mdocIn := file("docs/v1"),
    mdocOut := file("website/versioned_docs/version-v1/docs"),
    cleanFiles += ((ThisBuild / baseDirectory).value / "website" / "versioned_docs" / "version-v1"),
    scalacOptions ~= (_.filter(opt => opt != "-Xfatal-warnings")),
    scalacOptions ++= Seq(
      "-Wconf:msg=unused value:s",
      "-Wconf:msg=never used:s",
    ),
    libraryDependencies ++=
      Seq(
        "io.kevinlee" %% "logger-f-cats-effect"   % props.LoggerF1Version,
        "io.kevinlee" %% "logger-f-monix"         % props.LoggerF1Version,
        "io.kevinlee" %% "logger-f-scalaz-effect" % props.LoggerF1Version,
        "io.kevinlee" %% "logger-f-slf4j"         % props.LoggerF1Version,
        "io.kevinlee" %% "logger-f-log4j"         % props.LoggerF1Version,
        "io.kevinlee" %% "logger-f-log4s"         % props.LoggerF1Version,
        "io.kevinlee" %% "logger-f-sbt-logging"   % props.LoggerF1Version,
        libs.slf4jApi,
        libs.logbackClassic,
      ),
    libraryDependencies := libraryDependenciesRemoveScala3Incompatible(
      scalaVersion.value,
      libraryDependencies.value,
    ),
    mdocVariables := docsTools.createMdocVariables(props.LoggerF1Version),
  )
  .settings(noPublish)

addCommandAlias(
  "docsCleanAll",
  "; docs/clean; docsV1/clean",
)
addCommandAlias(
  "docsMdocAll",
  "; docs/mdoc; docsV1/mdoc",
)

// scalafmt: off
def prefixedProjectName(name: String) = s"${props.RepoName}${if (name.isEmpty) "" else s"-$name"}"
// scalafmt: on

def libraryDependenciesRemoveScala3Incompatible(
  scalaVersion: String,
  libraries: Seq[ModuleID],
): Seq[ModuleID] =
  (
    if (scalaVersion.startsWith("3."))
      libraries
        .filterNot(props.removeDottyIncompatible)
    else
      libraries
  )
