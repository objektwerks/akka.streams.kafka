package objektwerks

import akka.actor.Actor

import java.util.concurrent.atomic.AtomicInteger

sealed trait Accumulator
final case class Add(i: Int) extends Accumulator
final case object Sum extends Accumulator

final class AccActor extends Actor {
  val acc = new AtomicInteger(0)

  override def receive = {
    case Add(i) => acc.addAndGet(i)
    case Sum    => println(s"*** Acc Actor sum: $acc")
  }
}