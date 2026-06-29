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


lazy val docsTools = new {

  lazy val CmdRun = new {
    import sys.process._

    def runAndCapture(command: Seq[String]): (Int, String, String) = {
      val out      = new StringBuilder
      val err      = new StringBuilder
      val exitCode =
        Process(command).!(
          ProcessLogger(
            (o: String) => out.append(o).append('\n'),
            (e: String) => err.append(e).append('\n'),
          )
        )
      (exitCode, out.result().trim, err.result().trim)
    }

    def fail(prefix: String, step: String, command: Seq[String], out: String, err: String)(
      log: String => Unit
    ): Nothing = {
      val cmdString = command.mkString(" ")
      val details   =
        if (err.nonEmpty) err
        else if (out.nonEmpty) out
        else "(no output)"
      log(s">> [$prefix][$step] Command failed: `$cmdString`\n$details".red)
      throw new MessageOnlyException(s"$step failed: $cmdString\n$details")
    }
  }

  def getTheLatestTaggedVersion(orgSlashRepo: String)(logger: => String => Unit): String = {
    val (ghVersionExit, ghVersionOut, ghVersionErr) = CmdRun.runAndCapture(Seq("gh", "--version"))
    if (ghVersionExit != 0)
      CmdRun.fail(
        "getTheLatestTaggedVersion",
        "gh --version",
        Seq("gh", "--version"),
        ghVersionOut,
        ghVersionErr,
      )(logger)

    val (ghAuthExit, ghAuthOut, ghAuthErr) =
      CmdRun.runAndCapture(Seq("gh", "auth", "status", "-h", "github.com"))
    if (ghAuthExit != 0)
      CmdRun.fail(
        "getTheLatestTaggedVersion",
        "gh auth status",
        Seq("gh", "auth", "status", "-h", "github.com"),
        ghAuthOut,
        ghAuthErr,
      )(logger)

    val tagNameCmd =
      Seq("gh", "release", "view", "-R", orgSlashRepo, "--json", "tagName", "-q", ".tagName")

    val (tagExit, tagOut, tagErr) = CmdRun.runAndCapture(tagNameCmd)
    if (tagExit != 0)
      CmdRun.fail("getTheLatestTaggedVersion", "gh release view", tagNameCmd, tagOut, tagErr)(logger)

    val tagName = tagOut.trim
    if (tagName.isEmpty)
      CmdRun.fail(
        "getTheLatestTaggedVersion",
        "gh release view (empty tagName)",
        tagNameCmd,
        tagOut,
        tagErr,
      )(logger)

    if (!tagName.startsWith("v")) {
      logger(s">> [getTheLatestTaggedVersion] Expected tagName to start with 'v' but got: $tagName".red)
      throw new MessageOnlyException(s"Expected tagName to start with 'v' but got: $tagName")
    }

    val versionWithoutV = tagName.stripPrefix("v")
    SemVer.parse(versionWithoutV) match {
      case Right(v) => v.render
      case Left(parseError) =>
        logger(s">> [getTheLatestTaggedVersion] Invalid SemVer from tagName ($tagName): ${parseError.toString}".red)
        throw new MessageOnlyException(s"Invalid SemVer from tagName ($tagName): ${parseError.toString}")
    }
  }

  def writeLatestVersion(websiteDir: File, latestVersion: String)(implicit logger: Logger): Unit = {
    val latestVersionFile = websiteDir / "latestVersion.json"
    val latestVersionJson = raw"""{"version":"$latestVersion"}"""

    val websiteDirRelativePath =
      s"${latestVersionFile.getParentFile.getParentFile.getName.cyan}/${latestVersionFile.getParentFile.getName.yellow}"
    logger.info(
      s""">> Writing ${"the latest version".blue} to $websiteDirRelativePath/${latestVersionFile.getName.green}.
         |>> Content: ${latestVersionJson.blue}
         |""".stripMargin
    )
    IO.write(latestVersionFile, latestVersionJson)
  }

  def writeVersionsArchived(websiteDir: File, latestVersion: String)(implicit logger: Logger): Unit = {
    import sys.process._

    val (ghVersionExit, ghVersionOut, ghVersionErr) = CmdRun.runAndCapture(Seq("gh", "--version"))
    if (ghVersionExit != 0)
      CmdRun.fail("writeVersionsArchived", "gh --version", Seq("gh", "--version"), ghVersionOut, ghVersionErr)(
        logger.error(_)
      )

    val (ghAuthExit, ghAuthOut, ghAuthErr) =
      CmdRun.runAndCapture(Seq("gh", "auth", "status", "-h", "github.com"))
    if (ghAuthExit != 0)
      CmdRun.fail(
        "writeVersionsArchived",
        "gh auth status",
        Seq("gh", "auth", "status", "-h", "github.com"),
        ghAuthOut,
        ghAuthErr,
      )(logger.error(_))

    val repo = s"${props.GitHubUsername}/${props.CodeRepoName}"

    val ghTagsCmd =
      Seq(
        "gh",
        "api",
        "-H",
        "Accept: application/vnd.github+json",
        s"/repos/$repo/tags",
        "--paginate",
        "-q",
        ".[].name",
      )

    val (tagsExit, tagsOut, tagsErr) = CmdRun.runAndCapture(ghTagsCmd)
    if (tagsExit != 0)
      CmdRun.fail("writeVersionsArchived", "gh api tags", ghTagsCmd, tagsOut, tagsErr)(logger.error(_))

    val tags = tagsOut.trim
    if (tags.isEmpty)
      CmdRun.fail("writeVersionsArchived", "gh api tags (empty)", ghTagsCmd, tagsOut, tagsErr)(logger.error(_))

    val versions = tags
      .split("\n")
      .map(_.trim)
      .filter(t => t.nonEmpty && t.startsWith("v"))
      .map(_.stripPrefix("v"))
      .map(SemVer.parse)
      .collect { case Right(v) => v }
      .sorted(Ordering[SemVer].reverse)
      .map(_.render)
      .filter(_ != latestVersion)

    val versionsArchivedFile = websiteDir / "src" / "pages" / "versionsArchived.json"

    val versionsInJson = versions
      .map { v =>
        raw"""  {
             |    "name": "$v",
             |    "label": "$v"
             |  }""".stripMargin
      }
      .mkString("[\n", ",\n", "\n]")

    IO.write(versionsArchivedFile, versionsInJson)
  }

  def createMdocVariables(version: String, additionalVersions: (String, String)*): Map[String, String] = {
    val versionForDoc = version
    Map(
      "VERSION"                               -> versionForDoc,
      "SUPPORTED_SCALA_VERSIONS"              -> {
        val versions = props
          .CrossScalaVersions
          .map(CrossVersion.binaryScalaVersion)
          .map(binVer => s"`$binVer`")
        if (versions.length > 1)
          s"${versions.init.mkString(", ")} and ${versions.last}"
        else
          versions.mkString
      },
      "SUPPORTED_SCALA_VERSIONS_FOR_SCALA_JS" -> {
        val versions = props
          .CrossScalaVersionsForScalaJsAndNative
          .map(CrossVersion.binaryScalaVersion)
          .map(binVer => s"`$binVer`")
        if (versions.length > 1)
          s"${versions.init.mkString(", ")} and ${versions.last}"
        else
          versions.mkString
      },
    ) ++ additionalVersions.toMap
  }

}


