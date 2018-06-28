/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.sparta.plugin.workflow.output.elasticsearch

import com.stratio.sparta.plugin.TemporalSparkContext
import com.stratio.sparta.core.properties.JsoneyString
import org.apache.spark.sql.types._
import org.junit.runner.RunWith
import org.scalatest.ShouldMatchers
import org.scalatest.junit.JUnitRunner


@RunWith(classOf[JUnitRunner])
class ElasticSearchOutputStepTest extends TemporalSparkContext
  with ShouldMatchers{

  trait BaseValues {

    final val localPort = 9200
    final val remotePort = 9300
    val output = getInstance("localhost", localPort, remotePort)
    val outputMultipleNodes = new ElasticSearchOutputStep(
      "ES.out",
      sparkSession,
      Map("nodes" -> JsoneyString(
        s"""
           |[{
           |  "node":"host-a",
           |  "httpPort":"$localPort",
           |  "tcpPort":"$remotePort"
           |  },
           |  {
           |  "node":"host-b",
           |  "httpPort":"9201",
           |  "tcpPort":"9301"
           |}]
         """.stripMargin)
      ))

    def getInstance(host: String, httpPort: Int, tcpPort: Int) : ElasticSearchOutputStep =
      new ElasticSearchOutputStep(
        "ES-out",
        sparkSession,
        Map("nodes" -> JsoneyString(
          s"""
             |[{
             |  "node":"$host",
             |  "httpPort":"$httpPort",
             |  "tcpPort":"$tcpPort"
             |}]
           """.stripMargin)
        ))
  }

  trait NodeValues extends BaseValues{
    val ipOutput = getInstance("127.0.0.1", localPort, remotePort)
    val ipv6Output = getInstance("0:0:0:0:0:0:0:1", localPort, remotePort)
    val remoteOutput = getInstance("dummy", localPort, remotePort)
  }

  trait TestingValues extends BaseValues {
    val indexNameType = "table/sparta"
    val tableName = "table"
    val baseFields = Seq(StructField("string", StringType), StructField("int", IntegerType))
    val extraFields = Seq(
      StructField("id", StringType, nullable = false),
      StructField("timestamp", LongType, nullable = false))
    val schema = StructType(baseFields)
    val properties = Map("nodes" -> JsoneyString(
      s"""[{
        |"node":"localhost",
        |"httpPort":"9200",
        |"tcpPort":"9300"}
        |]
        |""".stripMargin))
    override val output = new ElasticSearchOutputStep("ES-out", sparkSession, properties)

    val dateField = StructField("timestamp", TimestampType, nullable = false)
    val expectedDateField = StructField("timestamp", LongType, nullable = false)
    val stringField = StructField("id", StringType)
    val expectedStringField = StructField("id", StringType)
  }

  "ElasticSearchOutputStep" should "format the properties" in new NodeValues {
    assertResult(output.httpNodes)(Seq(("localhost",9200)))
    assertResult(outputMultipleNodes.httpNodes)(Seq(("host-a",9200),("host-b",9201)))
  }

  it should "properly parse the index name type" in new TestingValues{
    output.mappingType should be ("sparta")
    output.indexNameType(tableName) should be (indexNameType)
  }

  it should "return a Seq of  tuples with a (host,port) format" in new NodeValues {
    assertResult(ipOutput.getHostPortConf("nodes", "localhost", "9200", "node", "httpPort"))(Seq(("127.0.0.1",9200)))
    assertResult(
      ipv6Output.getHostPortConf("nodes", "localhost", "9200", "node", "httpPort"))(Seq(("0:0:0:0:0:0:0:1",9200)))
    assertResult(outputMultipleNodes.getHostPortConf("nodes", "localhost", "9200", "node", "httpPort")
      )(Seq(("host-a", 9200),("host-b", 9201)))
  }
}
