inThisBuild(
  List(
    semanticdbEnabled := true,                       // enable SemanticDB
    semanticdbVersion := scalafixSemanticdb.revision // use Scalafix compatible version
  )
)
ThisBuild / scalafixDependencies ++= Seq(
  "com.github.liancheng" %% "organize-imports" % "0.6.0"
)

ThisBuild / scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(scalaVersion.value)
