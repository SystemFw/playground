import cats._, implicits._
import cats.effect._, concurrent._
import cats.effect.implicits._
import fs2._
import scala.concurrent.duration._

object Playground extends IOApp {
  def run(args: List[String]) = ExitCode.Success.pure[IO]

  implicit class Runner[A](s: Stream[IO, A]) {
    def yolo: Unit = s.compile.drain.unsafeRunSync
    def yoloV: Vector[A] = s.compile.toVector.unsafeRunSync
  }
  // put("hello").to[F]
  def put[A](a: A): IO[Unit] = IO(println(a))

  def yo =
    Stream
      .repeatEval(put("hello"))
      .interruptAfter(2.seconds)
      .yolo

  def p = IO.sleep(3.seconds).flatMap(_ => put("done"))

  def p1 = p.start >> put("p started")

  def play = p1.unsafeRunSync

  def void[F[_]: Functor, A]: F[A] => F[Unit] = _.map(_ => ())

  def start[F[_]: Concurrent, A]: F[A] => F[Fiber[F, A]] = ???


}

