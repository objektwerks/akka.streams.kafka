package kafka

import akka.actor.ActorSystem
import akka.kafka.scaladsl.{Consumer, Producer}
import akka.stream.scaladsl.{Flow, GraphDSL, RunnableGraph, Sink, Source}
import akka.stream.{ActorMaterializer, ClosedShape}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class WithSourceFlowSinkGraphTest extends FunSuite with BeforeAndAfterAll with Matchers {
  import KafkaCommon._
  import TestConf._

  implicit val system = ActorSystem.create("with-source-flow-sink-graph-test", config)
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher
  implicit val logger = system.log

  override protected def afterAll(): Unit = {
    Await.result(system.terminate, 3 seconds)
    ()
  }

  test("with source -> flow -> sink graph") {
    assertTopic(topic) shouldBe true
    withSourceFlowSinkGraph(3)
    countMessages(topic) shouldEqual 0
  }

  def withSourceFlowSinkGraph(count: Int): Unit = {
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
      val ignoreSink = Sink.ignore

      recordSource  ~> producerRecordFlow ~> kafkaSink
      kafkaSource ~> consumerRecordFlow ~> ignoreSink

      ClosedShape
    })
    runnableGraph.run
    ()
  }
}