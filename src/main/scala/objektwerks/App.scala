package objektwerks

import java.time.Duration

import akka.actor.ActorSystem

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

object App {
  def main(args: Array[String]): Unit = {
    val conf = new Conf()
    val kafka = new Kafka(conf)

    implicit val system = ActorSystem.create("akka-streams-kafka", conf.config)
    implicit val dispatcher = system.dispatcher
    implicit val logger = system.log

    Await.result(system.terminate(), 9 seconds)
    kafka.stop()
  }
}