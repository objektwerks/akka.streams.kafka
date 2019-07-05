package kafka

import akka.actor.ActorSystem
import akka.kafka.scaladsl.{Committer, Consumer, Producer}
import akka.stream.scaladsl.{GraphDSL, Keep, RunnableGraph, Source}
import akka.stream.{ActorMaterializer, ClosedShape}
import org.apache.kafka.clients.producer.ProducerRecord
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps

class WithSourceSinkGraphTest extends FunSuite with BeforeAndAfterAll with Matchers {
  import KafkaCommon._
  import TestConf._

  implicit val system = ActorSystem.create("with-source-sink-graph-test", config)
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher
  implicit val logger = system.log

  override protected def afterAll(): Unit = {
    Await.result(system.terminate, 3 seconds)
    ()
  }

  test("with source -> sink graph") {
    assertTopic(topic) shouldBe true
    withSourceSinkGraph()
    countMessages(topic) shouldEqual 0
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
}