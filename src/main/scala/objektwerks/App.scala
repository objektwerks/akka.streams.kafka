package objektwerks

import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}

import akka.actor.ActorSystem
import akka.Done
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.stream.scaladsl.{Sink, Source}

import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

import scala.collection.mutable
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

object App extends EmbeddedKafka {
  def main(args: Array[String]): Unit = {
    val logger = LoggerFactory.getLogger(getClass)

    val conf = new Conf()

    implicit val kafkaConfig = EmbeddedKafkaConfig.defaultConfig
    val kafka = EmbeddedKafka.start()
    logger.info("*** embedded kafka started")

    implicit val system = ActorSystem.create("akka-streams-kafka", conf.config)
    implicit val dispatcher = system.dispatcher
    logger.info("*** akka system started")

    val producerDone = Source(1 to 10)
      .map(integer => integer.toString)
      .map(integer => new ProducerRecord[String, String](conf.topic, 0, integer, integer))
      .runWith(Producer.plainSink(conf.producerSettings))
    Await.result(producerDone, 10 seconds)
    logger.info("*** Producer finished.")

    val consumerDone = Consumer
      .plainSource(conf.consumerSettings, conf.subscriptions)
      .runWith(Sink.foreach(println))
    Thread.sleep(10000)
    logger.info("*** Consumer finished.")

    Await.result(system.terminate(), 30 seconds)
    logger.info("*** akka system stopped")

    kafka.stop(false)
    logger.info("*** embedded kafka stopped")
  }
}