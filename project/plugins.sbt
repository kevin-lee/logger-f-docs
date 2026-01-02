logLevel := sbt.Level.Warn

addDependencyTreePlugin

addSbtPlugin("org.scalameta"   % "sbt-mdoc"        % "2.8.2")
addSbtPlugin("io.kevinlee"     % "sbt-docusaur"    % "0.20.0")

addSbtPlugin("org.scala-js"       % "sbt-scalajs"              % "1.18.2")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.3.2")

addSbtPlugin("org.scala-native"   % "sbt-scala-native"              % "0.5.9")
addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.3.2")

addSbtPlugin("org.scalameta" % "sbt-native-image" % "0.3.2")

val sbtDevOopsVersion = "3.3.2"
addSbtPlugin("io.kevinlee" % "sbt-devoops-scala"     % sbtDevOopsVersion)
addSbtPlugin("io.kevinlee" % "sbt-devoops-sbt-extra" % sbtDevOopsVersion)
addSbtPlugin("io.kevinlee" % "sbt-devoops-github"    % sbtDevOopsVersion)
addSbtPlugin("io.kevinlee" % "sbt-devoops-starter"   % sbtDevOopsVersion)
