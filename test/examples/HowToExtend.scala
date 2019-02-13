package helpers.sorus

import helpers.sorus.SorusDSL._
import org.slf4j.LoggerFactory
import scalaz.syntax.either._
import scalaz._

import scala.concurrent.Future
import scala.util.control.NonFatal

trait SorusEnhanced extends Sorus {

  private[this] val logger = LoggerFactory.getLogger(SorusDSL.getClass)

  implicit def fOptionToStepOps2[A](fOption: Future[A]): StepOpsEnhanced[A, Unit] = new StepOpsEnhanced[A, Unit] {
    override def orFailWith(failureHandler: Unit => Fail) = EitherT[Future, Fail, A](
      fOption.map(_.right)(executionContext).recover(log(failureHandler(()).withEx(_).left))(executionContext)
    )
  }
}

trait StepOpsEnhanced[A, B] {
  def orFailWith(failureHandler: B => Fail): Step[A]

  def |>(failureHandler: B => Fail): Step[A] = orFailWith(failureHandler)
  def |>(failureThunk: => String): Step[A] = orFailWith {
      case err: Throwable => new Fail(failureThunk).withEx(err)
      case fail: Fail     => new Fail(failureThunk).withEx(fail)
      case b              => new Fail(b.toString).withEx(failureThunk)
    }
}
