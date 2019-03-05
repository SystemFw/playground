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

  def p =
    (Stream(1, 2) ++ Stream(3, 4) ++ Stream(5, 6))
      .covary[IO]
      .evalTap(i => IO(println(i)))
      .switchMap(Stream.emit)
      .interruptAfter(5.seconds)
      .yoloV

  // `fa` can be canceled, but if `fa` completes, `next` must happen too.
  //
  // Cannot use `bracket` because that makes `fa` uncancelable
  // Cannot use `guarantee` because you don't have access to `a`
  // Cannot use `flatMap` cause you can be interrupted between `fa` and `A => F[B]`
  // Cannot use `fa.flatMap(fb).uncancelable` because that makes `fa` uncancelable too
  def continual[F[_]: Concurrent, A, B](fa: F[A], next: A => F[B]): F[B] = {

    val action = fa
      .flatMap(next)

    // can cancel `start`, and `fa` doesn't even start
    // if they cancel `join` or `flatMap`, `fa` has started and cannot be canceled
    val a = action.start.flatMap(_.join)

    // `fa` always starts, user can cancel `join`, but cannot interrupt `fa`
    val b = action.start.bracket(_.join)(_ => ().pure[F])

    // when users cancel `join`, `action` gets interrupted
    // the outer `bracket` guaranteees that you cannot leak `cancel`
    // however `action` does not have the `continual` guarantee
    // As it is, convoluted version of `fa.flatMap(next)`
    // However we can replace that `fiber.cancel` with an arbitrary action
    val c = action.start.bracket(fiber =>
      fiber.join.guaranteeCase {
        case ExitCase.Canceled => fiber.cancel
        case _ => ().pure[F]
    })(_ => ().pure[F])

    // This should work except the fiber.join in ffa becomes non terminating
    // when its fiber get canceled... if it could be interrupted instead
    // that would be great
    val d = Deferred[F, F[Unit]].flatMap { fin =>
      val ffa = fa.start.flatMap { fiber =>
        fin.complete(fiber.cancel) >> fiber.join
      }

      val action = ffa
        .flatMap(next)

      action.start.bracket(fiber =>
        fiber.join.guaranteeCase {
          case ExitCase.Canceled => fin.get.flatten // this is uncancelable
          case _ => ().pure[F]
      })(_ => ().pure[F])

    }

    // this should work, but it does not wait for the fa to be canceled
    // before returning control, would need a third deferred for that
    val e = (Deferred[F, Unit] product Deferred[F, B]).flatMap {
      case (stop, result) =>
        val action = fa.race(stop.get).flatMap {
          case Left(a) => next(a).flatMap(result.complete)
          case Right(_) => ().pure[F]
        }

        action.start.bracket(_ =>
          result.get.guaranteeCase {
            case ExitCase.Canceled => stop.complete(()) // this is uncancelable
            case _ => ().pure[F]
        })(_ => ().pure[F])

    }

    // same issue: finaliser doesnt' backpressure
    val f = Deferred[F, Unit].flatMap { stop =>
      val action = fa.race(stop.get).flatMap {
        case Left(a) => next(a).map(_.some)
        case Right(_) => Option.empty[B].pure[F]
      }

      action.start.bracket(fiber =>
        (fiber.join.map(_.get)).guaranteeCase {
          case ExitCase.Canceled => stop.complete(()) // this is uncancelable
          case _ => ().pure[F]
      })(_ => ().pure[F])

    }

    val g = Ref[F].of(Option.empty[F[Unit]]).flatMap { stop =>
      val ffa = fa.start.flatMap { fiber =>
        stop.set(fiber.cancel.some) >> fiber.join.guarantee(
          stop.set(().pure[F].some))
      }

      val action = ffa
        .flatMap(next)

      action.start.bracket(fiber =>
        fiber.join.guaranteeCase {
          case ExitCase.Canceled =>
            stop.get
              .iterateUntil(_.isDefined)
              .flatMap(_.get) // this is uncancelable
          case _ => ().pure[F]
      })(_ => ().pure[F])

    }

    // potential problem: fa is not canceled, but the join is made non-terminating all the same
    val h = Ref[F].of(Option.empty[F[Unit]] -> false).flatMap { state =>
      val ffa = fa.guaranteeCase {
        case ExitCase.Canceled =>
          state.update {
            case (cancelFA, faWasInterrupted) => cancelFA -> true
          }
        case _ => ().pure[F]
      }

      val fffa = ffa.start.flatMap { fiber =>
        state.update {
          case (cancelFA, faWasInterrupted) =>
            fiber.cancel.some -> faWasInterrupted
        } >> fiber.join // if fa is canceled, this becomes non terminating and needs to be canceled as well
      }

      val action = fffa.flatMap(next)

      action.start.bracket(fiber =>
        fiber.join.guaranteeCase {
          case ExitCase.Canceled =>
            state.get.iterateUntil(_._1.isDefined).flatMap {
              case (cancelFA, _) =>
                cancelFA.get // returns when all finalisers on fa have run
            } >> state.get.map(_._2).ifM(fiber.cancel, ().pure[F])
          case _ => ().pure[F]
      })(_ => ().pure[F])
    }

    val l = (Deferred[F, Unit] product Deferred[F, Unit]).flatMap {
      case (stop, finaliserDone) =>
        val action = fa.race(stop.get).flatMap {
          case Left(a) => next(a).map(_.some)
          case Right(_) => finaliserDone.complete(()) as Option.empty[B]
        }

        action.start.bracket(fiber =>
          (fiber.join.map(_.get)).guaranteeCase {
            case ExitCase.Canceled =>
              stop.complete(()) >> finaliserDone.get // this is uncancelable
            case _ => ().pure[F]
        })(_ => ().pure[F])
    }

    c
  }

  def p1 =
    (timer
      .sleep(1.second)
      .race(IO.never
        .guarantee(timer.sleep(3.seconds) >> put("finaliser done"))) >> put(
      "continue")).unsafeRunSync

}

