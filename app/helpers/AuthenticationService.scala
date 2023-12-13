package helpers.sorus

import scala.concurrent.duration._

class AuthenticationService() {

  private[this] def retry_count_cache_key(xxx: String) =
    s"some_very_very_very_very_very_very_very_very_very_very_very_long_string_$xxx"
  private[this] def retry_cache_key(xxx: String)       = s"some_short_string$xxx"
  private[this] val retry_timing_in_seconds            = 30 seconds
  val x                                                =
    "some_very_very_very_very_very_very_very_very_very_very_very_very_very_very_very_very_very_very_veryvery_very_very_very_very_very_very_very_very_very_very_very_very_very_very_very_very_very_very_long_string"
  val xx                                               = "àç098098"
  val xxx                                              = "àç098098"
  val xxxxx                                            = "àç098098"

  def coucou(): Int = {
    println(s"""
      ${retry_count_cache_key("123")}
      ${retry_cache_key("123")}
      $retry_timing_in_seconds
    """)

    12
  }
}
