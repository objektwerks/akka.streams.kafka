package objektwerks

import io.github.embeddedkafka._

import java.time.Duration
import java.util.Properties

import org.slf4j.LoggerFactory
import org.apache.kafka.common.serialization._
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.KafkaProducer

import scala.io.Source
import scala.jdk.CollectionConverters._

object Kafka {
  def apply(): Kafka = new Kafka()
}

class Kafka extends EmbeddedKafka {
  val logger = LoggerFactory.getLogger(getClass)
  implicit val config = EmbeddedKafkaConfig.defaultConfig
  implicit val serializer = new StringSerializer()
  implicit val deserializer = new StringDeserializer()
  val properties = loadProperties("/kafka.properties")
  val producer = new KafkaProducer[String, String](properties)
  val consumer = new KafkaConsumer[String, String](properties)

  val kafka = EmbeddedKafka.start()
  logger.info("*** embedded kafka started")

  def stop(): Unit = {
    producer.close()
    consumer.close()
    kafka.stop(false)
    logger.info("*** embedded kafka stopped")
  }

  def sendProducerRecord(topic: String, key: String, value: String): Unit = {
    val record = new ProducerRecord[String, String](topic, key, value)
    producer.send(record).get()
    logger.info("*** producer send: {}", record)
  }
 
  def pollConsumerRecords(topic: String): List[ConsumerRecord[String, String]] = {
    consumer.subscribe( List(topic).asJava )
    val records = consumer.poll( Duration.ofMillis(6000L) ).asScala.toList
    logger.info("*** consumer poll: {}", records.size)
    records
  }

  def loadProperties(file: String): Properties = {
    val properties = new Properties()
    properties.load(Source.fromInputStream(getClass.getResourceAsStream(file)).bufferedReader())
    properties
  }
}