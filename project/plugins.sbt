resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"

resolvers += "Typesafe repository plugin" at "https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/"

resolvers += "sonatype-releases" at "https://oss.sonatype.org/content/repositories/releases/"

resolvers += "scalaz-bintray" at "https://de.bintray.com/scalaz/releases/"

resolvers += Classpaths.sbtPluginReleases

// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.8.19")

// code plugins

addSbtPlugin("org.scalastyle" %% "scalastyle-sbt-plugin" % "1.0.0" excludeAll (
  ExclusionRule(organization = "com.danieltrinh")
))

// addSbtPlugin("org.scalariform" % "sbt-scalariform" % "1.8.3")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.0")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.10.3")

// This is an issue with lib / sbt plugin who don't have the same version for scala-xml
// Binary compatility is "nearly" ok between scala-xml version
// And this impact only sbt coverage test in jenkins
// cf. https://github.com/sbt/sbt/issues/6997
ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)
