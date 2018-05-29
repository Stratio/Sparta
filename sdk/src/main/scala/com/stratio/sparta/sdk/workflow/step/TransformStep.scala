/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.sparta.sdk.workflow.step

import java.io.{Serializable => JSerializable}

import akka.event.slf4j.SLF4JLogging
import com.stratio.sparta.sdk.DistributedMonad
import com.stratio.sparta.sdk.DistributedMonad.DistributedMonadImplicits
import com.stratio.sparta.sdk.enumerators.WhenError.WhenError
import com.stratio.sparta.sdk.enumerators.WhenFieldError.WhenFieldError
import com.stratio.sparta.sdk.enumerators.WhenRowError.WhenRowError
import com.stratio.sparta.sdk.enumerators.{WhenError, WhenFieldError, WhenRowError}
import com.stratio.sparta.sdk.helpers.{CastingHelper, SdkSchemaHelper}
import com.stratio.sparta.sdk.models._
import com.stratio.sparta.sdk.properties.ValidatingPropertyMap._
import com.stratio.sparta.sdk.properties.{JsoneyStringSerializer, Parameterizable}
import org.apache.spark.sql.crossdata.XDSession
import org.apache.spark.sql.types.{StructField, StructType}
import org.apache.spark.streaming.StreamingContext
import org.json4s.jackson.Serialization.read
import org.json4s.{DefaultFormats, Formats}

import scala.util.{Failure, Success, Try}

