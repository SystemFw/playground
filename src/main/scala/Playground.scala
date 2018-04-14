object Playground {
  import cats._, implicits._
  import cats.effect._
  import fs2._
  import scala.concurrent.duration._
  import scala.concurrent.ExecutionContext

  implicit class Runner[A](s: Stream[IO, A])(implicit ec: ExecutionContext) {
    def yolo: Unit = s.compile.drain.unsafeRunSync
    def yoloV: Vector[A] = s.compile.toVector.unsafeRunSync
  }


}
