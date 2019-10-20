import cats._, implicits._
import cats.effect._, concurrent._
import cats.effect.implicits._
import fs2._
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

  def a = Resource.make(put("open 1"))(_ => put("close 1"))
  def b = Resource.make(put("open 2"))(_ => put("close 2"))
  def c =
    Resource.make(IO.raiseError[Unit](new Exception))(_ => put("close 2"))
  def d = (a >> b >> c).allocated.unsafeRunSync

  implicit class R[F[_], A](r: Resource[F, A]) {
    import annotation.tailrec

    import Resource._

    def use[B](f: A => F[B])(implicit F: Bracket[F, Throwable]): F[B] = {
      // Indirection for calling `loop` needed because `loop` must be @tailrec
      def continue(current: Resource[F, Any],
                   stack: List[Any => Resource[F, Any]]): F[Any] =
        loop(current, stack)

      // Interpreter that knows how to evaluate a Resource data structure;
      // Maintains its own stack for dealing with Bind chains
      @tailrec
      def loop(current: Resource[F, Any],
               stack: List[Any => Resource[F, Any]]): F[Any] = {
        current match {
          case Allocate(resource) =>
            F.bracketCase(resource) {
              case (a, _) =>
                stack match {
                  case Nil => f.asInstanceOf[Any => F[Any]](a)
                  case f0 :: xs => continue(f0(a), xs)
                }
            } {
              case ((_, release), ec) =>
                release(ec)
            }
          case Bind(source, f0) =>
            loop(source, f0.asInstanceOf[Any => Resource[F, Any]] :: stack)
          case Suspend(resource) =>
            resource.flatMap(continue(_, stack))
        }
      }
      loop(r.asInstanceOf[Resource[F, Any]], Nil).asInstanceOf[F[B]]
    }

    def allocated(implicit F: Bracket[F, Throwable]): F[(A, F[Unit])] = {

      // Indirection for calling `loop` needed because `loop` must be @tailrec
      def continue(current: Resource[F, Any],
                   stack: List[Any => Resource[F, Any]],
                   release: F[Unit]): F[(Any, F[Unit])] =
        loop(current, stack, release)

      // Interpreter that knows how to evaluate a Resource data structure;
      // Maintains its own stack for dealing with Bind chains
      @tailrec
      def loop(current: Resource[F, Any],
               stack: List[Any => Resource[F, Any]],
               release: F[Unit]): F[(Any, F[Unit])] =
        current match {
          case Resource.Allocate(resource) =>
            F.bracketCase(resource) {
              case (a, rel) =>
                stack match {
                  case Nil =>
                    F.pure(a -> F.guarantee(rel(ExitCase.Completed))(release))
                  case f0 :: xs =>
                    continue(f0(a),
                             xs,
                             F.guarantee(rel(ExitCase.Completed))(release))
                }
            } {
              case (_, ExitCase.Completed) =>
                F.unit
              case ((_, release), ec) =>
                release(ec)
            }
          case Resource.Bind(source, f0) =>
            loop(source,
                 f0.asInstanceOf[Any => Resource[F, Any]] :: stack,
                 release)
          case Resource.Suspend(resource) =>
            resource.flatMap(continue(_, stack, release))
        }

      loop(r.asInstanceOf[Resource[F, Any]], Nil, F.unit).map {
        case (a, release) =>
          (a.asInstanceOf[A], release)
      }
    }
  }
}
