package kafka

import akka.actor.ActorSystem
import akka.kafka.scaladsl.{Committer, Consumer, Producer}
import akka.stream.{ActorMaterializer, ClosedShape}
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, RunnableGraph, Sink, Source}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try

class StreamsTest extends FunSuite with BeforeAndAfterAll with Matchers {
  import Conf._
  import Common._

  implicit val system = ActorSystem.create("kafka-akka-streams-test", config)
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher
  implicit val logger = system.log

  override protected def afterAll(): Unit = {
    Await.result(system.terminate, 9 seconds)
    ()
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
      // .toMat(Committer.sink(committerSettings))(Keep.right)
      // .mapMaterializedValue(DrainingControl.apply)
      // .run
      // Somehow prevents message consume and offset commit ( with Keep.both set above ).
      // This code does not work for me!;)
      .runWith(Sink.ignore)
    Try(Await.result(done, 3 seconds))
    // Future[Done] never completes, so it times out. But messages are consumed and offsets committed.
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
    runnableGraph.run
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
    runnableGraph.run
    ()
  }
}