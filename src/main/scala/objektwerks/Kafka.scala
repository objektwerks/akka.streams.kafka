package objektwerks

import io.github.embeddedkafka._

import org.slf4j.LoggerFactory
import org.apache.kafka.common.serialization._
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer

final class Kafka(conf: Conf) extends EmbeddedKafka {
  val logger = LoggerFactory.getLogger(getClass)

  implicit val config = EmbeddedKafkaConfig.defaultConfig
  implicit val serializer = new StringSerializer()
  implicit val deserializer = new StringDeserializer()

  val producer = new KafkaProducer[String, String](conf.properties)
  val consumer = new KafkaConsumer[String, String](conf.properties)

  val kafka = EmbeddedKafka.start()
  logger.info("*** embedded kafka started")

  def stop(): Unit = {
    producer.close()
    consumer.close()
    kafka.stop(false)
    logger.info("*** embedded kafka stopped")
  }
}