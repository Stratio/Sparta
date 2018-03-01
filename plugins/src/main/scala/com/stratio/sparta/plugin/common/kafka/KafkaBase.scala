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
import com.stratio.sparta.sdk.properties.JsoneyStringSerializer
import com.stratio.sparta.sdk.properties.ValidatingPropertyMap._
import com.stratio.sparta.sdk.properties.models.HostsPortsModel
import org.json4s.jackson.Serialization.read
import org.json4s.{DefaultFormats, Formats}

trait KafkaBase extends SLF4JLogging {

  lazy val DefaultHost = "localhost"
  lazy val DefaultBrokerPort = "9092"
  lazy val EmptyOpts = Map.empty[String, AnyRef]

  val properties: Map[String, JSerializable]

  /** HOSTS and PORT extractions **/

  def getHostPort(key: String): Map[String, String] = {
    val connection = try {
      if (properties.contains(key)) {
        implicit val json4sJacksonFormats: Formats = DefaultFormats + new JsoneyStringSerializer()
        val hostsPortsModel = read[HostsPortsModel](
          s"""{"hostsPorts": ${properties.getString(key, None).notBlank.fold("[]") { values => values.toString }}}"""
        )
        if (hostsPortsModel.hostsPorts.nonEmpty)
          Option(hostsPortsModel.hostsPorts.map(hostHortModel =>
            s"${hostHortModel.host}:${hostHortModel.port}").mkString(",")
          )
        else None
      } else None
    } catch {
      case e: Exception =>
        log.warn(s"Error extracting kafka connection chain, using default values... Error: ${e.getLocalizedMessage}")
        None
    }

    connection match {
      case Some(connectionKey) => Map(key -> connectionKey)
      case None => Map.empty
    }
  }

}
