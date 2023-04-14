package objektwerks

import akka.actor.Actor

import java.util.concurrent.atomic.AtomicInteger

final case class Add(i: Int)

final class AccActor extends Actor {
  val acc = new AtomicInteger(0)

  override def receive = {
    case Add(i) =>
      acc.addAndGet(i)
      println(s"*** Acc actor sum: $acc")
  }
}