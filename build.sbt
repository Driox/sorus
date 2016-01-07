name := """sorus"""

version := "1.0"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  "org.scalatest"          %% "scalatest"            % "2.2.4"    % "test" withSources(),
  "com.github.nscala-time" %% "nscala-time"          % "2.4.0"             withSources(),
  "org.scalaz"             %% "scalaz-core"          % "7.1.5"             withSources(),
  "joda-time"              %  "joda-time"            % "2.8.1"             withSources()
)

// sbt and compiler option
scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-unchecked",
    "-Xfatal-warnings",
    "-Xlint",
    "-Ywarn-dead-code",
    "-Ywarn-unused",
    "-Ywarn-unused-import",
    "-Ywarn-value-discard" //when non-Unit expression results are unused 
)
