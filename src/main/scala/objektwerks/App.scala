package objektwerks

import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}

import akka.actor.{Actor, ActorSystem, Props}
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.stream.scaladsl.{Sink, Source}

import org.apache.kafka.clients.producer.ProducerRecord

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.io.StdIn
import scala.language.postfixOps

import objektwerks.Add

object App extends EmbeddedKafka {
  def main(args: Array[String]): Unit = {
    val conf = new Conf()
    val topic = conf.topic
    val partitions = conf.partitions

    implicit val kafkaConfig = EmbeddedKafkaConfig.defaultConfig
    val kafka = EmbeddedKafka.start()
    createCustomTopic(
      topic = conf.topic,
      topicConfig = kafkaConfig.customBrokerProperties,
      partitions = partitions,
      replicationFactor = 1
    ): Unit
    println("*** embedded kafka started")

    implicit val system = ActorSystem.create("akka-streams-kafka", conf.config)
    implicit val dispatcher = system.dispatcher
    val accumulator = system.actorOf(Props[Accumulator], "accumulator")

    println("*** akka system started")

    println(s"*** producer producing records to topic: $topic ...")
    Source(0 until partitions)
      .map(integer => new ProducerRecord[String, String](topic, integer, integer.toString, integer.toString ))
      .runWith(Producer.plainSink(conf.producerSettings))
    println("*** producer finished.")

    println(s"*** consumer consuming records from topic: $topic ...")
    Consumer
      .plainSource(conf.consumerSettings, conf.subscription)
      .mapAsync(4) { record =>
        accumulator ! Add( record.partition, record.offset, record.key, record.value.toIntOption.getOrElse(0) )
        Future.unit
      }
      .runWith(Sink.ignore)
    println(s"*** once consumer records have been printed, depress RETURN key to shutdown app.")

    StdIn.readLine()

    Await.result(system.terminate(), 30 seconds)
    println("*** akka system stopped")

    kafka.stop(true)
    println("*** embedded kafka stopped")
    println("*** see log at /target/app.log")
  }
}