package kafka

import akka.Done
import akka.actor.ActorSystem
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import akka.stream.Supervision.Decider
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class KafkaAkkaStreamTest extends FunSuite with BeforeAndAfterAll with Matchers {
  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName)
  val config = ConfigFactory.load("test.conf")
  val producerConfig = config.getConfig("akka.kafka.producer")
  val consumerConfig = config.getConfig("akka.kafka.consumer")
  implicit val system = ActorSystem.create("stream", config)
  implicit val dispatcher = system.dispatcher
  val decider: Decider = Supervision.restartingDecider
  val settings = ActorMaterializerSettings(system).withSupervisionStrategy(decider)
  implicit val materializer = ActorMaterializer(settings)
  val topic = "cake-stream"

  override protected def afterAll(): Unit = {
    Await.result(system.terminate(), 1 second)
  }

  test("kafka") {
    Await.result(produceMessages(), 3 seconds)
    Await.result(consumeMessages(), 9 seconds)
  }

  def produceMessages(): Future[Done] = {
    val settings = ProducerSettings(producerConfig, new StringSerializer, new StringSerializer)
      .withBootstrapServers(producerConfig.getString("bootstrap.servers"))
    Source(1 to 3)
      .map(_.toString)
      .map { s =>
        val record = new ProducerRecord[String, String] (topic, s, s)
        logger.info(s"Producer -> key: ${record.key} value: ${record.value}")
        record
      }
      .runWith(Producer.plainSink(settings))
  }

  def consumeMessages(): Future[Done] = {
    val settings = ConsumerSettings(consumerConfig, new StringDeserializer, new StringDeserializer)
      .withBootstrapServers(consumerConfig.getString("bootstrap.servers"))
      .withGroupId(consumerConfig.getString("group.id"))
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, consumerConfig.getString("auto.offset.reset"))
    Consumer.committableSource(settings, Subscriptions.topics(topic))
      .mapAsync(1) { message =>
        logger.info(s"Consumer -> key: ${message.record.key} value: ${message.record.value}")
        Future.successful(Done).map(_ => message)
      }
      .mapAsync(1) { message => message.committableOffset.commitScaladsl() }
      .runWith(Sink.ignore)
  }
}