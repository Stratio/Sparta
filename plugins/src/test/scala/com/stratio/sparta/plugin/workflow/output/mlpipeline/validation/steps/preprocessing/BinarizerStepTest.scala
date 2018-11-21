/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.sparta.plugin.workflow.output.mlpipeline.validation.steps.preprocessing

import com.stratio.sparta.plugin.workflow.output.mlpipeline.validation.GenericPipelineStepTest
import org.apache.spark.ml.linalg.Vectors
import org.apache.spark.sql.DataFrame
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class BinarizerStepTest extends GenericPipelineStepTest {

  override def stepName: String = "binarizer"

  override def resourcesPath: String = "/mlpipeline/singlesteps/preprocessing/binarizer/"

  var data = Array(0.1, -0.5, 0.2, -0.3, 0.8, 0.7, -0.1, -0.4)

  val threshold: Double = 0.2

  val defaultBinarized: Array[Double] = data.map(x => if (x > threshold) 1.0 else 0.0)

  override def trainingDf: DataFrame = sparkSession.createDataFrame(Seq(
    (Vectors.dense(data), Vectors.dense(defaultBinarized))
  )).toDF("feature", "expected")
}