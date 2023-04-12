package objektwerks

import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}

import akka.actor.ActorSystem
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.stream.scaladsl.{Sink, Source}

import org.apache.kafka.clients.producer.ProducerRecord

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.io.StdIn

object App extends EmbeddedKafka {
  def main(args: Array[String]): Unit = {
    val conf = new Conf()

    implicit val kafkaConfig = EmbeddedKafkaConfig.defaultConfig
    val kafka = EmbeddedKafka.start()
    println("*** embedded kafka started")

    implicit val system = ActorSystem.create("akka-streams-kafka", conf.config)
    implicit val dispatcher = system.dispatcher
    println("*** akka system started")

    println("*** producer producing records ...")
    val producerDone = Source(1 to 10)
      .map(integer => integer.toString)
      .map(integer => new ProducerRecord[String, String](conf.topic, 0, integer, integer))
      .runWith(Producer.plainSink(conf.producerSettings))
    println("*** producer finished.")

    println("*** consumer consuming records ...")
    val consumerDone = Consumer
      .plainSource(conf.consumerSettings, conf.subscriptions)
      .runWith(Sink.foreach(println))
    println(s"*** once consumer records have been printed, depress RETURN key to shutdown app.")

    StdIn.readLine()

    Await.result(system.terminate(), 30 seconds)
    println("*** akka system stopped")

    kafka.stop(false)
    println("*** embedded kafka stopped")
  }
}