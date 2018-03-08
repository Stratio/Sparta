/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.sparta.plugin.workflow.transformation.trigger

import com.stratio.sparta.plugin.TemporalSparkContext
import com.stratio.sparta.sdk.DistributedMonad.DistributedMonadImplicits
import com.stratio.sparta.sdk.workflow.enumerators.SaveModeEnum
import com.stratio.sparta.sdk.workflow.step.{OutputOptions, TransformationStepManagement}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Row
import org.apache.spark.sql.catalyst.expressions.GenericRowWithSchema
import org.apache.spark.sql.types.{DoubleType, StringType, StructField, StructType}
import org.junit.runner.RunWith
import org.scalatest.Matchers
import org.scalatest.junit.JUnitRunner
import scala.collection.mutable

import com.stratio.sparta.sdk.properties.JsoneyString

@RunWith(classOf[JUnitRunner])
class TriggerTransformStepStreamingIT extends TemporalSparkContext with Matchers with DistributedMonadImplicits {

  "A TriggerTransformStep" should "make trigger over one DStream" in {

    val schema1 = StructType(Seq(StructField("color", StringType), StructField("price", DoubleType)))
    val dataQueue1 = new mutable.Queue[RDD[Row]]()
    val data1 = Seq(
      new GenericRowWithSchema(Array("blue", 12.1), schema1),
      new GenericRowWithSchema(Array("red", 12.2), schema1)
    )
    dataQueue1 += sc.parallelize(data1)
    val stream1 = ssc.queueStream(dataQueue1)
    val inputData = Map("step1" -> stream1)
    val outputOptions = OutputOptions(SaveModeEnum.Append, "tableName", None, None)
    val query = s"SELECT * FROM step1 ORDER BY step1.color"
    val inputSchema = """[{"stepName":"step1","schema":"{\"color\":\"1\",\"price\":15.5}"}]"""
    val result = new TriggerTransformStepStreaming(
      "dummy",
      outputOptions,
      TransformationStepManagement(),
 Option(ssc),
      sparkSession,
      Map("sql" -> query,"inputSchemas" -> JsoneyString(inputSchema))
    ).transform(inputData)
    val totalEvents = ssc.sparkContext.accumulator(0L, "Number of events received")

    result.ds.foreachRDD(rdd => {
      val streamingEvents = rdd.count()
      log.info(s" EVENTS COUNT : \t $streamingEvents")
      totalEvents += streamingEvents
      log.info(s" TOTAL EVENTS : \t $totalEvents")
      val streamingRegisters = rdd.collect()
      if (!rdd.isEmpty()) {
        streamingRegisters.foreach(row => assert(data1.contains(row)))
      }
    })
    ssc.start()
    ssc.awaitTerminationOrTimeout(3000L)
    ssc.stop()

    assert(totalEvents.value === 2)

  }

  "A TriggerTransformStep" should "make trigger over two DStreams" in {

    val schema1 = StructType(Seq(StructField("color", StringType), StructField("price", DoubleType)))
    val schema2 = StructType(Seq(StructField("color", StringType),
      StructField("company", StringType), StructField("name", StringType)))
    val schemaResult = StructType(Seq(StructField("color", StringType),
      StructField("company", StringType), StructField("name", StringType), StructField("price", DoubleType)))
    val dataQueue1 = new mutable.Queue[RDD[Row]]()
    val dataQueue2 = new mutable.Queue[RDD[Row]]()
    val data1 = Seq(
      new GenericRowWithSchema(Array("blue", 12.1), schema1),
      new GenericRowWithSchema(Array("red", 12.2), schema1)
    )
    val data2 = Seq(
      new GenericRowWithSchema(Array("blue", "Stratio", "Stratio employee"), schema2),
      new GenericRowWithSchema(Array("red", "Paradigma", "Paradigma employee"), schema2)
    )
    dataQueue1 += sc.parallelize(data1)
    dataQueue2 += sc.parallelize(data2)
    val stream1 = ssc.queueStream(dataQueue1)
    val stream2 = ssc.queueStream(dataQueue2)
    val inputData = Map("step1" -> stream1, "step2" -> stream2)
    val outputOptions = OutputOptions(SaveModeEnum.Append, "tableName", None, None)
    val query = s"SELECT step1.color, step2.company, step2.name, step1.price " +
      s"FROM step2 JOIN step1 ON step2.color = step1.color ORDER BY step1.color"
    val result = new TriggerTransformStepStreaming(
      "dummy",
      outputOptions,
      TransformationStepManagement(),
 Option(ssc),
      sparkSession,
      Map("sql" -> query)
    ).transform(inputData)
    val totalEvents = ssc.sparkContext.accumulator(0L, "Number of events received")

    result.ds.foreachRDD(rdd => {
      val streamingEvents = rdd.count()
      log.info(s" EVENTS COUNT : \t $streamingEvents")
      totalEvents += streamingEvents
      log.info(s" TOTAL EVENTS : \t $totalEvents")
      val streamingRegisters = rdd.collect()
      if (!rdd.isEmpty()) {
        val queryData = Seq(
          new GenericRowWithSchema(Array("blue", "Stratio", "Stratio employee", 12.1), schemaResult),
          new GenericRowWithSchema(Array("red", "Paradigma", "Paradigma employee", 12.2), schemaResult))
        streamingRegisters.foreach(row => assert(queryData.contains(row)))
      }
    })
    ssc.start()
    ssc.awaitTerminationOrTimeout(3000L)
    ssc.stop()

    assert(totalEvents.value === 2)

  }
}