// import cats._, implicits._
// import cats.effect._, concurrent._
// import cats.effect.implicits._
// import fs2._
// import scala.concurrent.duration._

// object Playground extends IOApp {
//   def run(args: List[String]) = ExitCode.Success.pure[IO]

//   implicit class Runner[A](s: Stream[IO, A]) {
//     def yolo: Unit = s.compile.drain.unsafeRunSync
//     def yoloV: Vector[A] = s.compile.toVector.unsafeRunSync
//   }

//   // put("hello").to[F]
//   def put[A](a: A): IO[Unit] = IO(println(a))

//   def yo =
//     Stream
//       .repeatEval(put("hello"))
//       .interruptAfter(2.seconds)
//       .yolo
// }

// import cats._, implicits._
// import cats.effect._
// import fs2._
// import fs2.concurrent.SignallingRef
// import scala.concurrent.duration._


// final class Token
// object Token {
//   def create[F[_]: Sync] = Sync[F].delay(new Token)
// }

// trait Alarm[F[_]] {
//   def reset(d: FiniteDuration): F[Unit]
//   def timeouts: Stream[F, Alarm.Bell]
// }
// object Alarm {
//   type Bell = Bell.type
//   object Bell


//   def create[F[_]: Concurrent: Timer]: F[Alarm[F]] = {

//     def now = Timer[F].clock.monotonic(NANOSECONDS).map(_.nanos)

//     class Timeout(val id: Token, issuedAt: FiniteDuration, d: FiniteDuration) {
//       def asOfNow:  F[FiniteDuration] = now.map(now => d - (now - issuedAt))
//     }
//     object Timeout {
//       def issueNow(d: FiniteDuration): F[Timeout] = (Token.create, now).mapN(new Timeout(_, _, d))
//       def issueNow(id: Token, d: FiniteDuration): F[Timeout] = (Token.create, now).mapN(new Timeout(_, _, d))
//     }

//       // this eliminates the need for timestamps to distinguish among timeouts
//       // timestamps are still needed though in case `synchronous` blocks _and_
//       // elem gets enqueued before stale timeout.
//       // could avoid it if the queue somehow distinguished between rejection due to
//       // lack of sub vs rejection due to another elem enqueued, I could just offer then?
//       // with the current queue, timeouts might be lost if I just offer
//      // given that Timeout already has a Token, I can use that to avoid races anyway


//       SignallingRef[F, Option[Timeout]](None).map { time =>


//         // TODO this is canceled by concurrently, but `Eval` nodes don't get interrupted
//         // aka `Stream.sleep` is leaky

//         // can I just make this emit units (or timeouts) and merge it with the real stream?
//         // will the unNone needed to emit nothing in case of sleep cause a lot of `st.get`?
//         // should I use `Signal.discrete` to only get timeouts when they have changed?
//         // right, the current version signals a lot of consecutives timeouts since things aren't reset at zero
//         // otoh I'm not sure if `discrete.flatMap` will work, you need to sample as soon as possible when the current sleep has finished, potentially waiting until a new change to the signal has happened <== nope, or I won't timeout in the no reset case
//         // spec: once the current sleep has finished, I need to check the time:
//         // ok, state machine as usual
//         // - state: slept,  I want to check the time regardless of whether it has changed or not:
//         // if it's changed, state: slept, action: sleep and recurse
//         // if it's not changed: state: triggered, action: emit timeout object
//         // - state: triggered, I only want to check the time if it has changed
//         // if it's changed, state: slept, action: sleep and recurse
//         // if it's not changed, this should not happen
//         // api for the time: get right now, complete if it has changed since last time I checked
//         // what about using discrete only but set the time to 0 or whahevs (None?) in worker? (only do it if it's not changed in between discrete and modify, this will trigger discrete all the same though :( )
//         // this "fake change" could work, it needs to preserve the original timeout though
//         // data State = Updated Long Long | Untouched Long Long
//         // insight potentially leading to custom abstraction: I don't care about updates (unlike Signal), only about actual semantic changes, but not for the first case I guess
//         // wait for update straight after handler, not in the common path of the recursion... idea
//         // should I use millis in case sleep is not that precise? the problem is if I sleep less than needed i guess, although that only wastes a cycle but still works
//         // the first elem of Signal.discrete is the last updated value

//         def nextAfter(t: Timeout): Stream[F, Timeout] =
//           time.discrete.unNone.dropWhile(_.id == t.id).head

//         new Alarm[F] {
//           // emit Timeout or Timeout.id instead, to avoid the race
//           def timeouts: Stream[F, Bell] =
//             Stream.eval(time.get).unNone.flatMap { timeout =>
//               Stream.eval(timeout.asOfNow).flatMap { t =>
//                 if (t <= 0.nanos) Stream.emit(Bell) ++ nextAfter(timeout).drain
//                 else Stream.sleep_[F](t)
//               }
//             } ++ timeouts

