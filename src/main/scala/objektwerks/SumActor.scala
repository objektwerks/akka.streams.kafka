package objektwerks

import akka.actor.Actor

import java.util.concurrent.atomic.AtomicInteger

sealed trait Accumulator
final case class Add(i: Int) extends Accumulator
final case object Sum extends Accumulator

final class SumActor extends Actor {
  val sum = new AtomicInteger(0)

  override def receive = {
    case Add(i) => sum.addAndGet(i)
    case Sum    => println(s"*** Accumulator Actor Sum: $sum")
  }
}