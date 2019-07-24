package helpers.sorus

import helpers.sorus.SorusDSL._
import play.api.mvc._
import play.api.mvc.Results._
import play.api.data.Form

import scala.concurrent.Future
import play.api.data.validation.ValidationError
import play.api.libs.json._

import scalaz._
import scala.language.implicitConversions

case class FailWithResult(
  override val message: String,
  val result: Result,
  override val cause: Option[\/[Throwable, Fail]] = None) extends Fail(message, cause) {

}

trait FormatErrorResult[T <: Request[_]] {

  def failToResult(fail: Fail): Result = BadRequest(fail.userMessage())

  def formatJsonValidationErrorToResult(errors: Seq[(JsPath, Seq[ValidationError])]): Result = {
    val translated_error = errors.map(a_path => (a_path._1, a_path._2.map(err => err.message)))
    BadRequest(toJson(translated_error))
  }

  private[this] def toJson(errors: Seq[(JsPath, Seq[String])]): JsObject = {
    errors.foldLeft(Json.obj()) { (obj, error) =>
      obj ++ Json.obj(error._1.toJsonString -> error._2.reduce((s1, s2) => s"$s1, $s2"))
    }
  }
}

/**
 * This trait allow you to use the ?| operator in your Play controller and get a Future[Result] instead of a Future[Fail \/ T]
 *
 * Usage :
 *
 * class MyController extends Controller with FormatErrorResult with SorusPlay
 *
 * You may override default serialization of Fail into Error by extending FormatErrorResult.
 */
trait SorusPlay[T <: Request[_]] extends Sorus { self: FormatErrorResult[T] =>

  private[SorusPlay] def fromForm[A](onError: Form[A] => Fail)(form: Form[A]): Step[A] =
    EitherT[Future, Fail, A](Future.successful(form.fold(onError andThen \/.left, \/.right)))

  implicit def formToStepOps[A](form: Form[A]): StepOps[A, Form[A]] = new StepOps[A, Form[A]] {
    override def orFailWith(failureHandler: (Form[A]) => Fail) = fromForm(failureHandler)(form)
  }

  implicit def resultStepToResult(step: Step[Result]): Future[Result] = {
    step.run.map { s =>
      s.leftMap(f => transformFail2Result(f)).toEither.merge
    }(executionContext)
  }

  private[this] def transformFail2Result(fail: Fail): Result = {
    fail match {
      case f: FailWithResult => f.result
      case f: Fail => failToResult(f)
    }
  }

  /**
   * Allow this kind of mapping with result on the left
   * ?|> don't stop the flow but replace the result if the first one fail
   *
   * criteria <- eventSearchForm.bindFromRequest ?| (formWithErrors => Ok(views.html.analyzer.index(formWithErrors)))
   */
  implicit def result2Fail(result: Result): FailWithResult = {
    FailWithResult("result from ctrl", result)
  }

  /**
   * Avoid to write mapping like that
   *
   * consumer <- consumerService.loadByKey(key) ?| (err:Unit => NotFound)
   */
  implicit def result2FailFromUnit(result: Result): Unit => FailWithResult = {
    _: Unit => FailWithResult("result from ctrl", result)
  }

  /**
   * Allow this kind of mapping
   *
   * consumers <- consumerService.search(q) ?| NotFound
   *
   * without the need to do
   *
   * consumers <- consumerService.search(q) ?| (err:Throwable => NotFound(err.getMessage).withEx(err))
   *
   * For now you can't override the body of the response
   *
   * consumers <- consumerService.search(q) ?| NotFound("this will be erased")
   */
  implicit def result2FailFunction(result: Result): Throwable => FailWithResult = { t: Throwable =>
    {
      val rez_with_body = Status(result.header.status)(t.getMessage)
      FailWithResult("result from ctrl", rez_with_body, Some(-\/(t)))
    }
  }
}
