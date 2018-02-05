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

package com.stratio.sparta.plugin.workflow.transformation.explode

import com.stratio.sparta.sdk.workflow.enumerators.SaveModeEnum
import com.stratio.sparta.sdk.workflow.step.{OutputOptions, TransformationStepManagement}
import org.apache.spark.sql.Row
import org.apache.spark.sql.catalyst.expressions.GenericRowWithSchema
import org.apache.spark.sql.types.{DoubleType, StringType, StructField, StructType}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{Matchers, WordSpecLike}

@RunWith(classOf[JUnitRunner])
class ExplodeTransformStepStreamTest extends WordSpecLike with Matchers {

  val inputField = "explode"
  val schema = StructType(Seq(StructField(inputField, StringType)))
  val red = "red"
  val blue = "blue"
  val redPrice = 19.95d
  val bluePrice = 10d

  val explodeFieldMap = Map("color" -> red, "price" -> redPrice)

  //scalastyle:off
  "A Explode transformation" should {
    "explode seq of maps field" in {
      val explodeField = Seq(Map("color" -> red, "price" -> redPrice))
      val input = new GenericRowWithSchema(Array(explodeField), schema)
      val outputOptions = OutputOptions(SaveModeEnum.Append, "tableName", None, None)
      val result = new ExplodeTransformStepStream(
        inputField,
        outputOptions,
        TransformationStepManagement(),
        null,
        null,
        Map("schema.fromRow" -> true,
          "inputField" -> inputField,
          "fieldsPreservationPolicy" -> "JUST_EXTRACTED")
      ).parse(input)

      val expected = Seq(Row(red, redPrice.toString))
      expected should be eq result
    }

    "explode seq of maps field with two elements" in {
      val explodeFieldMoreFields = Seq(Map("color" -> red, "price" -> redPrice), Map("color" -> blue, "price" -> bluePrice))
      val input = new GenericRowWithSchema(Array(explodeFieldMoreFields), schema)
      val outputOptions = OutputOptions(SaveModeEnum.Append, "tableName", None, None)
      val result = new ExplodeTransformStepStream(
        inputField,
        outputOptions,
        TransformationStepManagement(),
        null,
        null,
        Map("schema.fromRow" -> true,
          "inputField" -> inputField,
          "fieldsPreservationPolicy" -> "JUST_EXTRACTED")
      ).parse(input)

      val expected = Seq(Row(red, redPrice.toString), Row(blue, bluePrice.toString))
      expected should be eq result
    }

    "explode seq of maps field with two elements with less fields" in {
      val explodeFieldMoreFields = Seq(Map("color" -> red, "price" -> redPrice), Map("color" -> blue))
      val input = new GenericRowWithSchema(Array(explodeFieldMoreFields), schema)
      val outputOptions = OutputOptions(SaveModeEnum.Append, "tableName", None, None)
      val result = new ExplodeTransformStepStream(
        inputField,
        outputOptions,
        TransformationStepManagement(),
        null,
        null,
        Map("schema.fromRow" -> true,
          "inputField" -> inputField,
          "whenRowError" -> "RowDiscard",
          "fieldsPreservationPolicy" -> "JUST_EXTRACTED")
      ).parse(input)

      val expected = Seq(Row(red, redPrice), Row(blue, null))
      expected should be eq result
    }

    "explode map field" in {
      val explodeField = Map("color" -> red, "price" -> redPrice)
      val input = new GenericRowWithSchema(Array(explodeField), schema)
      val outputOptions = OutputOptions(SaveModeEnum.Append, "tableName", None, None)
      val result = new ExplodeTransformStepStream(
        inputField,
        outputOptions,
        TransformationStepManagement(),
        null,
        null,
        Map("schema.fromRow" -> true,
          "inputField" -> inputField,
          "fieldsPreservationPolicy" -> "JUST_EXTRACTED")
      ).parse(input)

      val expected = Seq(Row(red, redPrice.toString))
      expected should be eq result
    }

    "explode seq of row field" in {
      val inputSchema = StructType(Seq(StructField("color", StringType), StructField("price", DoubleType)))
      val explodeField = Seq(new GenericRowWithSchema(Array(red, redPrice), inputSchema))
      val input = new GenericRowWithSchema(Array(explodeField), schema)
      val outputOptions = OutputOptions(SaveModeEnum.Append, "tableName", None, None)
      val result = new ExplodeTransformStepStream(
        inputField,
        outputOptions,
        TransformationStepManagement(),
        null,
        null,
        Map("schema.fromRow" -> true,
          "inputField" -> inputField,
          "fieldsPreservationPolicy" -> "JUST_EXTRACTED")
      ).parse(input)

      val expected = Seq(new GenericRowWithSchema(Array(red, redPrice.toString), inputSchema))
      expected should be eq result
    }

    "explode seq of row field with two rows" in {
      val inputSchema = StructType(Seq(StructField("color", StringType), StructField("price", DoubleType)))
      val explodeField = Seq(
        new GenericRowWithSchema(Array(red, redPrice), inputSchema),
        new GenericRowWithSchema(Array(blue, bluePrice), inputSchema)
      )
      val input = new GenericRowWithSchema(Array(explodeField), schema)
      val outputOptions = OutputOptions(SaveModeEnum.Append, "tableName", None, None)
      val result = new ExplodeTransformStepStream(
        inputField,
        outputOptions,
        TransformationStepManagement(),
        null,
        null,
        Map("schema.fromRow" -> true,
          "inputField" -> inputField,
          "fieldsPreservationPolicy" -> "JUST_EXTRACTED")
      ).parse(input)

      val expected = Seq(
        new GenericRowWithSchema(Array(red, redPrice.toString), inputSchema),
        new GenericRowWithSchema(Array(blue, bluePrice.toString), inputSchema)
      )
      expected should be eq result
    }

    "explode seq of maps field with two elements and schema in fields" in {
      val explodeFieldMoreFields = Seq(
        Map("color" -> red, "price" -> redPrice.toString),
        Map("color" -> blue, "price" -> bluePrice.toString)
      )
      val input = new GenericRowWithSchema(Array(explodeFieldMoreFields), schema)
      val outputOptions = OutputOptions(SaveModeEnum.Append, "tableName", None, None)
      val fields =
        """[
          |{
          |   "name":"color"
          |},
          |{
          |   "name":"price",
          |   "type": "double"
          |}
          |]
          |""".stripMargin
      val result = new ExplodeTransformStepStream(
        inputField,
        outputOptions,
        TransformationStepManagement(),
        null,
        null,
        Map("schema.fromRow" -> false,
          "inputField" -> inputField,
          "schema.inputMode" -> "FIELDS",
          "schema.fields" -> fields.asInstanceOf[java.io.Serializable],
          "fieldsPreservationPolicy" -> "JUST_EXTRACTED")
      ).parse(input)

      val expected = Seq(Row(red, redPrice), Row(blue, bluePrice))
      expected should be eq result
    }

    "explode seq of maps field with two elements and schema in spark format" in {
      val explodeFieldMoreFields = Seq(
        Map("color" -> red, "price" -> redPrice.toString),
        Map("color" -> blue, "price" -> bluePrice.toString)
      )
      val sparkSchema = "StructType((StructField(color,StringType,true),StructField(price,DoubleType,true)))"
      val input = new GenericRowWithSchema(Array(explodeFieldMoreFields), schema)
      val outputOptions = OutputOptions(SaveModeEnum.Append, "tableName", None, None)
      val fields =
        """[
          |{
          |   "name":"color"
          |},
          |{
          |   "name":"price",
          |   "type": "double"
          |}
          |]
          |""".stripMargin
      val result = new ExplodeTransformStepStream(
        inputField,
        outputOptions,
        TransformationStepManagement(),
        null,
        null,
        Map("schema.fromRow" -> false,
          "inputField" -> inputField,
          "schema.inputMode" -> "SPARKFORMAT",
          "schema.sparkSchema" -> sparkSchema,
          "fieldsPreservationPolicy" -> "JUST_EXTRACTED")
      ).parse(input)

      val expected = Seq(Row(red, redPrice), Row(blue, bluePrice))
      expected should be eq result
    }

    "explode seq of maps field with two elements and schema in spark format with REPLACE" in {
      val explodeFieldMoreFields = Seq(
        Map("color" -> red, "price" -> redPrice.toString),
        Map("color" -> blue, "price" -> bluePrice.toString)
      )
      val sparkSchema = "StructType((StructField(color,StringType,true),StructField(price,DoubleType,true)))"
      val schema = StructType(Seq(
        StructField("app", StringType),
        StructField(inputField, StringType)
      ))
      val input = new GenericRowWithSchema(Array("sparta", explodeFieldMoreFields), schema)
      val outputOptions = OutputOptions(SaveModeEnum.Append, "tableName", None, None)
      val fields =
        """[
          |{
          |   "name":"color"
          |},
          |{
          |   "name":"price",
          |   "type": "double"
          |}
          |]
          |""".stripMargin
      val result = new ExplodeTransformStepStream(
        inputField,
        outputOptions,
        TransformationStepManagement(),
        null,
        null,
        Map("schema.fromRow" -> false,
          "inputField" -> inputField,
          "schema.inputMode" -> "SPARKFORMAT",
          "schema.sparkSchema" -> sparkSchema,
          "fieldsPreservationPolicy" -> "REPLACE")
      ).parse(input)

      val expected = Seq(Row("sparta", red, redPrice), Row("sparta", blue, bluePrice))
      expected should be eq result
    }

    "explode seq of maps field with two elements and schema in spark format with APPEND" in {
      val explodeFieldMoreFields = Seq(
        Map("color" -> red, "price" -> redPrice.toString),
        Map("color" -> blue, "price" -> bluePrice.toString)
      )
      val sparkSchema = "StructType((StructField(color,StringType,true),StructField(price,DoubleType,true)))"
      val schema = StructType(Seq(
        StructField("app", StringType),
        StructField(inputField, StringType)
      ))
      val input = new GenericRowWithSchema(Array("sparta", explodeFieldMoreFields), schema)
      val outputOptions = OutputOptions(SaveModeEnum.Append, "tableName", None, None)
      val fields =
        """[
          |{
          |   "name":"color"
          |},
          |{
          |   "name":"price",
          |   "type": "double"
          |}
          |]
          |""".stripMargin
      val result = new ExplodeTransformStepStream(
        inputField,
        outputOptions,
        TransformationStepManagement(),
        null,
        null,
        Map("schema.fromRow" -> false,
          "inputField" -> inputField,
          "schema.inputMode" -> "SPARKFORMAT",
          "schema.sparkSchema" -> sparkSchema,
          "fieldsPreservationPolicy" -> "APPEND")
      ).parse(input)

      val expected = Seq(Row("sparta", explodeFieldMoreFields, red, redPrice), Row("sparta", explodeFieldMoreFields, blue, bluePrice))
      expected should be eq result
    }

    "explode seq of maps field with two elements and schema in spark format with JUST_EXTRACTED" in {
      val explodeFieldMoreFields = Seq(
        Map("color" -> red, "price" -> redPrice.toString),
        Map("color" -> blue, "price" -> bluePrice.toString)
      )
      val sparkSchema = "StructType((StructField(color,StringType,true),StructField(price,DoubleType,true)))"
      val schema = StructType(Seq(
        StructField("app", StringType),
        StructField(inputField, StringType)
      ))
      val input = new GenericRowWithSchema(Array("sparta", explodeFieldMoreFields), schema)
      val outputOptions = OutputOptions(SaveModeEnum.Append, "tableName", None, None)
      val fields =
        """[
          |{
          |   "name":"color"
          |},
          |{
          |   "name":"price",
          |   "type": "double"
          |}
          |]
          |""".stripMargin
      val result = new ExplodeTransformStepStream(
        inputField,
        outputOptions,
        TransformationStepManagement(),
        null,
        null,
        Map("schema.fromRow" -> false,
          "inputField" -> inputField,
          "schema.inputMode" -> "SPARKFORMAT",
          "schema.sparkSchema" -> sparkSchema,
          "fieldsPreservationPolicy" -> "APPEND")
      ).parse(input)

      val expected = Seq(Row(red, redPrice), Row(blue, bluePrice))
      expected should be eq result
    }

  }

}
