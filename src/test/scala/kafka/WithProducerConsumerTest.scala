package kafka

import akka.actor.ActorSystem
import akka.kafka.scaladsl.{Committer, Consumer, Producer}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Source}
import org.apache.kafka.clients.producer.ProducerRecord
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try

class WithProducerConsumerTest extends FunSuite with BeforeAndAfterAll with Matchers {
  import KafkaCommon._
  import TestConf._

  implicit val system = ActorSystem.create("with-producer-consumer-test", config)
  implicit val materializer = ActorMaterializer()
  implicit val dispatcher = system.dispatcher
  implicit val logger = system.log

  override protected def afterAll(): Unit = {
    Await.result(system.terminate, 3 seconds)
    ()
  }

  test("with producer -> consumer") {
    assertTopic(topic) shouldBe true

    produceMessages()
    val postProduceMessageCount = countMessages(topic)

    consumeMessages()
    val postConsumeMessageCount = countMessages(topic)

    postProduceMessageCount should be >= 3
    postConsumeMessageCount shouldEqual 0
  }

  def produceMessages(): Unit = {
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
}