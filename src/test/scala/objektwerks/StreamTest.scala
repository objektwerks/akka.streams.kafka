package objektwerks

import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.kafka.scaladsl.{Committer, Consumer, Producer}
import akka.stream.ClosedShape
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, RunnableGraph, Sink, Source}

import org.apache.kafka.clients.admin.{AdminClient, NewTopic}
import org.apache.kafka.clients.consumer.{ConsumerRecord, KafkaConsumer}
import org.apache.kafka.clients.producer.ProducerRecord
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.language.postfixOps
import scala.util.Try

class StreamTest extends AnyFunSuite with BeforeAndAfterAll with Matchers {
  import Conf._
  import Common._

  implicit val system = ActorSystem.create("kafka-akka-streams-test", config)
  implicit val dispatcher = system.dispatcher
  implicit val logger = system.log

  override protected def afterAll(): Unit = {
    Await.result(system.terminate(), 9 seconds)
  }

  test("producer -> consumer") {
    createTopic(topic) shouldBe true

    produceRecords()
    val postProduceRecordCount = countRecords(topic)

    consumeRecords()
    val postConsumeRecordCount = countRecords(topic)

    postProduceRecordCount should be >= 3
    postConsumeRecordCount shouldEqual 0
  }

  test("source -> sink graph") {
    createTopic(topic) shouldBe true
    withSourceSinkGraph()
    countRecords(topic) shouldEqual 0
  }

  test("source -> flow -> sink graph") {
    createTopic(topic) shouldBe true
    withSourceFlowSinkGraph()
    countRecords(topic) shouldEqual 0
  }

  def produceRecords(): Unit = {
    val done = Source(1 to 3)
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

  def consumeRecords(): Unit = {
    val done = Consumer
      .committableSource(consumerSettings, subscriptions)
      .map { message =>
        val record = message.record
        logger.info(s"*** Consumer -> topic: ${record.topic} partition: ${record.partition} offset: ${record.offset} key: ${record.key} value: ${record.value}")
        message.committableOffset
      }
      .runWith(Sink.ignore)
    Await.result(done, 3 seconds)
    ()
  }

  def withSourceSinkGraph(): Unit = {
    val runnableGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val recordSource = Source(4 to 6)
        .map(_.toString)
        .map { string =>
          val record = new ProducerRecord[String, String](topic, string, string)
          logger.info(s"*** Producer -> topic: $topic key: ${record.key} value: ${record.value}")
          record
        }
      val kafkaSink = Producer.plainSink(producerSettings)

      val kafkaSource = Consumer
        .committableSource(consumerSettings, subscriptions)
        .map { message =>
          val record = message.record
          logger.info(s"*** Consumer -> topic: ${record.topic} partition: ${record.partition} offset: ${record.offset} key: ${record.key} value: ${record.value}")
          message.committableOffset
        }
      val committerSink = Committer.sink(committerSettings)

      recordSource ~> kafkaSink
      kafkaSource.toMat(committerSink)(Keep.right)

      ClosedShape
    })
    runnableGraph.run()
    ()
  }

  def withSourceFlowSinkGraph(): Unit = {
    val runnableGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val recordSource = Source(7 to 9).map(_.toString)
      val producerRecordFlow = Flow[String].map { string =>
        val record = new ProducerRecord[String, String](topic, string, string)
        logger.info(s"*** Producer -> topic: $topic key: ${record.key} value: ${record.value}")
        record
      }
      val kafkaSink = Producer.plainSink(producerSettings)

      val kafkaSource = Consumer.plainSource(consumerSettings, subscriptions)
      val consumerRecordFlow = Flow[ConsumerRecord[String, String]].map { record =>
        logger.info(s"*** Consumer -> topic: ${record.topic} partition: ${record.partition} offset: ${record.offset} key: ${record.key} value: ${record.value}")
        record.toString
      }
      val ignoreSink = Sink.ignore

      recordSource  ~> producerRecordFlow ~> kafkaSink
      kafkaSource ~> consumerRecordFlow ~> ignoreSink

      ClosedShape
    })
    runnableGraph.run()
    ()
  }

  def createTopic(topic: String): Boolean = {
    val adminClient = AdminClient.create(adminClientProperties)
    val newTopic = new NewTopic(topic, 1, 1.toShort)
    val createTopicResult = adminClient.createTopics(List(newTopic).asJavaCollection)
    createTopicResult.values().containsKey(topic)
  }

  def countRecords(topic: String)(implicit logger: LoggingAdapter): Int = {
    val consumer = new KafkaConsumer[String, String](kafkaConsumerProperties)
    consumer.subscribe(List(topic).asJava)
    val count = new AtomicInteger()
    for (i <- 1 to 2) {
      val records = consumer.poll(Duration.ofMillis(100L))
      logger.info(s"+++ Consumer -> { ${records.count} } records polled on attempt { $i }.")
      records.iterator.asScala.foreach { record =>
        logger.info(s"+++ Consumer -> topic: ${record.topic} partition: ${record.partition} offset: ${record.offset} key: ${record.key} value: ${record.value}")
        count.incrementAndGet()
      }
    }
    consumer.close()
    logger.info(s"+++ Consumer -> record count is ${count.get}")
    count.get
  }
}