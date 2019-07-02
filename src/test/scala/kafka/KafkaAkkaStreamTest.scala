package kafka

import java.util.concurrent.atomic.AtomicInteger

import akka.Done
import akka.actor.ActorSystem
import akka.kafka.ConsumerMessage.CommittableMessage
import akka.kafka.scaladsl.Consumer.DrainingControl
import akka.kafka.scaladsl.{Committer, Consumer, Producer}
import akka.kafka.{CommitterSettings, ConsumerSettings, ProducerSettings, Subscriptions}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Source}
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class KafkaAkkaStreamTest extends FunSuite with BeforeAndAfterAll with Matchers {
  val logger = LoggerFactory.getLogger(this.getClass.getSimpleName)
  val config = ConfigFactory.load("test.conf")
  val producerConfig = config.getConfig("akka.kafka.producer")
  val consumerConfig = config.getConfig("akka.kafka.consumer")

  implicit val system = ActorSystem.create("kafka-akka-stream", config)
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher

  val topic = "kv"

  val producerSettings = ProducerSettings(producerConfig, new StringSerializer, new StringSerializer)
    .withBootstrapServers(producerConfig.getString("bootstrap.servers"))

  val consumerSettings = ConsumerSettings(consumerConfig, new StringDeserializer, new StringDeserializer)
    .withBootstrapServers(consumerConfig.getString("bootstrap.servers"))
    .withGroupId(consumerConfig.getString("group.id"))
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, consumerConfig.getString("auto.offset.reset"))

  val committerSettings = CommitterSettings(system)

  override protected def afterAll(): Unit = {
    Await.result(system.terminate(), 1 second)
    ()
  }

  test("kafka") {
    produceMessages(3)
    consumeMessages() min 3
  }

  def produceMessages(count: Int): Unit = {
    val done = Source(1 to count)
      .map(_.toString)
      .map { string =>
        val record = new ProducerRecord[String, String] (topic, string, string)
        logger.info(s"Producer -> topic: $topic key: ${record.key} value: ${record.value}")
        record
      }
      .runWith(Producer.plainSink(producerSettings))
    Await.result(done, 3 seconds)
    ()
  }

  def consumeMessages(): Int = {
    val count = new AtomicInteger()
    val control: DrainingControl[Done] = Consumer
      .committableSource(consumerSettings, Subscriptions.topics(topic))
      .mapAsync(parallelism = 1) { message =>
        count.incrementAndGet
        consumeMessage(message).map(_ => message.committableOffset)
      }
      .toMat(Committer.sink(committerSettings))(Keep.both)
      .mapMaterializedValue(DrainingControl.apply)
      .run()
    val done = control.drainAndShutdown
    Await.result(done, 3 seconds)
    count.get
  }

  def consumeMessage(message: CommittableMessage[String, String]): Future[Done] = {
    val record = message.record
    logger.info(s"Consumer -> topic: ${record.topic} partition: ${record.partition} offset: ${record.offset} key: ${record.key} value: ${record.value}")
    Future.successful(Done)
  }
}