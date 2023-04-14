package objektwerks

import akka.actor.Actor

import java.util.concurrent.atomic.AtomicInteger

final case class Add(partition: Int, key: String, value: Int)

final class Accumulator extends Actor {
  val acc = new AtomicInteger(0)

  override def receive = {
    case Add(partition, key, value) =>
      acc.addAndGet(value)
      println(s"*** Accumulator partition: $partition key: $key value: $value sum: $acc")
  }
}