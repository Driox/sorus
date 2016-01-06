package helpers

import org.scalatest._

class FailTest extends FlatSpec with Matchers {

  "Fail " should "work from within a browser" in {
      val x = 1 + 1
      x should be(2)
    }

}
