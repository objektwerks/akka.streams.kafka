package objektwerks

import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.routing.{ActorRefRoutee, RoundRobinRoutingLogic, Router}
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.stream.scaladsl.{Sink, Source}

import org.apache.kafka.clients.producer.ProducerRecord

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.io.StdIn
import scala.language.postfixOps

final case class Work(partition: Int, offset: Long, key: String, value: String)

class Worker(partition: Int) extends Actor with ActorLogging {
  log.info(s"*** worker actor $partition intialized")

  def receive: Receive = {
    case Work(partition, offset, key, value) =>
      log.info(s"*** worker id: $partition partition: ${partition} offset: ${offset} key: ${key} value: ${value}")
  }
}

class Manager(partitions: Int) extends Actor with ActorLogging {
  val router = {
    val routees = (0 until partitions).map { partition =>
      ActorRefRoutee( context.actorOf(Props(classOf[Worker], partition), name = s"worker-$partition") )
    }
    Router(RoundRobinRoutingLogic(), routees)
  }
  log.info("*** manager actor intialized")

  def receive: Receive = {
    case work @ Work =>
      log.info(s"*** manager actor received: $work")
      router.route(work, sender())
  }
}

object ActorRouterApp extends EmbeddedKafka {
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
    println(s"*** created topic: $topic with $partitions partitions")
    println("*** embedded kafka started")

    implicit val system: ActorSystem = ActorSystem.create("akka-streams-kafka", conf.config)
    implicit val dispatcher: ExecutionContext = system.dispatcher
    val manager = system.actorOf(Props(classOf[Manager], partitions), name = "manager")

    println("*** akka system started")

    println(s"*** producing records for topic: $topic ...")
    Source(0 until partitions)
      .map(integer => new ProducerRecord[String, String](topic, integer, integer.toString, integer.toString ))
      .runWith(Producer.plainSink(conf.producerSettings))
    println("*** producer finished")

    println(s"*** consuming records from topic: $topic with $partitions actor [worker] routees ...")
    Consumer
      .plainSource(conf.consumerSettings, conf.subscription)
      .map { record =>
        manager ! Work(record.partition, record.offset, record.key, record.value)
      }
      .runWith(Sink.ignore)
    println(s"*** once consumer records have been printed, depress RETURN key to shutdown app")

    StdIn.readLine()

    Await.result(system.terminate(), 30 seconds)
    println("*** akka system terminated")

    kafka.stop(true)
    println("*** embedded kafka stopped")
    println("*** see log at /target/app.log")
    println("*** app shutdown")
  }
}