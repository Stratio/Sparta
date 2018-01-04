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

package com.stratio.sparta.plugin.workflow.transformation.json

import java.io.{Serializable => JSerializable}

import com.stratio.sparta.plugin.enumerations.FieldsPreservationPolicy
import com.stratio.sparta.plugin.helper.SchemaHelper._
import com.stratio.sparta.sdk.DistributedMonad
import com.stratio.sparta.sdk.properties.ValidatingPropertyMap._
import com.stratio.sparta.sdk.properties.models.PropertiesQueriesModel
import com.stratio.sparta.sdk.workflow.step._
import org.apache.spark.sql.Row
import org.apache.spark.sql.catalyst.expressions.GenericRowWithSchema
import org.apache.spark.sql.crossdata.XDSession
import org.apache.spark.sql.types.StructField
import org.apache.spark.streaming.StreamingContext

import scala.util.Try

abstract class JsonPathTransformStep[Underlying[Row]](
                                                       name: String,
                                                       outputOptions: OutputOptions,
                                                       ssc: Option[StreamingContext],
                                                       xDSession: XDSession,
                                                       properties: Map[String, JSerializable]
                                                     )(implicit dsMonadEvidence: Underlying[Row] => DistributedMonad[Underlying])
  extends TransformStep[Underlying](name, outputOptions, ssc, xDSession, properties)
    with ErrorCheckingStepRow
    with SchemaCasting {

  lazy val queriesModel: PropertiesQueriesModel = properties.getPropertiesQueries("queries")

  lazy val supportNullValues: Boolean = properties.getBoolean("supportNullValues", default = true)

  lazy val inputField: String = properties.getString("inputField")

  lazy val preservationPolicy: FieldsPreservationPolicy.Value = FieldsPreservationPolicy.withName(
    properties.getString("fieldsPreservationPolicy", "REPLACE").toUpperCase)

  lazy val outputFieldsSchema = queriesModel.queries.map { queryField =>
    val outputType = queryField.`type`.notBlank.getOrElse("string")
    StructField(
      name = queryField.field,
      dataType = SparkTypes.get(outputType) match {
        case Some(sparkType) => sparkType
        case None => schemaFromString(outputType)
      },
      nullable = queryField.nullable.getOrElse(true)
    )
  }
  assert(inputField.nonEmpty)

  override def transform(inputData: Map[String, DistributedMonad[Underlying]]): DistributedMonad[Underlying] =
    applyHeadTransform(inputData) { (inputSchema, inputStream) =>
      inputStream.flatMap(data => parse(data))
    }

  //scalastyle:off
  def parse(row: Row): Seq[Row] = returnSeqDataFromRow {
    val inputSchema = row.schema
    val outputSchema = getNewOutputSchema(inputSchema, preservationPolicy, outputFieldsSchema, inputField)
    val inputFieldIndex = inputSchema.fieldIndex(inputField)
    val inputValue = Option(row.get(inputFieldIndex))
    val newValues = inputValue match {
      case Some(value) =>
        if (value.toString.nonEmpty) {
          val valuesParsed = value match {
            case valueCast: Array[Byte] =>
              JsonPathTransformStep.jsonParse(new Predef.String(valueCast), queriesModel, supportNullValues)
            case valueCast: String =>
              JsonPathTransformStep.jsonParse(valueCast, queriesModel, supportNullValues)
            case _ =>
              JsonPathTransformStep.jsonParse(value.toString, queriesModel, supportNullValues)
          }
          outputSchema.map { outputField =>
            valuesParsed.get(outputField.name) match {
              case Some(valueParsed) if valueParsed != null | (valueParsed == null && supportNullValues) =>
                castingToOutputSchema(outputField, valueParsed)
              case _ =>
                Try(row.get(inputSchema.fieldIndex(outputField.name))).getOrElse(returnWhenError(
                  new Exception(s"Impossible to parse outputField: $outputField in the schema")))
            }
          }
        } else throw new Exception(s"The input value is empty")
      case None => throw new Exception(s"The input value is null")
    }
    new GenericRowWithSchema(newValues.toArray, outputSchema)
  }
}

object JsonPathTransformStep {

  def jsonParse(jsonData: String, queriesModel: PropertiesQueriesModel, isLeafToNull: Boolean): Map[String, Any] = {
    val jsonPathExtractor = new JsonPathExtractor(jsonData, isLeafToNull)

    queriesModel.queries.map(queryModel => (queryModel.field, jsonPathExtractor.query(queryModel.query))).toMap
  }
}