addCommandAlias(
  "docsCleanAll",
  "; docs/clean; docsV1/clean",
)
addCommandAlias(
  "docsMdocAll",
  "; docs/mdoc; docsV1/mdoc",
)


lazy val props =
  new {

    private val GitHubRepo = findRepoOrgAndName

    val GitHubUsername = GitHubRepo.fold("kevin-lee")(_.orgToString)
    val RepoName       = GitHubRepo.fold("logger-f-docs")(_.nameToString)

    val CodeRepoName = RepoName.stripSuffix("-docs")

    val ProjectOrgSlashRepo = s"$GitHubUsername/$CodeRepoName"

    final val Scala3Versions = List("3.3.5")
    final val Scala2Versions = List("2.13.18", "2.12.18")

//    final val ProjectScalaVersion = Scala3Versions.head
    final val ProjectScalaVersion = Scala2Versions.head

    lazy val licenses = List("MIT" -> url("http://opensource.org/licenses/MIT"))

    val removeDottyIncompatible: ModuleID => Boolean =
      m =>
        m.name == "ammonite" ||
          m.name == "kind-projector" ||
          m.name == "better-monadic-for" ||
          m.name == "mdoc"

    val CrossScalaVersions = (Scala3Versions ++ Scala2Versions).distinct

    val CrossScalaVersionsForScalaJsAndNative = CrossScalaVersions.filterNot(_.startsWith("2.12"))

    final val IncludeTest = "compile->compile;test->test"

    val HedgehogVersion = "0.13.0"

    val HedgehogExtraVersion = "0.15.0"

    val EffectieVersion = "2.3.0"

    final val CatsVersion = "2.12.0"

    val catsEffect3Version          = "3.3.14"
    val catsEffect3ForNativeVersion = "3.7.0-RC1"

    val Monix3Version = "3.4.0"

    val Doobie1Version = "1.0.0-RC10"

    final val LoggerF1Version = "1.20.0"

    final val ExtrasVersion = "0.49.0"

    val Slf4JVersion       = "2.0.12"
    val Slf4JLatestVersion = "2.0.17"

    val LogbackVersion       = "1.5.0"
    val LogbackLatestVersion = "1.5.18"

    final val Log4sVersion = "1.10.0"

    final val Log4JVersion = "2.19.0"

    val LogbackScalaInteropVersion       = "1.0.0"
    val LogbackScalaInteropLatestVersion = "1.17.0"

    val OrphanVersion = "0.5.0"

    val MunitVersion = "0.7.29"

    val MunitCatsEffectVersion = "1.0.7"

    val ScalaJsMacrotaskExecutorVersion = "1.1.1"

    val ScalaJavaTimeVersion = "2.6.0"

  }

