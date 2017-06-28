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

package com.stratio.sparta.plugin.transformation.datetime

import com.stratio.sparta.sdk.properties.JsoneyString
import org.apache.spark.sql.Row
import org.apache.spark.sql.types.{StringType, StructField, StructType}
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{Matchers, WordSpecLike}

@RunWith(classOf[JUnitRunner])
class DateTimeParserTest extends WordSpecLike with Matchers {

  val inputField = Some("ts")
  val outputsFields = Seq("ts")
  val formatFromStandard = Map("formatFrom" -> "standard")
  val formatFromAutoGenerated = Map("formatFrom" -> "autogenerated")
  val formatFromUser = Map("formatFrom" -> "user")

  //scalastyle:off
  "A DateTimeParser" should {
    "parse unixMillis to string" in {
      val input = Row(1416330788000L)
      val schema = StructType(Seq(StructField("ts", StringType)))

      val result = new DateTimeParser(1, inputField, outputsFields, schema,
        Map("standardFormat" -> "unixMillis") ++ formatFromStandard).parse(input)

      val expected = Seq(Row(1416330788000L, "1416330788000"))

      assertResult(result)(expected)
    }

    "parse unix to string" in {
      val input = Row(1416330788)
      val schema = StructType(Seq(StructField("ts", StringType)))

      val result =
        new DateTimeParser(1, inputField, outputsFields, schema, Map("standardFormat" -> "unix") ++ formatFromStandard)
          .parse(input)

      val expected = Seq(Row(1416330788, "1416330788000"))

      assertResult(result)(expected)
    }

    "parse unix to string removing raw" in {
      val input = Row(1416330788)
      val schema = StructType(Seq(StructField("ts", StringType)))

      val result =
        new DateTimeParser(1, inputField, outputsFields, schema, Map("standardFormat" -> "unix",
          "removeInputField" -> JsoneyString.apply("true")) ++ formatFromStandard)
          .parse(input)

      val expected = Seq(Row("1416330788000"))

      assertResult(result)(expected)
    }

    "not parse anything if the field does not match" in {
      val input = Row("1212")
      val schema = StructType(Seq(StructField("otherField", StringType)))

      an[IllegalStateException] should be thrownBy new DateTimeParser(1, inputField, outputsFields, schema,
        Map("standardFormat" -> "unixMillis") ++ formatFromStandard).parse(input)
    }

    "not parse anything and generate a new Date" in {
      val input = Row("anything")
      val schema = StructType(Seq(StructField("ts", StringType)))

      val result =
        new DateTimeParser(1, inputField, outputsFields, schema,
          Map("autoGenerated" -> "true")  ++ formatFromAutoGenerated).parse(input)

      assertResult(result.head.size)(2)
    }

    "Auto generated with inputFormat autogenerated" in {
      val input = Row("1416330788")
      val schema = StructType(Seq(StructField("ts", StringType)))

      val result =
        new DateTimeParser(1, inputField, outputsFields, schema,
          Map("autoGenerated" -> "true")  ++ formatFromAutoGenerated).parse(input)

      assertResult(result.head.size)(2)
    }

    "parse dateTime in hive format" in {
      val input = Row("2015-11-08 15:58:58")
      val schema = StructType(Seq(StructField("ts", StringType)))

      val result =
        new DateTimeParser(1, inputField, outputsFields, schema,
          Map("standardFormat" -> "hive") ++ formatFromStandard).parse(input)

      val expected = Seq(Row("2015-11-08 15:58:58", "1446998338000"))

      assertResult(result)(expected)
    }
  }
}