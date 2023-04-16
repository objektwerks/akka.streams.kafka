package objektwerks

import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}

import akka.actor.ActorSystem
import akka.kafka.scaladsl.{Producer, Transactional}
import akka.stream.scaladsl.{Sink, Source}

import org.apache.kafka.clients.producer.ProducerRecord

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.io.StdIn
import scala.language.postfixOps

object App extends EmbeddedKafka:
  def main(args: Array[String]): Unit =
    val conf = new Conf()
    val topic = conf.topic
    val partitions = conf.partitions
    val parallelism = conf.parallelism

    implicit val kafkaConfig = EmbeddedKafkaConfig.defaultConfig
    val kafka = EmbeddedKafka.start()
    createCustomTopic(
      topic = conf.topic,
      topicConfig = kafkaConfig.customBrokerProperties,
      partitions = partitions,
      replicationFactor = 1
    ): Unit
    println(s"*** created topic: $topic with $partitions partitions")
    println("*** embedded kafka started")

    given system: ActorSystem = ActorSystem.create("akka-streams-kafka", conf.config)
    given dispatcher: ExecutionContext = system.dispatcher

    println("*** akka system started")

    println(s"*** producing records for topic: $topic ...")
    Source(0 until partitions)
      .map(integer => new ProducerRecord[String, String](topic, integer, integer.toString, integer.toString ))
      .runWith(Producer.plainSink(conf.producerSettings))
    println("*** producer finished")

    println(s"*** consuming records from topic: $topic with mapAsync parallelism set to: $parallelism ...")
    Transactional
      .source(conf.consumerSettings, conf.subscription)
      .mapAsync(parallelism) { message =>
        Future { // simulate async io, optionally persist partitions and offsets
          val record = message.record
          println(s"*** partition: ${record.partition} offset: ${record.offset} key: ${record.key} value: ${record.value}")
        }
      }
      .runWith(Sink.ignore)
    println(s"*** once consumer records have been printed, depress RETURN key to shutdown app")

    StdIn.readLine()

    Await.result(system.terminate(), 30 seconds)
    println("*** akka system stopped")

    kafka.stop(true)
    println("*** embedded kafka stopped")
    println("*** see log at /target/app.log")