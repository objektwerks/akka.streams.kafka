package kafka

import akka.actor.ActorSystem
import akka.kafka.scaladsl.{Committer, Consumer, Producer}
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ClosedShape}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try

class KafkaAkkaStreamTest extends FunSuite with BeforeAndAfterAll with Matchers {
  import KafkaCommon._
  import TestConf._

  implicit val system = ActorSystem.create("kafka-akka-stream", config)
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher
  implicit val logger = system.log

  override protected def afterAll(): Unit = {
    Await.result(system.terminate, 3 seconds)
    ()
  }

  test("producer -> consumer") {
    assertTopic(topic) shouldBe true

    produceMessages(3)
    val postProduceMessageCount = countMessages(topic)

    consumeMessages()
    val postConsumeMessageCount = countMessages(topic)

    postProduceMessageCount should be >= 3
    postConsumeMessageCount shouldEqual 0
  }

  test("runnable graph") {
    assertTopic(topic) shouldBe true
    produceConsumeMessagesWithRunnableGraph(3)
    countMessages(topic) shouldEqual 0
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
      .committableSource(consumerSettings, subscriptions)
      .map { message =>
        val record = message.record
        logger.info(s"*** Consumer -> topic: ${record.topic} partition: ${record.partition} offset: ${record.offset} key: ${record.key} value: ${record.value}")
        message.committableOffset
      }
      .toMat(Committer.sink(committerSettings))(Keep.right)
      // .mapMaterializedValue(DrainingControl.apply) // Somehow prevents message consume and commit ( with Keep.both above )
      .run
    Try(Await.result(done, 3 seconds)) // Future[Done] never completes, so times out. But messages are consumed and offsets committed.
    ()
  }

  def produceConsumeMessagesWithRunnableGraph(count: Int): Unit = {
    val runnableGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val recordSource = Source(1 to count).map(_.toString)
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
      val printlnSink = Sink.foreach(println)

      recordSource  ~> producerRecordFlow ~> kafkaSink
      kafkaSource ~> consumerRecordFlow ~> printlnSink

      ClosedShape
    })
    runnableGraph.run
    ()
  }
}