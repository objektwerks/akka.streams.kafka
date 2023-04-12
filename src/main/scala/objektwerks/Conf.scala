package objektwerks

import java.util.Properties

import akka.kafka.{CommitterSettings, ConsumerSettings, ProducerSettings, Subscriptions}

import com.typesafe.config.ConfigFactory

import org.apache.kafka.clients.admin.AdminClientConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}

import scala.io.Source

final class Conf {
  val config = ConfigFactory.load("app.conf")
  val producerConfig = config.getConfig("akka.kafka.producer")
  val consumerConfig = config.getConfig("akka.kafka.consumer")
  val committerConfig = config.getConfig("akka.kafka.committer")
  val topic = config.getString("kafka.topic")

  val properties = loadProperties("/kafka.properties")

  val producerSettings = ProducerSettings[String, String](producerConfig, new StringSerializer, new StringSerializer)
    .withBootstrapServers(producerConfig.getString("bootstrap.servers"))

  val consumerSettings = ConsumerSettings[String, String](consumerConfig, new StringDeserializer, new StringDeserializer)
    .withBootstrapServers(consumerConfig.getString("bootstrap.servers"))
    .withGroupId(consumerConfig.getString("group.id"))
    .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, consumerConfig.getString("auto.offset.reset"))

  val committerSettings = CommitterSettings(committerConfig)

  val subscriptions = Subscriptions.topics(topic)

  def loadProperties(file: String): Properties = {
    val properties = new Properties()
    properties.load(Source.fromInputStream(getClass.getResourceAsStream(file)).bufferedReader())
    properties
  }
}