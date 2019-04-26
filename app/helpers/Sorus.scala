/*
 * Copyright 2014
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package helpers.sorus

import org.slf4j.LoggerFactory

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }
import scalaz.syntax.either._
import scalaz.syntax.std.option._
import scalaz._
import play.api.libs.json._

/**
 * Inspiration :
 *  - http://fr.slideshare.net/normation/nrm-scala-iocorrectlymanagingerrorsinscalav13
 *  - https://github.com/Kanaka-io/play-monadic-actions
 */
object SorusDSL {

  private[this] val logger = LoggerFactory.getLogger(SorusDSL.getClass)
  private[SorusDSL] val executionContext: ExecutionContext = play.api.libs.concurrent.Execution.defaultContext

  type Step[A] = EitherT[Future, Fail, A]
  type JsErrorContent = Seq[(JsPath, Seq[JsonValidationError])]

  private[SorusDSL] def fromFuture[A](onFailure: Throwable => Fail)(future: Future[A])(implicit ec: ExecutionContext): Step[A] = {
    EitherT[Future, Fail, A](
      future.map(_.right).recover(log(onFailure(_).left)))
  }

  private[SorusDSL] def fromFOption[A](onNone: => Fail)(fOption: Future[Option[A]])(implicit ec: ExecutionContext): Step[A] = {
    EitherT[Future, Fail, A](
      fOption.map(_ \/> onNone).recover(log(onNone.withEx(_).left)))
  }

  private[SorusDSL] def fromFEither[A, B](onLeft: B => Fail)(fEither: Future[Either[B, A]])(implicit ec: ExecutionContext): Step[A] = {
    EitherT[Future, Fail, A] {
      fEither
        .map(_.fold(onLeft andThen \/.left, \/.right))
        .recover(log(x => (new Fail("Unexpected error in Future from FEither").withEx(x)).left))
    }
  }

  private[SorusDSL] def fromFDisjunction[A, B](onLeft: B => Fail)(fDisjunction: Future[B \/ A])(implicit ec: ExecutionContext): Step[A] =
    EitherT[Future, Fail, A] {
      fDisjunction
        .map(_.leftMap(onLeft))
        .recover(log(x => (new Fail("Unexpected error in Future from FDisjunction").withEx(x)).left))
    }

  private[SorusDSL] def fromOption[A](onNone: => Fail)(option: Option[A]): Step[A] =
    EitherT[Future, Fail, A](Future.successful(option \/> onNone))

  private[SorusDSL] def fromDisjunction[A, B](onLeft: B => Fail)(disjunction: B \/ A): Step[A] =
    EitherT[Future, Fail, A](Future.successful(disjunction.leftMap(onLeft)))

  private[SorusDSL] def fromEither[A, B](onLeft: B => Fail)(either: Either[B, A]): Step[A] =
    EitherT[Future, Fail, A](Future.successful(either.fold(onLeft andThen \/.left, \/.right)))

  private[SorusDSL] def fromBoolean(onFalse: => Fail)(boolean: Boolean): Step[Unit] =
    EitherT[Future, Fail, Unit](Future.successful(if (boolean) ().right else onFalse.left))

  private[SorusDSL] def fromTry[A](onFailure: Throwable => Fail)(tryValue: Try[A]): Step[A] =
    EitherT[Future, Fail, A](Future.successful(tryValue match {
      case Failure(t) => onFailure(t).left
      case Success(v) => v.right
    }))

  private[helpers] def fromJsResult[A](onJsError: JsErrorContent => Fail)(jsResult: JsResult[A]): Step[A] = {
    EitherT[Future, Fail, A](Future.successful(jsResult.fold(onJsError andThen \/.left, \/.right)))
  }

  // PartialFunction : http://blog.bruchez.name/2011/10/scala-partial-functions-without-phd.html
  private[this] def log[A](f: Throwable => Fail \/ A): PartialFunction[Throwable, Fail \/ A] = {
    case NonFatal(t) => {
      logger.error("Unexpected error in Future", t)
      f(t)
    }
  }

