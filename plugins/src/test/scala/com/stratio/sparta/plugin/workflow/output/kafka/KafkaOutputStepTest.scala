/*
 * Copyright (C) 2015 Stratio (http://stratio.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.stratio.sparta.plugin.workflow.output.kafka

import java.io.Serializable
import java.util.Properties

import com.stratio.sparta.plugin.TemporalSparkContext
import org.junit.runner.RunWith
import org.scalatest._
import org.scalatest.junit.JUnitRunner

import scala.util._

@RunWith(classOf[JUnitRunner])
class KafkaOutputStepTest extends TemporalSparkContext with Matchers {

  val mandatoryOptions: Map[String, Serializable] = Map(
    "bootstrap.servers" -> """[{"host":"localhost","port":"9092"}]""",
    "key.serializer" -> "org.apache.kafka.common.serialization.StringSerializer",
    "value.serializer" -> "com.stratio.sparta.plugin.common.kafka.serializers.RowSerializer",
    "acks" -> "1",
    "batch.size" -> "200")

  val validProperties: Map[String, Serializable] = Map(
    "bootstrap.servers" -> """[{"host":"localhost","port":"9092"},{"host":"localhost2","port":"90922"}]""",
    "key.serializer" -> "org.apache.kafka.common.serialization.StringSerializer",
    "value.serializer" -> "com.stratio.sparta.plugin.common.kafka.serializers.RowSerializer",
    "acks" -> "all",
    "batch.size" -> 200
  )

  val noValidProperties: Map[String, Serializable] = Map(
    "bootstrap.servers" -> "",
    "key.serializer" -> "",
    "acks" -> "",
    "batch.size" -> ""
  )

  "extractOptions" should "extract mandatory options" in {
    val kafkatest = new KafkaOutputStep("kafka", sparkSession, mandatoryOptions)

    val options = kafkatest.mandatoryOptions
    options.size shouldBe 5
    options("bootstrap.servers") shouldBe "localhost:9092"
    options("key.serializer") shouldBe "org.apache.kafka.common.serialization.StringSerializer"
    options("value.serializer") shouldBe "com.stratio.sparta.plugin.common.kafka.serializers.RowSerializer"
    options("acks") shouldBe "1"
    options("batch.size") shouldBe "200"
  }

  "extractOptions" should "extract default mandatory options when map is empty" in {

    val kafkatest = new KafkaOutputStep("kafka", sparkSession, Map.empty)

    val options = kafkatest.mandatoryOptions
    options.size shouldBe 4
    options("key.serializer") shouldBe "org.apache.kafka.common.serialization.StringSerializer"
    options("value.serializer") shouldBe "com.stratio.sparta.plugin.common.kafka.serializers.RowSerializer"
    options("acks") shouldBe "0"
    options("batch.size") shouldBe "200"
  }

  "extractOptions" should "create a correct properties file" in {
    val kafkatest = new KafkaOutputStep("kafka", sparkSession, mandatoryOptions)

    val options: Properties = KafkaOutput.createProducerProps(kafkatest.properties,
      kafkatest.mandatoryOptions ++ kafkatest.getCustomProperties )
    options.size shouldBe 5
    options.get("bootstrap.servers") shouldBe "localhost:9092"
    options.get("key.serializer") shouldBe "org.apache.kafka.common.serialization.StringSerializer"
    options.get("value.serializer") shouldBe "com.stratio.sparta.plugin.common.kafka.serializers.RowSerializer"
    options.get("acks") shouldBe "1"
    options.get("batch.size") shouldBe "200"
  }

  "createProducer" should "return a valid KafkaProducer" in {
    val kafkatest = new KafkaOutputStep("kafka", sparkSession, mandatoryOptions)

    val securityProperties: Map[String, AnyRef] = Map.empty
    val options: Map[String, String] = Map.empty
    val createProducer = Try(KafkaOutput.getProducer("key", kafkatest.properties, securityProperties,
      kafkatest.mandatoryOptions ++ kafkatest.getCustomProperties))

    createProducer match {
      case Success(some) => log.info("Test OK!")
      case Failure(e) => log.error("Test KO", e)
    }

    createProducer.isSuccess shouldBe true
  }
}
