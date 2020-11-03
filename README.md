Akka Streams Kafka
------------------
>Akka Streams and Alpakka Kafka Connector feature tests.

Installation
------------
1. brew tap homebrew/services
2. brew install scala
3. brew install sbt
4. brew install zookeeper
5. brew install kafka

Kafka
-----
>You may have to set these Kafka server properties ( /usr/local/etc/kafka/server.properties ):

1. listeners=PLAINTEXT://0.0.0.0:9092
2. advertised.listeners=PLAINTEXT://127.0.0.1:9092

Service
-------
1. brew services start zookeeper & kafka
2. brew services stop kafka & zookeeper

Test
----
1. sbt clean test

Kafka
-----
* kafka-topics --zookeeper localhost:2181 --list
* kafka-topics --zookeeper localhost:2181 --describe --topic kv
* kafka-run-class kafka.tools.GetOffsetShell --broker-list localhost:9092 --topic kv --time -1
* kafka-consumer-groups --bootstrap-server localhost:9092 --group objektwerks-group --describe
* kafka-topics --zookeeper localhost:2181 --delete --topic kv
* kafka-consumer-groups --bootstrap-server localhost:9092 --list
* kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group objektwerks-group