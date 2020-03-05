import cats._, implicits._
import cats.effect._, concurrent._
import cats.effect.implicits._
import fs2._
import fs2.concurrent._
import scala.concurrent.duration._

object Playground extends IOApp {
  def run(args: List[String]) = ExitCode.Success.pure[IO]

  implicit class Runner[A](s: Stream[IO, A]) {
    def yolo(): Unit = s.compile.drain.unsafeRunSync
    def yoloV: Vector[A] = s.compile.toVector.unsafeRunSync
  }
  // put("hello").to[F]
  def put[A](a: A): IO[Unit] = IO(println(a))

  def yo() =
    Stream
      .repeatEval(put("hello"))
      .interruptAfter(2.seconds)
      .yolo

  // def race[F[_]: Concurrent, A](lhs: Stream[F, A], rhs : Stream[F, A]): Stream[F, A] =
  //   Stream.eval(Deferred[F, Unit]).flatMap { stop =>

  //   }

  def a =
    List(1.some.pure[IO], 2.some.pure[IO])
      .traverse(x => cats.data.Nested(x))
      .value

  def tt =
    Stream
      .sleep(1.second)
      .mergeHaltL(Stream.sleep(20.seconds))
      .onFinalize(put("finished"))
      .yolo
  def parZipPr = {
    Stream
      .eval(
        // IO(scala.util.Random.nextInt(1000))
        //   .flatMap(s =>
        IO.sleep(1.second) >> put("a")
      )
      .mergeHaltBoth(
        Stream.eval(
          // IO(
          //   scala.util.Random
          //     .nextInt(1000)
          // ).flatMap(s =>
          IO.sleep(1.second) >> put("b") >> IO.never
            .onCancel(put("interrupted"))
        )
      )
  }.yolo

  def e =
    Stream
      .iterate(0)(_ + 1)
      .covary[IO]
      .metered(500.millis)
      .interruptAfter(4.seconds)
      .noneTerminate
      .evalMap(put)
      .yolo

  def ex =
    Stream
      .repeatEval(put("yo"))
      .merge(Stream.repeatEval(put("lo")))
      .interruptAfter(3.seconds)
      .yolo

}
