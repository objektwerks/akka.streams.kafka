package kafka

import java.time.Duration
import java.util.Properties
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.kafka.scaladsl.Consumer.DrainingControl
import akka.kafka.scaladsl.{Committer, Consumer, Producer}
import akka.kafka.{CommitterSettings, ConsumerSettings, ProducerSettings, Subscriptions}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Source}
import com.typesafe.config.ConfigFactory
import org.apache.kafka.clients.consumer.{ConsumerConfig, KafkaConsumer}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import scala.collection.JavaConverters._

class KafkaAkkaStreamTest extends FunSuite with BeforeAndAfterAll with Matchers {
  val config = ConfigFactory.load("test.conf")
  val producerConfig = config.getConfig("akka.kafka.producer")
  val consumerConfig = config.getConfig("akka.kafka.consumer")
  val committerConfig = config.getConfig("akka.kafka.committer")

  implicit val system = ActorSystem.create("kafka-akka-stream", config)
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher
  implicit val logger = system.log

  val topic = "kv"

  val kafkaConsumerProperties = loadProperties("/kafka-consumer.properties")

  val producerSettings = ProducerSettings(producerConfig, new StringSerializer, new StringSerializer)
    .withBootstrapServers(producerConfig.getString("bootstrap.servers"))

  val consumerSettings = ConsumerSettings(consumerConfig, new StringDeserializer, new StringDeserializer)
    .withBootstrapServers(consumerConfig.getString("bootstrap.servers"))
    .withGroupId(consumerConfig.getString("group.id"))
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, consumerConfig.getString("auto.offset.reset"))

  val committerSettings = CommitterSettings(committerConfig)

  override protected def afterAll(): Unit = {
    Await.result(system.terminate, 3 seconds)
    ()
  }

  test("kafka") {
    produceMessages(3)
    val postProduceMessageCount = countMessages(2)

    consumeMessages()
    val postConsumeMessageCount = countMessages(2)

    postProduceMessageCount should be >= 3
    postConsumeMessageCount shouldEqual 0
  }

  def produceMessages(count: Int): Unit = {
    val done = Source(1 to count)
      .map(_.toString)
      .map { string =>
        val record = new ProducerRecord[String, String] (topic, string, string)
        logger.info(s"*** Producer -> topic: $topic key: ${record.key} value: ${record.value}")
        record
      }
      .runWith(Producer.plainSink(producerSettings))
    Await.result(done, 3 seconds)
    ()
  }

  def consumeMessages(): Unit = {
    val done = Consumer
      .committableSource(consumerSettings, Subscriptions.topics(topic))
      .mapAsync(parallelism = 4) { message => Future.successful(message.committableOffset) }
      .toMat(Committer.sink(committerSettings))(Keep.both)
      .mapMaterializedValue(DrainingControl.apply)
      .run
      .drainAndShutdown
    Await.result(done, 3 seconds)
    ()
  }

  def countMessages(retries: Int): Int = {
    val consumer = new KafkaConsumer[String, String](kafkaConsumerProperties)
    consumer.subscribe(List(topic).asJava)
    val count = new AtomicInteger()
    for (i <- 1 to retries) {
      val records = consumer.poll(Duration.ofMillis(100L))
      logger.info(s"*** Consumer -> { ${records.count} } records polled on attempt { $i }.")
      records.iterator.asScala.foreach { record =>
        logger.info(s"*** Consumer -> topic: ${record.topic} partition: ${record.partition} offset: ${record.offset} key: ${record.key} value: ${record.value}")
        count.incrementAndGet()
      }
    }
    consumer.close()
    logger.info(s"*** Consumer -> count is ${count.get}")
    count.get
  }

  def loadProperties(file: String): Properties = {
    val properties = new Properties()
    properties.load(scala.io.Source.fromInputStream(getClass.getResourceAsStream(file)).bufferedReader())
    properties
  }
}