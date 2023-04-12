package objektwerks

import io.github.embeddedkafka.EmbeddedKafka

import akka.actor.ActorSystem
import akka.Done
import akka.kafka.scaladsl.Producer
import akka.stream.scaladsl.Source

import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

object App extends EmbeddedKafka {
  def main(args: Array[String]): Unit = {
    val logger = LoggerFactory.getLogger(getClass)

    val conf = new Conf()

    val kafka = EmbeddedKafka.start()
    logger.info("*** embedded kafka started")

    implicit val system = ActorSystem.create("akka-streams-kafka", conf.config)
    implicit val dispatcher = system.dispatcher
    logger.info("*** akka system started")

    val producerDone = Source(1 to 100)
      .map(value => new ProducerRecord[String, String](conf.topic, value.toString))
      .runWith(Producer.plainSink(conf.producerSettings))

    producerDone onComplete  {
      case Success(_) => logger.info("*** Producer done!")
      case Failure(error) => println(s"*** Producer failed: ${error.getMessage}")
    }


    Await.result(system.terminate(), 30 seconds)
    logger.info("*** akka system stopped")

    kafka.stop(false)
    logger.info("*** embedded kafka stopped")
  }
}