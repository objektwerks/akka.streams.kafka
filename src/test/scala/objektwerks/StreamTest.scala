package objektwerks

import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

import akka.actor.ActorSystem
import akka.event.LoggingAdapter

class StreamTest extends AnyFunSuite with BeforeAndAfterAll with Matchers {
  val kafka = Kafka()

  implicit val system = ActorSystem.create("kafka-akka-streams-test", config)
  implicit val dispatcher = system.dispatcher
  implicit val logger = system.log

  override protected def afterAll(): Unit = {
    Await.result(system.terminate(), 9 seconds)
    kafka.stop()
  }

  test("producer -> consumer") {

  }
}