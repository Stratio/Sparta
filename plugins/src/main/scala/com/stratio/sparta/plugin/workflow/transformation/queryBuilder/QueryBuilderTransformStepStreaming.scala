/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.sparta.plugin.workflow.transformation.queryBuilder

import java.io.{Serializable => JSerializable}

import akka.event.slf4j.SLF4JLogging
import com.stratio.sparta.core.DistributedMonad
import com.stratio.sparta.core.DistributedMonad.Implicits._
import com.stratio.sparta.core.models.{OutputOptions, TransformationStepManagement}
import com.stratio.sparta.plugin.workflow.transformation.trigger.TriggerTransformStepStreaming
import org.apache.spark.sql.crossdata.XDSession
import org.apache.spark.sql.types.StructType
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.dstream.DStream

//scalastyle:off

class QueryBuilderTransformStepStreaming(
                                         name: String,
                                         outputOptions: OutputOptions,
                                         transformationStepsManagement: TransformationStepManagement,
                                         ssc: Option[StreamingContext],
                                         xDSession: XDSession,
                                         properties: Map[String, JSerializable]
                                       )
  extends QueryBuilderTransformStep[DStream](name, outputOptions, transformationStepsManagement, ssc, xDSession, properties)
    with SLF4JLogging {

  lazy val triggerStreaming = new TriggerTransformStepStreaming(
    name,
    outputOptions,
    transformationStepsManagement,
    ssc,
    xDSession,
    properties ++ Map("sql" -> sql)
  )

  override def transformWithDiscards(
                                      inputData: Map[String, DistributedMonad[DStream]]
                                    ): (DistributedMonad[DStream], Option[StructType], Option[DistributedMonad[DStream]], Option[StructType]) = {
    requireValidateSql(inputData.size)
    triggerStreaming.transformWithDiscards(inputData)
  }

}
