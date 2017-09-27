package helpers.sorus

import org.scalatest._
import scalaz._

class FailTest extends FlatSpec with Matchers {

  "Fail " should "compose with String error" in {
    val fail = Fail("Initial fail")
    fail.withEx("Second fail message") should be(Fail("Second fail message", Some(\/-(Fail("Initial fail")))))
  }

  "Fail " should "compose with Throwable" in {
    val fail = Fail("Initial fail")
    val e = new Exception("exception msg")
    fail.withEx(e) should be(Fail("Initial fail", Some(-\/(e))))
  }

  "Fail " should "compose with Fail" in {
    val fail = Fail("Initial fail")
    val secondFail = Fail("second fail")
    fail.withEx(secondFail) should be(Fail("Initial fail", Some(\/-(secondFail))))
  }

  "Fail " should "accumulate error message" in {
    val fail = Fail("Initial fail")
    fail.messages() should be(NonEmptyList("Initial fail"))

    val secondFail = fail.withEx(Fail("second fail"))
    secondFail.messages().size should be(2)

    val thirdFail = secondFail.withEx(new Exception("a msg"))
    thirdFail.messages().size should be(2)
  }

  "Fail " should "allow to retrive original exception" in {
    val fail = Fail("Initial fail")
    val ex = new Exception("a msg")
    val failWithEx = fail.withEx(ex).withEx("second msg").withEx("third msg")
    
    fail.getRootException should be(None)
    failWithEx.getRootException should be(Some(ex))
  }

}
