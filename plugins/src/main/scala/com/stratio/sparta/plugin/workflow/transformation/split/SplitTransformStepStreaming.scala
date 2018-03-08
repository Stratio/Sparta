/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.sparta.plugin.workflow.transformation.split

import java.io.{Serializable => JSerializable}

import com.stratio.sparta.sdk.workflow.step.{OutputOptions, TransformationStepManagement}
import org.apache.spark.sql.crossdata.XDSession
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.dstream.DStream
import com.stratio.sparta.sdk.DistributedMonad.Implicits._

class SplitTransformStepStreaming(
                              name: String,
                              outputOptions: OutputOptions,
                              transformationStepsManagement: TransformationStepManagement,
                              ssc: Option[StreamingContext],
                              xDSession: XDSession,
                              properties: Map[String, JSerializable]
                            ) extends SplitTransformStep[DStream](name, outputOptions, transformationStepsManagement, ssc, xDSession, properties)