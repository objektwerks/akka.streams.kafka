package objektwerks

import akka.actor.Actor

import java.util.concurrent.atomic.AtomicInteger

sealed trait Adder
final case class Add(i: Int) extends Adder

final class AccActor extends Actor {
  val acc = new AtomicInteger(0)

  override def receive = {
    case Add(i) =>
      acc.addAndGet(i)
      println(s"*** Acc actor sum: $acc")
  }
}