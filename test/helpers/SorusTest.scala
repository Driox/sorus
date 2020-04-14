package helpers.sorus

import helpers.sorus.SorusDSL._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import scala.util.{ Failure, Success, Try }
import scala.language.postfixOps
import scalaz.syntax.either._
import scalaz._

class SorusTest extends AnyFlatSpec with Matchers with Sorus {

  // Just the fact this function compile allow us to compose various Future
  def composeVariousFuture(): Future[Fail \/ String] = {

    for {
      foo <- Future.successful("foo")      ?| "future.error"
      bar <- Future.successful(Some(foo))  ?| "future.option.error"
      baz <- Future.successful(Right(bar)) ?| "future.either.error"
      foz <- Future.successful(baz.right)  ?| "future.disjunction.error"
      faz <- Some(foz)                     ?| "option.error"
      far <- Right(faz)                    ?| "either.error"
      boo <- far.right                     ?| "disjunction.error"
      boz <- Try { boo }                   ?| "try.error"
      bor <- (boz == "foo")                ?| "boolean.error"
    } yield {
      boz
    }
  }

  "Sorus" should "handle failure in future" in {
    def underlyingMethod(): Future[Fail \/ String] = Future.failed(new Exception("Future in error"))

    val result: Future[Fail \/ String] = for {
      _ <- underlyingMethod() ?| ()
    } yield {
      "success"
    }

    Await.result(result, 10 seconds).toString shouldBe Fail("Unexpected error in Future from FDisjunction").withEx(new Exception("Future in error")).left.toString
  }

  "Sorus" should "properly promote Future[A] to Step[A]" in {
    val successfulFuture = Future.successful(42)
    Await.result((successfulFuture ?| "error").run, 10 seconds) shouldBe 42.right

    val ex = new NullPointerException
    val failedFuture = Future.failed[Int](ex)
    Await.result((failedFuture ?| "error").run, 10 seconds) shouldBe Fail("error", Some(-\/(ex))).left
  }

  "Sorus" should "properly promote Future[Option[A]] to Step[A]" in {
    val someFuture = Future.successful(Some(42))
    Await.result((someFuture ?| "error").run, 10 seconds) shouldBe 42.right

    val noneFuture = Future.successful[Option[Int]](None)
    Await.result((noneFuture ?| "error").run, 10 seconds) shouldBe Fail("error").left
  }

  "Sorus" should "properly promote Future[Either[B, A]] to Step[A]" in {
    val rightFuture = Future.successful[Either[String, Int]](Right(42))
    Await.result((rightFuture ?| "error").run, 10 seconds) shouldBe 42.right

    val leftFuture = Future.successful[Either[String, Int]](Left("foo"))
    val eitherT = leftFuture ?| "error"
    Await.result(eitherT.run, 10 seconds) shouldBe Fail("error", Some(\/-(Fail("foo", None)))).left
  }

  "Sorus" should "properly promote Future[B \\/ A] to Step[A]" in {
    val rightFuture = Future.successful(42.right[String])
    Await.result((rightFuture ?| "error").run, 10 seconds) shouldBe 42.right

    val leftFuture = Future.successful(-\/("foo"))
    val eitherT = leftFuture ?| "error"
    Await.result(eitherT.run, 10 seconds) shouldBe Fail("error", Some(\/-(Fail("foo", None)))).left
  }

  "Sorus" should "properly promote Option[A] to Step[A]" in {
    val some = Some(42)
    Await.result((some ?| "error").run, 10 seconds) shouldBe 42.right

    val none = None
    Await.result((none ?| "error").run, 10 seconds) shouldBe Fail("error").left
  }

  "Sorus" should "properly promote Either[A, B] to Step[A]" in {
    val right = Right(42)
    Await.result((right ?| "error").run, 10 seconds) shouldBe 42.right

    val left = Left("foo")
    val eitherT = left ?| "error"
    Await.result(eitherT.run, 10 seconds) shouldBe Fail("error", Some(\/-(Fail("foo", None)))).left
  }

  "Sorus" should "properly promote Boolean to Step[A]" in {
    Await.result((true ?| "error").run, 10 seconds) shouldBe ().right
    Await.result((false ?| "error").run, 10 seconds) shouldBe Fail("error").left
  }

  "Sorus" should "properly promote Try[A] to Step[A]" in {
    val success = Success(42)
    Await.result((success ?| "error").run, 10 seconds) shouldBe 42.right

    val ex = new Exception("foo")
    val failure = Failure(ex)
    val eitherT = failure ?| "error"
    Await.result(eitherT.run, 10 seconds) shouldBe Fail("error", Some(-\/(ex))).left
  }

  "Sorus" should "pass underlying message to Result" in {

    def underlyingMethod(): Future[Fail \/ String] = Future.successful(Fail("underlying error").left)

    val result: Future[Fail \/ String] = for {
      x <- underlyingMethod() ?| ()
    } yield {
      "success"
    }

    Await.result(result, 10 seconds) shouldBe Fail("underlying error").left
  }
}
