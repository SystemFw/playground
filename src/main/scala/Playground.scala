object Playground {
  import cats._, implicits._
  import cats.effect._
  import fs2._
  import scala.concurrent.duration._
  import scala.concurrent.ExecutionContext.Implicits.global

  def stdOut[F[_], I](implicit F: Sync[F]): Sink[F, I] =
    _.map(_.toString).to(_.evalMap(str => F.delay(Console.out.println(str))))
}

