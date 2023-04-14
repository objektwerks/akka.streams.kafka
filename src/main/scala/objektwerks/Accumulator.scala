package objektwerks

import akka.actor.Actor

import java.util.concurrent.atomic.AtomicInteger

final case class Add(partition: Int, integer: Int)

final class Accumulator extends Actor {
  val acc = new AtomicInteger(0)

  override def receive = {
    case Add(partition, integer) =>
      acc.addAndGet(integer)
      println(s"*** Accumulator partition: $partition add: $integer sum: $acc")
  }
}