lazy val libs =
  new {

    lazy val slf4jApi: ModuleID       = "org.slf4j" % "slf4j-api" % props.Slf4JVersion
    lazy val slf4jApiLatest: ModuleID = "org.slf4j" % "slf4j-api" % props.Slf4JLatestVersion

    lazy val logbackClassic: ModuleID       = "ch.qos.logback" % "logback-classic" % props.LogbackVersion
    lazy val logbackClassicLatest: ModuleID = "ch.qos.logback" % "logback-classic" % props.LogbackLatestVersion

    lazy val log4sLib = "org.log4s" %% "log4s" % props.Log4sVersion

    lazy val log4jApi  = "org.apache.logging.log4j" % "log4j-api"  % props.Log4JVersion
    lazy val log4jCore = "org.apache.logging.log4j" % "log4j-core" % props.Log4JVersion

    def sbtLoggingLib(sbtLoggingVersion: String) = "org.scala-sbt" %% "util-logging" % sbtLoggingVersion

    lazy val cats = "org.typelevel" %% "cats-core" % props.CatsVersion

    def libCatsEffect(catsEffectVersion: String) = "org.typelevel" %% "cats-effect" % catsEffectVersion

    lazy val monix3Execution = "io.monix" %% "monix-execution" % props.Monix3Version

    lazy val effectieCore        = "io.kevinlee" %% "effectie-core" % props.EffectieVersion
    lazy val effectieSyntax      = "io.kevinlee" %% "effectie-syntax" % props.EffectieVersion
    lazy val effectieCats        = "io.kevinlee" %% "effectie-cats" % props.EffectieVersion
    lazy val effectieCatsEffect2 = "io.kevinlee" %% "effectie-cats-effect2" % props.EffectieVersion
    lazy val effectieCatsEffect3 = "io.kevinlee" %% "effectie-cats-effect3" % props.EffectieVersion

    lazy val effectieMonix = "io.kevinlee" %% "effectie-monix3" % props.EffectieVersion

    lazy val logbackScalaInterop       = "io.kevinlee" % "logback-scala-interop" % props.LogbackScalaInteropVersion
    lazy val logbackScalaInteropLatest =
      "io.kevinlee" % "logback-scala-interop" % props.LogbackScalaInteropLatestVersion

    lazy val orphanCats = "io.kevinlee" %% "orphan-cats" % props.OrphanVersion

    lazy val doobieFree = "org.tpolecat" %% "doobie-free" % props.Doobie1Version

    lazy val tests = new {

      lazy val monix = "io.monix" %% "monix" % props.Monix3Version % Test

      lazy val effectieCatsEffect3 =
        "io.kevinlee" %% "effectie-cats-effect3" % props.EffectieVersion % Test

      lazy val effectieMonix3 = "io.kevinlee" %% "effectie-monix3" % props.EffectieVersion % Test

      lazy val hedgehogLibs =
        List(
          "qa.hedgehog" %% "hedgehog-core"   % props.HedgehogVersion % Test,
          "qa.hedgehog" %% "hedgehog-runner" % props.HedgehogVersion % Test,
          "qa.hedgehog" %% "hedgehog-sbt"    % props.HedgehogVersion % Test,
        )

      lazy val hedgehogExtra =
        List(
          "io.kevinlee" %% "hedgehog-extra-core" % props.HedgehogExtraVersion
        ).map(_ % Test)

      lazy val extrasCats = "io.kevinlee" %% "extras-cats" % props.ExtrasVersion % Test

      lazy val extrasTestingTools = "io.kevinlee" %% "extras-testing-tools" % props.ExtrasVersion % Test

      lazy val extrasHedgehogCatsEffect3 =
        "io.kevinlee" %% "extras-hedgehog-ce3" % props.ExtrasVersion % Test

      lazy val extrasConcurrent        = "io.kevinlee" %% "extras-concurrent" % props.ExtrasVersion % Test
      lazy val extrasConcurrentTesting = "io.kevinlee" %% "extras-concurrent-testing" % props.ExtrasVersion % Test

      lazy val scalaJsMacrotaskExecutor =
        "org.scala-js" %% "scala-js-macrotask-executor" % props.ScalaJsMacrotaskExecutorVersion % Test

      lazy val munit = "org.scalameta" %% "munit" % props.MunitVersion % Test

      lazy val munitCatsEffect3 =
        "org.typelevel" %% "munit-cats-effect-3" % props.MunitCatsEffectVersion % Test
    }

  }

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
