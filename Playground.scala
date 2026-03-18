pimport cats._, implicits._
import cats.data._
import cats.effect._, std._, concurrent._
import cats.effect.implicits._
import cats.effect.testkit.TestControl.{executeEmbed => simul}
import cats.effect.unsafe.implicits.global
import fs2._
import fs2.concurrent._
import scala.concurrent.duration._
import scala.util.chaining._

object Ex extends IOApp.Simple {
  def run = IO.println("hello")
}
