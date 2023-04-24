package objektwerks

import io.github.embeddedkafka.{EmbeddedKafka, EmbeddedKafkaConfig}

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.pattern.ask
import akka.routing.{ActorRefRoutee, RoundRobinRoutingLogic, Router}
import akka.kafka.scaladsl.{Producer, Transactional}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout

import org.apache.kafka.clients.producer.ProducerRecord

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.io.StdIn
import scala.language.postfixOps

final case class Work(partition: Int, offset: Long, key: String, value: String)
final case class Processed(partition: Int, offset: Long, key: String, value: String)

/**
  * WARNING: Don't use string interpolation in Akka Actor logging!
  * Doing so incurs a huge performance penalty! Use this technique:
  * log.info("*** commment {}", message)
  */
final class Worker(partition: Int) extends Actor with ActorLogging {
  log.info("*** worker actor {} intialized", partition)

  def receive: Receive = {
    case work @ Work(partition, offset, key, value) => // Note worker name vis-a-vis id and partition in app.log!
      log.info("*** name: {} id: {} partition: {} offset: {} key: {} value: {}",
               Array( context.self.path.name, partition, partition, offset, key, value ) )
  }
}

final class Manager(partitions: Int) extends Actor with ActorLogging {
  val router = {
    val routees = (0 until partitions).map { partition =>
      ActorRefRoutee( context.actorOf(Props(classOf[Worker], partition), name = s"worker-$partition") )
    }
    Router(RoundRobinRoutingLogic(), routees)
  }
  log.info("*** manager actor intialized")

  def receive: Receive = {
    case work @ Work(partition, offset, key, value) =>
      log.info("*** manager actor received work: {}", work)
      router.route(work, sender)
      sender ! Processed(partition, offset, key, value)
  }
}

object ActorApp extends EmbeddedKafka {
  def main(args: Array[String]): Unit = {
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

    implicit val system: ActorSystem = ActorSystem.create("akka-streams-kafka", conf.config)
    implicit val dispatcher: ExecutionContext = system.dispatcher
    implicit val timeout = Timeout(30 seconds)
    val manager = system.actorOf(Props(classOf[Manager], partitions), name = "manager")
    println("*** akka system started")

    println(s"*** producing records for topic: $topic ...")
    Source(0 until partitions)
      .map(integer => new ProducerRecord[String, String](topic, integer, integer.toString, integer.toString ))
      .runWith(Producer.plainSink(conf.producerSettings))
    println("*** producer finished")

    println(s"*** consuming records from topic: $topic with mapAsync parallelism set to: $parallelism with $partitions actor [worker] routees ...")
    Transactional
      .source(conf.consumerSettings, conf.subscription)
      .mapAsync(parallelism) { message =>
        val record = message.record
        (manager ? Work(record.partition, record.offset, record.key, record.value) ).mapTo[Processed]
      }
      .map { processed =>
        println(s"*** processed > partition: ${processed.partition} offset: ${processed.offset} key: ${processed.key} value: ${processed.value}")
      }
      .runWith(Sink.ignore)
    println(s"*** once all consumer records have been printed, depress RETURN key to shutdown app")

    StdIn.readLine()

    Await.result(system.terminate(), 30 seconds)
    println("*** akka system terminated")

    kafka.stop(true)
    println("*** embedded kafka stopped")
    println("*** see log at /target/app.log")
    println("*** app shutdown")
  }
}