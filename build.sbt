name := "sorus"

version := "1.1.2"

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

publishMavenStyle := true

licenses += ("Apache2", url("http://www.apache.org/licenses/LICENSE-2.0.txt"))

homepage := Some(url("https://github.com/Driox/sorus"))

organization := "com.github.driox"

publishArtifact in Test := false;

publishTo := {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    Some("snapshots" at nexus + "content/repositories/snapshots")
  else
    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

pomIncludeRepository := { _ => false }

pomExtra := (
  <scm>
    <url>git@github.com:Driox/sorus.git</url>
    <connection>scm:git:git@github.com:Driox/sorus.git</connection>
  </scm>
  <developers>
    <developer>
      <id>acrovetto</id>
      <name>Adrien Crovetto</name>
      <url>https://github.com/Driox</url>
    </developer>
  </developers>
)

