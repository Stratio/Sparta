/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.sparta.plugin.workflow.input.crossdata

import java.io.{Serializable => JSerializable}

import akka.event.slf4j.SLF4JLogging
import com.stratio.sparta.core.DistributedMonad
import com.stratio.sparta.core.DistributedMonad.Implicits._
import com.stratio.sparta.core.models.OutputOptions
import com.stratio.sparta.plugin.helper.SecurityHelper
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.crossdata.XDSession
import org.apache.spark.sql.types.StructType
import org.apache.spark.streaming.StreamingContext

class CrossdataInputStepBatch(
                               name: String,
                               outputOptions: OutputOptions,
                               ssc: Option[StreamingContext],
                               xDSession: XDSession,
                               properties: Map[String, JSerializable]
                             )
  extends CrossdataInputStep[RDD](name, outputOptions, ssc, xDSession, properties) {

  override def init(): DistributedMonad[RDD] = {
    throw new Exception("Not used on inputs that generates Datasets with schema")
  }

  override def initWithSchema(): (DistributedMonad[RDD], Option[StructType]) = {
    require(maybeCrossdataInfo.isDefined, "Could not extract Crossdata database and table")

    val df = xDSession.sql(query)
    (df.rdd, Option(df.schema))
  }

}

object CrossdataInputStepBatch {

  def getSparkSubmitConfiguration(configuration: Map[String, JSerializable]): Seq[(String, String)] = {
    SecurityHelper.dataStoreSecurityConf(configuration)
  }

}