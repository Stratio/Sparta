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

package com.stratio.sparta.plugin.workflow.transformation.datetime

import java.io.{Serializable => JSerializable}

import com.stratio.sparta.plugin.TemporalSparkContext
import com.stratio.sparta.sdk.DistributedMonad.Implicits._
import com.stratio.sparta.sdk.workflow.enumerators.SaveModeEnum
import com.stratio.sparta.sdk.workflow.step.{OutputOptions, TransformationStepManagement}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Row
import org.apache.spark.sql.catalyst.expressions.GenericRowWithSchema
import org.apache.spark.sql.types._
import org.junit.runner.RunWith
import org.scalatest.Matchers
import org.scalatest.junit.JUnitRunner

import scala.collection.mutable

@RunWith(classOf[JUnitRunner])
class DateTimeTransformStepBatchIT extends TemporalSparkContext with Matchers {

  val inputField = Some("ts")
  val outputsFields = Seq("ts")
  val formatFromStandard = Map("formatFrom" -> "standard")
  val formatFromAutoGenerated = Map("formatFrom" -> "autogenerated")
  val formatFromUser = Map("formatFrom" -> "user")

  //scalastyle:off
  "A DateTimeTransform" should "parse unixMillis to string and add a Timestamp" in {
    val schema = StructType(Seq(StructField("eventID", StringType), StructField("creationDate", LongType)))
    val schemaOutput = StructType(Seq(StructField("eventID", StringType), StructField("creationDate", StringType), StructField("timestamp", StringType)))
    val data1 = Seq(
      new GenericRowWithSchema(Array("ADFGHJKGHG1325", 1416330799999L), schema).asInstanceOf[Row],
      new GenericRowWithSchema(Array("ADFGHJKGHG1325", 1416330799999L), schema).asInstanceOf[Row]
    )
    val inputRdd = sc.parallelize(data1)
    val inputData = Map("step1" -> inputRdd)
    val outputOptions = OutputOptions(SaveModeEnum.Append, "tableName", None, None)
    val dataDatetime = Seq(
      new GenericRowWithSchema(Array("ADFGHJKGHG1235", "1416330788000", ""), schemaOutput),
      new GenericRowWithSchema(Array("ADFGHJKGHG1325", "1416330799999", ""), schemaOutput)
    )
    val fieldsDatetime =
      """[
        |{
        |"inputField":"creationDate",
        |"formatFrom":"STANDARD",
        |"userFormat":"",
        |"standardFormat":"unixMillis",
        |"localeTime":"ENGLISH",
        |"granularityNumber":"",
        |"granularityTime":"millisecond",
        |"fieldsPreservationPolicy":"REPLACE",
        |"outputFieldName":"creationDate",
        |"outputFieldType":"string",
        |"outputFormatFrom": "DEFAULT"
        |},
        |{
        |"inputField":"creationDate",
        |"formatFrom":"AUTOGENERATED",
        |"localeTime":"",
        |"userFormat":"",
        |"standardFormat":"unixMillis",
        |"granularityNumber":"",
        |"granularityTime":"millisecond",
        |"fieldsPreservationPolicy":"APPEND",
        |"outputFieldName":"timestamp",
        |"outputFieldType":"string",
        |"outputFormatFrom": "DEFAULT"
        |}
        |]
        |""".stripMargin
    val result = new DateTimeTransformStepBatch(
      "transformTimestamp",
      outputOptions,
      TransformationStepManagement(),
      Some(ssc),
      sparkSession,
      Map("fieldsDatetime" -> fieldsDatetime.asInstanceOf[JSerializable])
    ).transform(inputData)
    val streamingEvents = result.ds.count()
    val streamingRegisters = result.ds.collect()

    streamingRegisters.foreach { row =>
      assert(row.size == 3)
      assert(row.schema == dataDatetime.head.schema)
    }
    assert(streamingEvents === 2)

  }
}