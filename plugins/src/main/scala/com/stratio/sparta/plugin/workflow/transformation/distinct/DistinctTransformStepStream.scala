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

package com.stratio.sparta.plugin.workflow.transformation.distinct

import java.io.{Serializable => JSerializable}

import akka.event.slf4j.SLF4JLogging
import com.stratio.sparta.sdk.DistributedMonad
import com.stratio.sparta.sdk.DistributedMonad.Implicits._
import com.stratio.sparta.sdk.workflow.step.OutputOptions
import org.apache.spark.sql.crossdata.XDSession
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.dstream.DStream

class DistinctTransformStepStream(
                                   name: String,
                                   outputOptions: OutputOptions,
                                   ssc: Option[StreamingContext],
                                   xDSession: XDSession,
                                   properties: Map[String, JSerializable]
                                 )
  extends DistinctTransformStep[DStream](name, outputOptions, ssc, xDSession, properties) with SLF4JLogging {

  def transformFunction(inputSchema: String, inputStream: DistributedMonad[DStream]): DistributedMonad[DStream] = {
    inputStream.ds.transform { rdd =>
      if (rdd.isEmpty()) rdd
      else partitions.fold(rdd.distinct()) { numPartitions => rdd.distinct(numPartitions) }
    }
  }

  override def transform(inputData: Map[String, DistributedMonad[DStream]]): DistributedMonad[DStream] =
    applyHeadTransform(inputData)(transformFunction)
}
