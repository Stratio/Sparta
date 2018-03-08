/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.sparta.plugin.workflow.input.crossdata

import com.stratio.sparta.sdk.properties.JsoneyString
import com.stratio.sparta.sdk.workflow.enumerators.SaveModeEnum
import com.stratio.sparta.sdk.workflow.step.OutputOptions
import org.apache.spark.sql.crossdata.XDSession
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.datasource.models.{OffsetConditions, OffsetField, OffsetOperator}
import org.junit.runner.RunWith
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.junit.JUnitRunner
import org.scalatest.mock.MockitoSugar

@RunWith(classOf[JUnitRunner])
class CrossdataInputStepStreamingTest extends WordSpec with Matchers with MockitoSugar {

  val ssc = mock[StreamingContext]
  val xdSession = mock[XDSession]
  val outputOptions = OutputOptions(SaveModeEnum.Append, "tableName", None, None)

  "CrossdataInputStep" should {
      val offsetFields =
        """[
          |{
          |"offsetField":"id",
          |"offsetOperator":">=",
          |"offsetValue": "500"
          |},
          |{
          |"offsetField":"storeID",
          |"offsetOperator":">=",
          |"offsetValue": "75"
          |},
          |{
          |"offsetField":"cashierID",
          |"offsetOperator":">=",
          |"offsetValue": "1002"
          |}
          |]
        """.stripMargin

      val properties = Map("offsetFields" -> JsoneyString(offsetFields))
      val input = new CrossdataInputStepStreaming("name", outputOptions, Option(ssc), xdSession, properties)

      val conditions = OffsetConditions(
        input.offsetItems,
        input.limitRecords)

      val complexConditions = conditions.copy(
        fromOffset = conditions.fromOffset.map( x =>
        OffsetField(
          x.name,
          OffsetOperator.toMultiProgressOperator(x.operator),
          x.value
        )))

    "parse and concatenate correctly offset options" in {
      input.offsetItems should contain theSameElementsInOrderAs Seq(
        OffsetField("id",OffsetOperator.>=,Some("500")),
        OffsetField("storeID",OffsetOperator.>=,Some("75")),
        OffsetField("cashierID",OffsetOperator.>=,Some("1002")))
    }
    "create a simple WHERE condition query if simple operators" in {
      val actualConditionsSimpleString =
        conditions.extractConditionSentence(None).trim.replaceAll("\\s+", " ")

      val expectedConditionsSimpleString = " WHERE id >= '500' AND storeID >= '75' AND cashierID >= '1002'"
        .trim.replaceAll("\\s+", " ")

      actualConditionsSimpleString should be (expectedConditionsSimpleString)
    }

    "append previous WHERE condition to the new query if simple operators" in {
      val actualConditionsSimpleString =
        conditions.extractConditionSentence(Option("name > The")).trim.replaceAll("\\s+", " ")

      val expectedConditionsSimpleString =
        " WHERE id >= '500' AND storeID >= '75' AND cashierID >= '1002' AND name > The"
          .trim.replaceAll("\\s+", " ")

      actualConditionsSimpleString should be (expectedConditionsSimpleString)
    }

    "create a complex WHERE condition query if incremental/decremental operators" in {
      val actualConditionsComplexString = complexConditions
        .extractConditionSentence(None).trim.replaceAll("\\s+", " ")

      val expectedConditionsComplexString =
        ("  WHERE ( storeID = '75' AND id = '500' AND cashierID >'1002' ) OR ( id = '500' AND storeID >'75' ) " +
          "OR id >'500'").trim.replaceAll("\\s+", " ")
      actualConditionsComplexString should be (expectedConditionsComplexString)
    }

    "create a simple ORDER BY condition no matter the operators" in {
      val actualConditionsComplexString = conditions.extractOrderSentence("select * from tableA")
        .replaceAll("\\s+", " ")
      val actualConditionsSimpleString = complexConditions.extractOrderSentence("select * from tableA")
        .replaceAll("\\s+", " ")
      val expectedConditionsString = "ORDER BY id DESC, storeID DESC, cashierID DESC".replaceAll("\\s+", " ")
      Seq(actualConditionsComplexString, actualConditionsSimpleString).forall(_ == expectedConditionsString)
    }
  }
}