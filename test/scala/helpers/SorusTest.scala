package helpers

import org.scalatest._

class SorusTest extends FlatSpec with Matchers {

  "Sorus" should "work from within a browser" in {
      val x = 1 + 1
      x should be(2)
    }
  
}
