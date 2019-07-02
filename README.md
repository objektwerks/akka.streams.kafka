Kafka
-----
>The purpose of this project is to test Kafka features.

***

Homebrew
--------
>Install Homebrew on OSX.

Installation
------------
>Install the following packages via Homebrew:

1. brew tap homebrew/services
2. brew install scala
3. brew install sbt
4. brew install zookeeper
5. brew install kafka

Service
-------
>Start:

1. brew services start zookeeper
2. brew services start kafka

>Stop:

1. brew services stop kafka
2. brew services stop zookeeper

Test
----
1. sbt clean test

Kafka
-----
* kafka-topics --zookeeper localhost:2181 --list
* kafka-topics --zookeeper localhost:2181 --delete --topic kv
* kafka-consumer-groups --bootstrap-server localhost:9092 --list
* kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group objektwerks-group