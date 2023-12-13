import scala.util._
import scala.sys.process._

import sbt._

object BuildUtil {
  def styled(in: Any): String =
    scala.Console.CYAN + in + scala.Console.RESET

  def prompt(projectName: String): String =
    gitPrompt
      .fold(projectPrompt(projectName)) { g =>
        s"\$g:\${projectPrompt(projectName)}"
      }

  private[this] def projectPrompt(projectName: String): String =
    s"sbt:\${styled(projectName)}"

  def projectName(state: State): String =
    Project
      .extract(state)
      .currentRef
      .project

  private[this] def gitPrompt: Option[String] =
    for {
      b <- branch.map(styled)
      h <- hash.map(styled)
    } yield s"git:\$b:\$h"

  private[this] def branch: Option[String] =
    run("git rev-parse --abbrev-ref HEAD")

  private[this] def hash: Option[String] =
    run("git rev-parse --short HEAD")

  private[this] def run(command: String): Option[String] =
    Try(
      command
        .split(" ")
        .toSeq
        .!!(noopProcessLogger)
        .trim
    ).toOption

  private[this] val noopProcessLogger: ProcessLogger =
    ProcessLogger(_ => (), _ => ())

  val Cctt: String =
    "compile->compile;test->test"
}
