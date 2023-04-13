package objektwerks

import akka.actor.Actor

import java.util.concurrent.atomic.AtomicInteger

sealed trait Acc
final case class Add(i: Int) extends Acc
final case object Sum extends Acc

final class AccActor extends Actor {
  val acc = new AtomicInteger(0)

  override def receive = {
    case Add(i) => acc.addAndGet(i)
    case Sum    => println(s"*** Acc actor sum: $acc")
  }
}