  trait StepOps[A, B] {
    def orFailWith(failureHandler: B => Fail): Step[A]
    def ?|(failureHandler: B => Fail): Step[A] = orFailWith(failureHandler)
    def ?|(failureThunk: => String): Step[A] = orFailWith {
      case err: Throwable => new Fail(failureThunk).withEx(err)
      case fail: Fail => new Fail(failureThunk).withEx(fail)
      case b => new Fail(b.toString).withEx(failureThunk)
    }
    def ?|(): Step[A] = orFailWith {
      case err: Throwable => new Fail("Unexpected exception").withEx(err)
      case fail: Fail => fail
      case b => new Fail(b.toString)
    }
    /**
     * This allow to compose Step of different types
     *
     * Check BasicExemple.scala to see it in action
     */
    def ?|>(failureThunk: => Future[Fail \/ A]): Step[A] = {
      val intermediary_result: Future[Fail \/ A] = orFailWith {
        case err: Throwable => new Fail("Unexpected exception").withEx(err)
        case fail: Fail => fail
        case b => new Fail(b.toString)
      }.run

      val result = intermediary_result.flatMap {
        case -\/(_) => failureThunk
        case x => Future.successful(x)
      }(executionContext)
      EitherT[Future, Fail, A](result)
    }
  }

  trait Sorus {

    import scala.language.implicitConversions

    protected val executionContext: ExecutionContext = SorusDSL.executionContext

    implicit val futureIsAFunctor = new Functor[Future] {
      override def map[A, B](fa: Future[A])(f: (A) => B) = fa.map(f)(executionContext)
    }

    implicit val futureIsAMonad = new Monad[Future] {
      override def point[A](a: => A) = Future(a)(executionContext)

      override def bind[A, B](fa: Future[A])(f: (A) => Future[B]) = fa.flatMap(f)(executionContext)
    }

    // This instance is needed to enable filtering/pattern-matching in for-comprehensions
    // It is acceptable for pattern-matching
    implicit val failIsAMonoid = new Monoid[Fail] {
      override def zero = new Fail("")

      override def append(f1: Fail, f2: => Fail) = throw new IllegalStateException("should not happen")
    }

    implicit def futureToStepOps[A](future: Future[A]): StepOps[A, Throwable] = new StepOps[A, Throwable] {
      override def orFailWith(failureHandler: (Throwable) => Fail) = fromFuture(failureHandler)(future)(executionContext)
    }

    implicit def fOptionToStepOps[A](fOption: Future[Option[A]]): StepOps[A, Unit] = new StepOps[A, Unit] {
      override def orFailWith(failureHandler: Unit => Fail) = fromFOption(failureHandler(()))(fOption)(executionContext)
    }

    implicit def fEitherToStepOps[A, B](fEither: Future[Either[B, A]]): StepOps[A, B] = new StepOps[A, B] {
      override def orFailWith(failureHandler: (B) => Fail) = fromFEither(failureHandler)(fEither)(executionContext)
    }

    implicit def fDisjunctionToStepOps[A, B](fDisjunction: Future[B \/ A]): StepOps[A, B] = new StepOps[A, B] {
      override def orFailWith(failureHandler: (B) => Fail) = fromFDisjunction(failureHandler)(fDisjunction)(executionContext)
    }

    implicit def optionToStepOps[A](option: Option[A]): StepOps[A, Unit] = new StepOps[A, Unit] {
      override def orFailWith(failureHandler: (Unit) => Fail) = fromOption(failureHandler(()))(option)
    }

    implicit def disjunctionToStepOps[A, B](disjunction: B \/ A): StepOps[A, B] = new StepOps[A, B] {
      override def orFailWith(failureHandler: (B) => Fail) = fromDisjunction(failureHandler)(disjunction)
    }

    implicit def eitherToStepOps[A, B](either: Either[B, A]): StepOps[A, B] = new StepOps[A, B] {
      override def orFailWith(failureHandler: (B) => Fail) = fromEither(failureHandler)(either)
    }

    implicit def booleanToStepOps(boolean: Boolean): StepOps[Unit, Unit] = new StepOps[Unit, Unit] {
      override def orFailWith(failureHandler: (Unit) => Fail) = fromBoolean(failureHandler(()))(boolean)
    }

    implicit def tryToStepOps[A](tryValue: Try[A]): StepOps[A, Throwable] = new StepOps[A, Throwable] {
      override def orFailWith(failureHandler: (Throwable) => Fail) = fromTry(failureHandler)(tryValue)
    }

    implicit def jsResultToStepOps[A](jsResult: JsResult[A]): StepOps[A, JsErrorContent] = new StepOps[A, JsErrorContent] {
      override def orFailWith(failureHandler: (JsErrorContent) => Fail) = fromJsResult(failureHandler)(jsResult)
    }

    implicit def stepToResult(step: Step[Fail]): Future[Fail] = step.run.map(_.toEither.merge)(executionContext)

    implicit def stepToEither[A](step: Step[A]): Future[Either[Fail, A]] = step.run.map(_.toEither)(executionContext)

    implicit def stepToDisjonction[A](step: Step[A]): Future[\/[Fail, A]] = step.run
  }
}