object Continual extends IOApp {

  def run(args: List[String]) = ExitCode.Success.pure[IO]


  // `F[A]` can be canceled, but if it completes, `Err \/ A => F[B]` happen too.
  //
  // Cannot use `bracket` because that makes `F[A]` uncancelable
  // Cannot use `guarantee` because you don't have access to `A`
  // Cannot use `flatMap` cause you can be interrupted between `F[A]` and `Err \/ A => F[B]`
  // Cannot use `fa.flatMap(fb).uncancelable` because that makes `fa` uncancelable too
  def continual[F[_]: Concurrent, A, B](cancelable: F[A])(continuation: Either[Throwable, A] => F[B]): F[B] =
    (Deferred[F, Unit] product Deferred[F, Unit]).flatMap {
      case (stop, finaliserDone) =>
        val action = cancelable.attempt.race(stop.get).flatMap {
          case Left(a) => continuation(a).map(_.some)
          case Right(_) => Option.empty[B].pure[F]
        } <* finaliserDone.complete(())

        action.start.bracket(fiber =>
          (fiber.join.map(_.get)).guaranteeCase {
            case ExitCase.Canceled =>
              stop.complete(()) >> finaliserDone.get // this is uncancelable
            case _ => ().pure[F]
        })(_ => ().pure[F])
    }

  def runner(n: Int)(io: IO[Unit]) =
    io
      .timeout(n.seconds)
      .guarantee(IO(println("done")))
      .handleError(_ => ())
      .unsafeRunSync

  def faIsInterruptible = runner(1) {
    continual(IO(println("start")) >> IO.never)(_ => IO.unit)
  }

  def finaliserBackPressures = runner(1) {
    def fa = (IO(println("start")) >> IO.never).guaranteeCase {
      case ExitCase.Canceled => timer.sleep(2.seconds) >> IO(println("finaliser done"))
      case _ => IO.unit
    }

    continual(fa)(_ => IO.unit)
  }

  def noInterruptionHappyPath =
    continual(IO(1))(n => IO(n.right.get + 1)).map(_ + 1).unsafeRunSync

  def noInterruptionError =
    continual(IO.raiseError(new Exception("boom")))(n => IO(n.left.get)).unsafeRunSync

  def noInterruptionContinuationError =
      continual(IO(1))(_ => IO.raiseError(new Exception("boom"))).attempt.unsafeRunSync

  def continualSemantics = runner(1) {
    continual(IO(println("start")))(_ => timer.sleep(2.seconds) >> IO(println("continuation done")))
  }


// scala> import Continual._
// import Continual._

// scala> faIsInterruptible
// start
// done

// scala> finaliserBackPressures
// start
// finaliser done
// done

// scala> noInterruptionHappyPath
// res2: Int = 3

// scala> noInterruptionError
// res3: Throwable = java.lang.Exception: boom

// scala> noInterruptionContinuationError
// res4: Either[Throwable,Nothing] = Left(java.lang.Exception: boom)

// scala> continualSemantics
// start
// continuation done
// done

}
