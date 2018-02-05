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

package com.stratio.sparta.plugin.workflow.transformation.trigger

import java.io.{Serializable => JSerializable}

import akka.event.slf4j.SLF4JLogging
import com.stratio.sparta.sdk.DistributedMonad
import com.stratio.sparta.sdk.properties.ValidatingPropertyMap._
import com.stratio.sparta.sdk.workflow.step.{OutputOptions, TransformStep, TransformationStepManagement}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Row
import org.apache.spark.sql.crossdata.XDSession
import org.apache.spark.streaming.StreamingContext

import scala.util.{Failure, Success, Try}

abstract class TriggerTransformStep[Underlying[Row]](
                                                      name: String,
                                                      outputOptions: OutputOptions,
                                                      transformationStepsManagement: TransformationStepManagement,
                                                      ssc: Option[StreamingContext],
                                                      xDSession: XDSession,
                                                      properties: Map[String, JSerializable]
                                                    )(implicit dsMonadEvidence: Underlying[Row] => DistributedMonad[Underlying])
  extends TransformStep[Underlying](name, outputOptions, transformationStepsManagement, ssc, xDSession, properties)
    with SLF4JLogging {

  lazy val sql = Try(properties.getString("sql"))
    .getOrElse(throw new IllegalArgumentException("Is mandatory one sql query"))

  def executeSQL: RDD[Row] = {
    log.debug(s"Executing query in Spark: $sql")

    val queryDf = Try(xDSession.sql(sql)) match {
      case Success(sqlResult) => sqlResult
      case Failure(exception: org.apache.spark.sql.AnalysisException) =>
        val info = s"Error while running analysis in Catalyst, query $sql in the trigger $name"
        log.warn(info, exception)
        throw new RuntimeException(info, exception)
      case Failure(exception) =>
        val info = s"Error while running query $sql in the trigger $name"
        log.warn(info, exception)
        throw new RuntimeException(info, exception)
    }

    queryDf.rdd
  }

  def isCorrectTableName(tableName: String): Boolean =
    tableName.nonEmpty && tableName != "" &&
      tableName.toLowerCase != "select" &&
      tableName.toLowerCase != "project" &&
      !tableName.contains("-") && !tableName.contains("*") && !tableName.contains("/")
}

