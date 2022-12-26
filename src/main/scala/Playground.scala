import cats._, implicits._
import cats.data._
import cats.effect._, concurrent._
import cats.effect.implicits._
import cats.effect.unsafe.implicits.global
import fs2._
import fs2.concurrent._
import scala.concurrent.duration._
import scala.util.chaining._

object Playground {

  implicit class Runner[A](s: Stream[IO, A]) {
    def yolo(): Unit = s.compile.drain.unsafeRunSync()
    def yoloV: Vector[A] = s.compile.toVector.unsafeRunSync()
  }

  def yo() =
    Stream
      .repeatEval(IO.println("hello"))
      .interruptAfter(2.seconds)
      .yolo

  val stream =
    Stream.unfold(10)(s => Some((s, s + 10))).covary[IO].metered(500.millis)

  def third = stream.take(3).compile.lastOrError.unsafeRunSync()
}
