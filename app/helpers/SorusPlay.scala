package helpers.sorus

import helpers.sorus.SorusDSL._
import play.api.Logging
import play.api.data.Form
import play.api.data.validation.ValidationError
import play.api.libs.json._
import play.api.mvc.Results._
import play.api.mvc._
import scalaz._

import java.security.SecureRandom
import scala.concurrent.Future
import scala.language.implicitConversions

case class FailWithResult(
  override val message: String,
  val result:           Result,
  override val cause:   Option[\/[Throwable, Fail]] = None
) extends Fail(message, cause) {
  override def withEx(fail: Fail): FailWithResult = new FailWithResult(this.message, result, Some(\/-(fail)))
}

trait FormatErrorResult[T <: Request[_]] {

  def failToResult(fail: Fail)(implicit request: Request[_]): Result = BadRequest(fail.userMessage())

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
trait SorusPlay[T <: Request[_]] extends Sorus with Logging { self: FormatErrorResult[T] =>

  private[SorusPlay] def fromForm[A](onError: Form[A] => Fail)(form: Form[A]): Step[A] =
    EitherT[Future, Fail, A](Future.successful(form.fold(onError andThen \/.left, \/.right)))

  implicit def formToStepOps[A](form: Form[A]): StepOps[A, Form[A]] = new StepOps[A, Form[A]] {
    override def orFailWith(failureHandler: (Form[A]) => Fail) = fromForm(failureHandler)(form)
  }

  implicit def resultStepToResult(step: Step[Result])(implicit request: T): Future[Result] = {
    step.run.map { s =>
      s.leftMap(addExceptionCode)
        .leftMap { f => log(f); f }
        .leftMap(transformFail2Result)
        .toEither
        .merge
    }(executionContext)
  }

  private def addExceptionCode(fail: Fail): Fail = {
    fail.getRootException()
      .map(_ => Fail(s"#${StringUtils.randomAlphanumericString(8)} ${fail.message}", fail.cause))
      .getOrElse(fail)
  }

  protected def log(fail: Fail): Unit = {
    fail.getRootException().foreach(ex => logger.error(fail.userMessage, ex))
  }

  private[this] def transformFail2Result(fail: Fail)(implicit request: T): Result = {
    fail match {
      case f: FailWithResult => f.result
      case f: Fail           => failToResult(f)
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

private object StringUtils {

  /**
   * Elegant random string generation in Scala -> http://www.bindschaedler.com/2012/04/07/elegant-random-string-generation-in-scala/
   */
  // Random Generator
  private[this] val random = new SecureRandom()

  // Generate a random string of length n from the given alphabet
  private[this] def randomString(alphabet: String)(n: Int): String = {
    (1 to n).map(_ => random.nextInt(alphabet.size)).map(alphabet).mkString
  }

  // Generate a random alphabnumeric string of length n
  def randomAlphanumericString(n: Int): String = {
    randomString("abcdefghijklmnopqrstuvwxyz0123456789")(n)
  }
}
