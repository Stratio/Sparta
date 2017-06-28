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
package com.stratio.sparta.plugin.input.fileSystem

import java.io.{Serializable => JSerializable}

import com.stratio.sparta.sdk.properties.ValidatingPropertyMap._
import com.stratio.sparta.sdk.pipeline.input.Input
import org.apache.spark.sql.Row
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.dstream.DStream


/**
  * This input creates a dataFrame given a path to an HDFS-compatible file.
  * Spark will monitor the directory and will only create dataFrames
  * from new entries.
  * @param properties
  */
class FileSystemInput(properties: Map[String, JSerializable]) extends Input(properties) {

  def initStream(ssc: StreamingContext): DStream[Row] = {

    ssc.textFileStream(properties.getString("directory", "")).map(data => Row(data))
  }
}