//           def reset(d: FiniteDuration) = Timeout.issueNow(d).flatMap(t => time.set(t.some))
//         }
//       }
//   }

//   def minimal[F[_]: Concurrent: Timer] = {
//     object Bell
//     type Bell = Bell.type // I can use Token here

//     type Timeout = (Token, FiniteDuration, FiniteDuration)

//     def now: F[FiniteDuration] = Timer[F].clock.monotonic(NANOSECONDS).map(_.nanos)
//     def id: Timeout => Token = _._1
//     def asOfNow: Timeout => F[FiniteDuration] = {
//       case (_, issuedAt, d) => now.map(now => d - (now - issuedAt))
//     }
//     def issueNow(d: FiniteDuration): F[Timeout] = (Token.create, now).mapN((_, _, d))

//     SignallingRef[F, Option[Timeout]](None).map { time =>
//       def nextAfter(t: Timeout): Stream[F, Timeout] =
//         time.discrete.unNone.dropWhile(ct => id(ct) == id(t)).head

//       def timeouts: Stream[F, Bell] =
//         Stream.eval(time.get).unNone.flatMap { timeout =>
//           Stream.eval(asOfNow(timeout)).flatMap { t =>
//             if (t <= 0.nanos) Stream.emit(Bell) ++ nextAfter(timeout).drain
//             else Stream.sleep_[F](t)
//           }
//         } ++ timeouts

//       def reset(d: FiniteDuration) = issueNow(d).flatMap(t => time.set(t.some))

//       ???
//     }
//   }


import fs2._
import fs2.concurrent.{Queue, SignallingRef}
import cats.effect._
import cats.effect.concurrent.{Ref, Semaphore}
import cats.implicits._

import scala.concurrent.duration._

trait MiniKafka[F[_]] {
  def poll: F[Unit]
  def commit: F[Unit]
}
object MiniKafka {
  def create[F[_]: Sync: Timer] =
    Ref[F].of(false).map { inUse =>
      new MiniKafka[F] {
        def error(s: String) = new Exception(s"Simultaneous action! $s")

        def acquire(s: String) =
          inUse.modify {
            case true => true -> Sync[F].raiseError[Unit](error(s))
            case false => true -> ().pure[F]
          }.flatten

        def release = inUse.set(false)

        def poll = acquire("poll") >> Sync[F].delay(println("poll")) >> release

        def commit = acquire("commit") >> Sync[F].delay(println("commit")) >> release
      }
    }
}

case class Repro[F[_]: Concurrent: Timer]() {
  def prog =
    (MiniKafka.create[F], Queue.synchronous[F, Unit], SignallingRef[F, Unit](())).mapN {
      (kafka, q, sig) =>
      def consumer = Stream.repeatEval(sig.set(()) >> q.dequeue1).evalMap(_ => kafka.commit)
      def producer = sig.discrete.zipRight(Stream.repeatEval(kafka.poll)).through(q.enqueue)

      consumer
        .concurrently(producer)
        .compile
        .drain
    }.flatten
}

object App extends IOApp {
  def run(args: List[String]) =
    Repro[IO].prog.as(ExitCode.Success)

  def repl = Repro[IO].prog.unsafeRunSync
}

object Playground

trait Alarm[F[_]] {
  def reset(d: FiniteDuration, timeoutId: Token): F[Unit]
  def timeouts: Stream[F, Token]
}
object Alarm {
  def create[F[_]: Concurrent: Timer]: F[Alarm[F]] = {

    def now = Timer[F].clock.monotonic(NANOSECONDS).map(_.nanos)

    class Timeout(val id: Token, issuedAt: FiniteDuration, d: FiniteDuration) {
      def asOfNow:  F[FiniteDuration] = now.map(now => d - (now - issuedAt))
    }
    object Timeout {
      def issueNow(id: Token, d: FiniteDuration): F[Timeout] = now.map(new Timeout(id, _, d))
    }

    SignallingRef[F, Option[Timeout]](None).map { time =>
      def nextAfter(t: Timeout): Stream[F, Timeout] =
          time.discrete.unNone.dropWhile(_.id == t.id).head

        new Alarm[F] {
          def timeouts: Stream[F, Token] =
            Stream.eval(time.get).unNone.flatMap { timeout =>
              Stream.eval(timeout.asOfNow).flatMap { t =>
                if (t <= 0.nanos) Stream.emit(timeout.id) ++ nextAfter(timeout).drain
                else Stream.sleep_[F](t)
              }
            } ++ timeouts

          def reset(d: FiniteDuration) = Timeout.issueNow(d).flatMap(t => time.set(t.some))
        }
      }
  }
