object Playground {
  import cats._, implicits._
  import cats.effect._
  import fs2._
  import scala.concurrent.duration._
  import scala.concurrent.ExecutionContext

  def stdOut[F[_], I](implicit F: Sync[F]): Sink[F, I] =
    _.evalMap(e => F.delay(Console.out.println(e)))

}