//scalastyle:off
abstract class TransformStep[Underlying[Row]](
                                               val name: String,
                                               val outputOptions: OutputOptions,
                                               val transformationStepsManagement: TransformationStepManagement,
                                               @transient private[sparta] val ssc: Option[StreamingContext],
                                               @transient private[sparta] val xDSession: XDSession,
                                               properties: Map[String, JSerializable]
                                             ) extends Parameterizable(properties) with GraphStep
  with DistributedMonadImplicits with SLF4JLogging {


  override lazy val customKey = "transformationOptions"
  override lazy val customPropertyKey = "transformationOptionsKey"
  override lazy val customPropertyValue = "transformationOptionsValue"

  lazy val whenErrorDo: WhenError = WhenError.withName(propertiesWithCustom.getString("whenError", None)
    .notBlank.getOrElse(transformationStepsManagement.whenError.toString))

  lazy val whenRowErrorDo: WhenRowError = WhenRowError.withName(propertiesWithCustom.getString("whenRowError", None)
    .notBlank.getOrElse(transformationStepsManagement.whenRowError.toString))

  lazy val whenFieldErrorDo: WhenFieldError = WhenFieldError.withName(
    propertiesWithCustom.getString("whenFieldError", None)
      .notBlank.getOrElse(transformationStepsManagement.whenFieldError.toString))

  lazy val inputsModel: PropertySchemasInput =
    SdkSchemaHelper.getInputSchemasModel(properties.getString("inputSchemas", None).notBlank)

  lazy val discardConditions: Seq[DiscardCondition] = {
    implicit val json4sJacksonFormats: Formats = DefaultFormats + new JsoneyStringSerializer()
    val conditions =
      s"${properties.getString("discardConditions", None).notBlank.fold("[]") { values => values.toString }}"

    read[Seq[DiscardCondition]](conditions)
  }

  /* METHODS IMPLEMENTED */

  override def validate(options: Map[String, String] = Map.empty[String, String]): ErrorValidations = {
    var validation = ErrorValidations(valid = true, messages = Seq.empty)

    if (!SdkSchemaHelper.isCorrectTableName(name))
      validation = ErrorValidations(
        valid = false,
        messages = validation.messages :+ WorkflowValidationMessage(s"the step name $name is not valid", name))

    validation
  }

  /**
    * Transformation function that all the transformation plugins must implements.
    *
    * @param inputData Input steps data that the function receive. The key is the name of the step and the value is
    *                  the collection ([[DistributedMonad]])
    * @return The output [[DistributedMonad]] generated after apply the function
    */
  def transform(inputData: Map[String, DistributedMonad[Underlying]]): DistributedMonad[Underlying] = {
    inputData.head._2
  }

  /**
    *
    * @param inputData Input steps data that the function receive. The key is the name of the step and the value is
    *                  the collection ([[DistributedMonad]])
    * @return (transformedData, transformed schema, input schema)
    */
  def transformWithSchema(
                           inputData: Map[String, DistributedMonad[Underlying]]
                         ): (DistributedMonad[Underlying], Option[StructType], Option[StructType]) = {
    val data = transform(inputData)

    (data, None, None)
  }

  /**
    *
    * @param inputData Input steps data that the function receive. The key is the name of the step and the value is
    *                  the collection ([[DistributedMonad]])
    * @return (transformedData, transformed schema, discarded data, discarded schema)
    */
  def transformWithDiscards(
                             inputData: Map[String, DistributedMonad[Underlying]]
                           ): (DistributedMonad[Underlying], Option[StructType], Option[DistributedMonad[Underlying]], Option[StructType]) = {
    val (data, schema, inputSchema) = transformWithSchema(inputData)

    (data, schema, None, inputSchema)
  }

  /**
    * Execute the transform function passed as parameter over the first data of the map.
    *
    * @param inputData                Input data that must contains only one distributed collection.
    * @param generateDistributedMonad Function to apply
    * @return The transformed distributed collection [[DistributedMonad]]
    */
  def applyHeadTransform[Underlying[Row]](inputData: Map[String, DistributedMonad[Underlying]])
                                         (
                                           generateDistributedMonad: (String, DistributedMonad[Underlying]) => DistributedMonad[Underlying]
                                         ): DistributedMonad[Underlying] = {
    assert(inputData.size == 1, s"The step $name must have one input, now have: ${inputData.keys}")

    val (firstStep, firstStream) = inputData.head

    generateDistributedMonad(firstStep, firstStream)
  }

  /**
    * Execute the transform function passed as parameter over the first data of the map.
    *
    * @param inputData                Input data that must contains only one distributed collection.
    * @param generateDistributedMonad Function to apply
    * @return The transformed distributed collection [[DistributedMonad]]
    */
  def applyHeadTransformWithDiscards[Underlying[Row]](inputData: Map[String, DistributedMonad[Underlying]])
                                         (
                                           generateDistributedMonad: (String, DistributedMonad[Underlying]) => (DistributedMonad[Underlying], DistributedMonad[Underlying])
                                         ): (DistributedMonad[Underlying], DistributedMonad[Underlying]) = {
    assert(inputData.size == 1, s"The step $name must have one input, now have: ${inputData.keys}")

    val (firstStep, firstStream) = inputData.head

    generateDistributedMonad(firstStep, firstStream)
  }

  /**
    * Execute the transform function passed as parameter over the first data of the map.
    *
    * @param inputData                Input data that must contains only one distributed collection.
    * @param generateDistributedMonad Function to apply
    * @return (transformedData, transformed schema, input schema)
    */
  def applyHeadTransformSchema[Underlying[Row]](inputData: Map[String, DistributedMonad[Underlying]])
                                               (
                                                 generateDistributedMonad: (String, DistributedMonad[Underlying]) => (DistributedMonad[Underlying], Option[StructType], Option[StructType])
                                               ): (DistributedMonad[Underlying], Option[StructType], Option[StructType]) = {
    assert(inputData.size == 1, s"The step $name must have one input, now have: ${inputData.keys}")

    val (firstStep, firstData) = inputData.head

    generateDistributedMonad(firstStep, firstData)
  }


  /**
    *
    * @param inputData         Input data that must contains only one distributed collection.
    * @param inputSchema       Input schema
    * @param transformedData   Transformed data by the transformation
    * @param transformedSchema Transformed schema
    * @return (transformedData, transformed schema, discarded data, discarded schema)
    */
  def applyHeadDiscardedData[Underlying[Row]](
                                               inputData: Map[String, DistributedMonad[Underlying]],
                                               inputSchema: Option[StructType],
                                               transformedData: DistributedMonad[Underlying],
                                               transformedSchema: Option[StructType]
                                             ): (DistributedMonad[Underlying], Option[StructType], Option[DistributedMonad[Underlying]], Option[StructType]) = {
    assert(inputData.size == 1, s"The step $name must have one input, now have: ${inputData.keys}")

    val (firstStep, firstData) = inputData.head

    applyDiscardedData(firstStep, firstData, inputSchema, transformedData, transformedSchema)
  }

  def applyDiscardedData[Underlying[Row]](
                                           inputStep: String,
                                           inputData: DistributedMonad[Underlying],
                                           inputSchema: Option[StructType],
                                           transformedData: DistributedMonad[Underlying],
                                           transformedSchema: Option[StructType]
                                         ): (DistributedMonad[Underlying], Option[StructType], Option[DistributedMonad[Underlying]], Option[StructType]) = {
    val discardedData = if (discardConditions.nonEmpty) {
      inputData.setXdSession(xDSession)
      ssc.foreach(streamingContext => inputData.setStreamingContext(streamingContext))
      Try {
        inputData.discards(transformedData, name, transformedSchema, inputStep, inputSchema, discardConditions)
      } match {
        case Success(data) =>
          Option(data)
        case Failure(e) =>
          log.info(s"Error executing query when extracting discarded data with exception ${e.getLocalizedMessage}", e)
          None
      }
    } else None

    (transformedData, transformedSchema, discardedData, inputSchema)
  }

  def returnWhenFieldError(exception: Exception): Null =
    whenFieldErrorDo match {
      case WhenFieldError.Null => null
      case _ => throw exception
    }

  def castingToOutputSchema(outSchema: StructField, inputValue: Any): Any =
    Try {
      CastingHelper.castingToSchemaType(outSchema.dataType, inputValue.asInstanceOf[Any])
    } match {
      case Success(result) => result
      case Failure(e) => returnWhenFieldError(new Exception(
        s"Error casting to output type the value: ${inputValue.toString}", e))
    }
}

object TransformStep {
  val StepType = "transformation"
}