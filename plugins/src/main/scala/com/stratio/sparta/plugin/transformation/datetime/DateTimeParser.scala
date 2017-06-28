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

import java.io.{Serializable => JSerializable}
import java.lang.reflect.Method
import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}

import com.github.nscala_time.time.Imports._
import com.stratio.sparta.plugin.transformation.datetime.DateFormatEnum.DateFormat
import com.stratio.sparta.sdk.pipeline.transformation.Parser
import com.stratio.sparta.sdk.properties.ValidatingPropertyMap._
import com.stratio.sparta.sdk.utils.AggregationTime._
import org.apache.spark.sql.Row
import org.apache.spark.sql.types.StructType
import org.joda.time.format.{DateTimeFormatter, ISODateTimeFormat}

import scala.util.Try

class DateTimeParser(order: Integer,
                     inputField: Option[String],
                     outputFields: Seq[String],
                     schema: StructType,
                     properties: Map[String, JSerializable])
  extends Parser(order, inputField, outputFields, schema, properties) {

  val FormatFrom: DateFormat = DateFormatEnum.withName(properties.getString("formatFrom", "AUTOGENERATED").toUpperCase)
  val StandardFormat: Option[String] = properties.getString("standardFormat", None)
  val UserFormat: Option[String] = properties.getString("userFormat", None)
  val GranularityProperty: Option[String] = properties.getString(GranularityPropertyName, None)

  //scalastyle:off
  override def parse(row: Row): Seq[Row] = {
    val inputValue = Option(row.get(inputFieldIndex))
    val newData = Try {
      outputFields.map(outputField => {
        val outputSchemaValid = outputFieldsSchema.find(field => field.name == outputField)
        outputSchemaValid match {
          case Some(outSchema) =>
            if (FormatFrom == DateFormatEnum.AUTOGENERATED)
              parseToOutputType(outSchema, applyGranularity(new DateTime()))
            else {
              inputValue match {
                case Some(value: DateTime) =>
                  parseToOutputType(outSchema, applyGranularity(value))
                case Some(value: String) =>
                  if (value.isEmpty)
                    returnWhenError(new IllegalStateException(
                      s"Impossible to parse because value for field: ${outSchema.name} is empty"))
                  else parseToOutputType(outSchema, applyGranularity(parseDate(value)))
                case Some(value: Array[Byte]) =>
                  val valueCasted = new Predef.String(value)
                  if (value.isEmpty)
                    returnWhenError(new IllegalStateException(
                      s"Impossible to parse because value for field: ${outSchema.name} is empty"))
                  else parseToOutputType(outSchema, applyGranularity(parseDate(valueCasted)))
                case Some(value) =>
                  parseToOutputType(outSchema, applyGranularity(parseDate(value)))
                case None =>
                  returnWhenError(new IllegalStateException(
                    s"Impossible to parse because value for field: ${outSchema.name} is empty"))
              }
            }
          case None =>
            returnWhenError(new IllegalStateException(s"Impossible to parse outputField: $outputField in the schema"))
        }
      })
    }

    returnData(newData, removeInputField(row))
  }

  def parseDate(inputValue: Any): DateTime = {
    FormatFrom match {
      case DateFormatEnum.STANDARD =>
        StandardFormat match {
          case Some("unix") =>
            new DateTime(inputValue.toString.toLong * 1000L)
          case Some("unixMillis") =>
            new DateTime(inputValue.toString.toLong)
          case Some("hive") =>
            new DateTime(getDateFromFormat(inputValue.toString))
          case Some(format) =>
            val formats = DateTimeParser.FormatMethods
            if (formats.contains(format))
              formats(format).invoke(None).asInstanceOf[DateTimeFormatter].parseDateTime(inputValue.toString)
            else throw new IllegalStateException(s"The standard date format is not valid")
          case None =>
            throw new IllegalStateException(s"The standard date format is not valid")
        }
      case DateFormatEnum.USER =>
        UserFormat match {
          case Some(format) => new DateTime(getDateFromFormat(inputValue.toString, format))
          case None => throw new IllegalStateException(s"The user date format is not valid")
        }
      case _ =>
        throw new IllegalStateException(s"The format is not supported")
    }
  }

  //scalastyle:on

  def applyGranularity(inputValue: DateTime): Long =
    GranularityProperty.fold(inputValue.getMillis) { granularity => truncateDate(inputValue, granularity) }

  def getDateFromFormat(inputDate: String, format: String = "yyyy-MM-dd HH:mm:ss"): Date = {
    val sdf = new SimpleDateFormat(format)
    if (!format.contains("T") && !format.contains("z") && !format.contains("Z"))
      sdf.setTimeZone(TimeZone.getTimeZone("UTC"))
    sdf.parse(inputDate)
  }
}

object DateTimeParser {

  val FormatMethods: Map[String, Method] = classOf[ISODateTimeFormat].getMethods.toSeq.map(x => (x.getName, x)).toMap
}

object DateFormatEnum extends Enumeration {

  type DateFormat = Value
  val AUTOGENERATED, STANDARD, USER = Value

}