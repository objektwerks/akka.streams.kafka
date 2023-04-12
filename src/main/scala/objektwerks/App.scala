package objektwerks

import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.event.LoggingAdapter

import Conf._

object App {
  def main(args: Array[String]): Unit = {
    val kafka = Kafka()

    implicit val system = ActorSystem.create("akka-streams-kafka", config)
    implicit val dispatcher = system.dispatcher
    implicit val logger = system.log

    Await.result(system.terminate(), 9 seconds)
    kafka.stop()
  }
}