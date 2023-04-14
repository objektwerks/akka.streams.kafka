package objektwerks

import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}

import akka.actor.{Actor, ActorSystem, Props}
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.stream.scaladsl.{Sink, Source}

import org.apache.kafka.clients.producer.ProducerRecord

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.io.StdIn
import scala.language.postfixOps
import akka.kafka.scaladsl.Committer

import objektwerks.Add
object App extends EmbeddedKafka {
  def main(args: Array[String]): Unit = {
    val conf = new Conf()

    implicit val kafkaConfig = EmbeddedKafkaConfig.defaultConfig
    val kafka = EmbeddedKafka.start()
    createCustomTopic(
      topic = conf.topic,
      topicConfig = kafkaConfig.customBrokerProperties,
      partitions = 10,
      replicationFactor = 1
    ): Unit
    println("*** embedded kafka started")

    implicit val system = ActorSystem.create("akka-streams-kafka", conf.config)
    implicit val dispatcher = system.dispatcher
    val accumulator = system.actorOf(Props[Accumulator], "accumulator")

    println("*** akka system started")

    println("*** producer producing records ...")
    Source(0 to 9)
      .map(integer => new ProducerRecord[String, String](conf.topic, integer, integer.toString, (integer + 1).toString ))
      .runWith(Producer.plainSink(conf.producerSettings))
    println("*** producer finished.")

    println("*** consumer consuming records ...")
    Consumer
      .plainSource(conf.consumerSettings, conf.subscription)
      .map { record =>
        accumulator ! Add( record.value.toIntOption.getOrElse(0) )
        record
      }
      .runWith(Sink.foreach(println)) // Note record order.
    println(s"*** once consumer records have been printed, depress RETURN key to shutdown app.")

    StdIn.readLine()

    Await.result(system.terminate(), 30 seconds)
    println("*** akka system stopped")

    kafka.stop(true)
    println("*** embedded kafka stopped")
    println("*** see /target/app.log for details")
  }
}