package kafka

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ActorMaterializerSettings, Supervision}
import akka.stream.Supervision.Decider
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

class KafkaAkkaStreamTest extends FunSuite with BeforeAndAfterAll with Matchers {
  implicit val system = ActorSystem.create("stream", ConfigFactory.load("test.conf"))
  implicit val dispatcher = system.dispatcher
  val decider: Decider = Supervision.restartingDecider
  val settings = ActorMaterializerSettings(system).withSupervisionStrategy(decider)
  implicit val materializer = ActorMaterializer(settings)

  override protected def afterAll(): Unit = {
    Await.result(system.terminate(), 1 second)
  }

  test("kafka") {

  }
}
