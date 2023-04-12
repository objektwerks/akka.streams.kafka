package objektwerks

import io.github.embeddedkafka._

import akka.kafka.{CommitterSettings, ConsumerSettings, ProducerSettings, Subscriptions}

import com.typesafe.config.ConfigFactory

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}

import scala.io.Source

final class Conf extends EmbeddedKafka {
  val config = ConfigFactory.load("app.conf")

  val producerConfig = config.getConfig("akka.kafka.producer")
  val producerSettings = ProducerSettings[String, String](
    producerConfig,
    new StringSerializer,
    new StringSerializer
  )
  .withBootstrapServers(producerConfig.getString("bootstrap.servers"))

  val consumerConfig = config.getConfig("akka.kafka.consumer")
  val consumerSettings = ConsumerSettings[String, String](
    consumerConfig,
    new StringDeserializer,
    new StringDeserializer
  )
  .withBootstrapServers(consumerConfig.getString("bootstrap.servers"))
  .withGroupId(consumerConfig.getString("group.id"))
  .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, consumerConfig.getString("auto.offset.reset"))

  val committerConfig = config.getConfig("akka.kafka.committer")
  val committerSettings = CommitterSettings(committerConfig)

  val topic = config.getString("kafka.topic")
  val subscriptions = Subscriptions.topics(topic)
}