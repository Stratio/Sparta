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

package com.stratio.sparta.plugin.common.kafka

import java.io.{Serializable => JSerializable}

import akka.event.slf4j.SLF4JLogging
import com.stratio.sparta.sdk.properties.ValidatingPropertyMap._
import org.apache.spark.SparkConf

import scala.util.{Failure, Success, Try}

trait KafkaBase extends SLF4JLogging {

  lazy val DefaultHost = "localhost"
  lazy val DefaultBrokerPort = "9092"
  lazy val EmptyOpts = Map.empty[String, AnyRef]

  val properties: Map[String, JSerializable]

  /** HOSTS and PORT extractions **/

  def getHostPort(key: String,
                  defaultHost: String,
                  defaultPort: String): Map[String, String] = {
    val connection = try {
      if(properties.contains(key))
        properties.getHostsPorts(key).hostsPorts
        .map(hostHortModel => s"${hostHortModel.host}:${hostHortModel.port}")
        .mkString(",")
      else s"$defaultHost:$defaultPort"
    } catch {
      case e: Exception =>
        log.warn("Error extracting kafka connection chain, using default values...", e)
        s"$defaultHost:$defaultPort"
    }

    Map(key -> connection)
  }

  /** SECURITY OPTIONS **/

  def securityOptions(sparkConf: Map[String, String]): Map[String, AnyRef] = {
    val prefixKafka = "spark.ssl.kafka."
    if (sparkConf.get(prefixKafka + "enabled").isDefined && sparkConf(prefixKafka + "enabled") == "true") {
      val configKafka = sparkConf.flatMap { case (key, value) =>
        if (key.startsWith(prefixKafka))
          Option(key.replace(prefixKafka, "") -> value)
        else None
      }

      Try(securityMapOptions(configKafka)) match {
        case Success(options) =>
          options
        case Failure(e) =>
          log.warn("Error getting spark security, some of this configurations is missing or invalid", e)
          EmptyOpts
      }
    } else EmptyOpts
  }

  def securityOptions(sparkConf: SparkConf): Map[String, AnyRef] = {
    val prefixKafka = "spark.ssl.kafka."
    if (sparkConf.getOption(prefixKafka + "enabled").isDefined && sparkConf.get(prefixKafka + "enabled") == "true")
      try {
        securityMapOptions(sparkConf.getAllWithPrefix(prefixKafka).toMap)
      } catch {
        case e: Exception =>
          log.warn("Error getting spark security, some of this configurations is missing or invalid", e)
          EmptyOpts
      }
    else EmptyOpts
  }

  private def securityMapOptions(configKafka: Map[String, String]): Map[String, AnyRef] =
    Map(
      "security.protocol" -> "SSL",
      "ssl.key.password" -> configKafka("keyPassword"),
      "ssl.keystore.location" -> configKafka("keyStore"),
      "ssl.keystore.password" -> configKafka("keyStorePassword"),
      "ssl.truststore.location" -> configKafka("trustStore"),
      "ssl.truststore.password" -> configKafka("trustStorePassword")
    )
}