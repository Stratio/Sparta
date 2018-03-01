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

package com.stratio.sparta.serving.core.factory

import java.io.File
import java.nio.file.{Files, Paths}
import javax.xml.bind.DatatypeConverter

import akka.event.slf4j.SLF4JLogging
import com.stratio.sparta.sdk.properties.ValidatingPropertyMap._
import com.stratio.sparta.sdk.utils.AggregationTimeUtils
import com.stratio.sparta.serving.core.config.SpartaConfig
import com.stratio.sparta.serving.core.helpers.JarsHelper
import com.stratio.sparta.serving.core.services.SparkSubmitService.spartaTenant
import com.stratio.sparta.serving.core.services.{HdfsService, SparkSubmitService}
import org.apache.spark.security.ConfigSecurity
import org.apache.spark.sql.crossdata.XDSession
import org.apache.spark.streaming.{Duration, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}

import scala.util.{Failure, Properties, Success, Try}

object SparkContextFactory extends SLF4JLogging {

  /* MUTABLE VARIABLES */

  private var sc: Option[SparkContext] = None
  private var xdSession: Option[XDSession] = None
  private var ssc: Option[StreamingContext] = None


  /* LAZY VARIABLES */

  private lazy val xDConfPath = SpartaConfig.getDetailConfig.get.getString("crossdata.reference")
  private lazy val hdfsWithUgiService = Try(HdfsService()).toOption
    .flatMap(utils => utils.ugiOption.flatMap(_ => Option(utils)))
  private lazy val jdbcDriverVariables: Seq[(String, String)] =
    SparkSubmitService.getJarsSparkConfigurations(JarsHelper.getJdbcDriverPaths).toSeq
  private lazy val kerberosYarnDefaultVariables: Seq[(String, String)] = {
    val hdfsConfig = SpartaConfig.getHdfsConfig
    (HdfsService.getPrincipalName(hdfsConfig).notBlank, HdfsService.getKeyTabPath(hdfsConfig).notBlank) match {
      case (Some(principal), Some(keyTabPath)) =>
        Seq(
          ("spark.mesos.kerberos.keytabBase64", DatatypeConverter.printBase64Binary(
            Files.readAllBytes(Paths.get(keyTabPath)))),
          ("spark.yarn.principal", principal),
          ("spark.hadoop.yarn.resourcemanager.principal", principal)
        )
      case _ =>
        Seq.empty[(String, String)]
    }
  }


  /* PUBLIC METHODS */

  def maybeWithHdfsUgiService(f: => Unit): Unit = hdfsWithUgiService.map(_.runFunction(f)).getOrElse(f)

  //scalastyle:off
  def getOrCreateXDSession(
                            withStandAloneExtraConf: Boolean = true,
                            initSqlSentences: Seq[String] = Seq.empty[String]
                          ): XDSession = {
    synchronized {
      xdSession.getOrElse {
        maybeWithHdfsUgiService {
          val referenceFile = Try {
            new File(xDConfPath)
          } match {
            case Success(file) =>
              log.info(s"Loading Crossdata configuration from file: ${file.getAbsolutePath}")
              file
            case Failure(e) =>
              val refFile = "/reference.conf"
              log.debug(s"Error loading Crossdata configuration file with error: ${e.getLocalizedMessage}")
              log.info(s"Loading Crossdata configuration from resource file $refFile")
              new File(getClass.getResource(refFile).getPath)
          }

          JarsHelper.addJdbcDriversToClassPath()

          sc match {
            case Some(sparkContext) =>
              val sparkConf = sparkContext.getConf

              if (withStandAloneExtraConf) addStandAloneExtraConf(sparkConf)

              xdSession = Option(XDSession.builder()
                .config(referenceFile)
                .config(sparkConf)
                .create(spartaTenant)
              )
            case None =>
              val sparkConf = new SparkConf()

              if (withStandAloneExtraConf) addStandAloneExtraConf(sparkConf)

              getOrCreateSparkContext(
                extraConfiguration = SparkSubmitService.getSparkLocalConfig ++ sparkConf.getAll.toMap,
                jars = Seq.empty,
                forceStop = false
              )

              xdSession = Option(XDSession.builder()
                .config(referenceFile)
                .config(sparkConf)
                .create(spartaTenant)
              )
          }

          xdSession.foreach { session =>
            initSqlSentences.filter(_.nonEmpty).foreach { sentence =>
              val trimSentence = sentence.trim
              if (trimSentence.startsWith("CREATE") || trimSentence.startsWith("IMPORT"))
                session.sql(trimSentence)
              else log.warn(s"Initial query ($trimSentence) not supported. Available operations: CREATE and IMPORT")
            }
          }
        }

        xdSession.getOrElse(throw new Exception("Spark Session not initialized"))
      }
    }
  }

  def getOrCreateStreamingContext(
                                   batchDuration: Duration,
                                   checkpointDir: Option[String],
                                   remember: Option[String]
                                 ): StreamingContext =
    synchronized {
      ssc.getOrElse(createStreamingContext(batchDuration, checkpointDir, remember))
    }

  def getStreamingContext: StreamingContext =
    ssc.getOrElse(throw new Exception("Streaming Context not initialized"))

  def getOrCreateSparkContext(
                               extraConfiguration: Map[String, String],
                               jars: Seq[File],
                               forceStop: Boolean = true
                             ): SparkContext =
    synchronized {
      if (forceStop) stopSparkContext()
      sc.getOrElse(createSparkContext(extraConfiguration, jars))
    }

  def getOrCreateClusterSparkContext(
                                      extraConfiguration: Map[String, String],
                                      files: Seq[String],
                                      forceStop: Boolean = false
                                    ): SparkContext =
    synchronized {
      if (forceStop) stopSparkContext()
      sc.getOrElse(createClusterContext(extraConfiguration, files))
    }

  def stopSparkContext(stopStreaming: Boolean = true): Unit = {
    if (stopStreaming) stopStreamingContext()

    sc.fold(log.info("Spark Context is empty")) { sparkContext =>
      synchronized {
        try {
          log.info("Stopping SparkContext: " + sparkContext.appName)
          sparkContext.stop()
          log.info("SparkContext: " + sparkContext.appName + " stopped correctly")
        } finally {
          xdSession = None
          ssc = None
          sc = None
        }
      }
    }
  }


  /* PRIVATE METHODS */

  private[core] def addStandAloneExtraConf(sparkConf: SparkConf): SparkConf = {
    val additionalConfigurations = jdbcDriverVariables ++ kerberosYarnDefaultVariables
    sparkConf.setAll(additionalConfigurations)
    log.debug(s"Added variables to Spark Conf in XDSession: $additionalConfigurations")

    sparkConf.setAppName(SparkSubmitService.spartaLocalAppName)

    if (Properties.envOrNone("MARATHON_APP_LABEL_HAPROXY_1_VHOST").isDefined) {
      val proxyPath = s"/workflows-$spartaTenant/crossdata-sparkUI"
      log.debug(s"XDSession with proxy base: $proxyPath")
      sparkConf.set("spark.ui.proxyBase", proxyPath)
    } else log.debug(s"XDSession without proxy base")

    if (Properties.envOrNone("SPARK_SECURITY_DATASTORE_ENABLE").isDefined) {
      val environment = ConfigSecurity.prepareEnvironment
      log.debug(s"XDSession secured environment prepared with variables: $environment")
      sparkConf.setAll(environment.toSeq)
    } else log.debug(s"XDSession secured environment not configured")

    sparkConf
  }

  private[core] def createStreamingContext(
                                            batchDuration: Duration,
                                            checkpointDir: Option[String],
                                            remember: Option[String]
                                          ): StreamingContext = {
    val sparkContext = sc.getOrElse(throw new Exception("Spark Context not initialized," +
      " is mandatory initialize it before create a new Streaming Context"))
    ssc = Option(new StreamingContext(sparkContext, batchDuration))
    checkpointDir.foreach(ssc.get.checkpoint)
    remember.foreach(value => ssc.get.remember(Duration(AggregationTimeUtils.parseValueToMilliSeconds(value))))
    ssc.get
  }

  private[core] def createSparkContext(configuration: Map[String, String], jars: Seq[File]): SparkContext = {
    sc = Option(SparkContext.getOrCreate(configurationToSparkConf(configuration)))
    jars.foreach { jar =>
      log.debug(s"Adding jar ${jar.getAbsolutePath} to Spark context")
      sc.get.addJar(jar.getAbsolutePath)
    }
    sc.get
  }

  private[core] def createClusterContext(configuration: Map[String, String], files: Seq[String]): SparkContext = {
    sc = Option(SparkContext.getOrCreate(configurationToSparkConf(configuration)))
    files.foreach { jar =>
      log.info(s"Adding jar $jar to Spark context")
      sc.get.addJar(jar)
    }
    sc.get
  }

  private[core] def configurationToSparkConf(configuration: Map[String, String]): SparkConf = {
    val conf = new SparkConf()
    configuration.foreach { case (key, value) => conf.set(key, value) }
    if(Try(conf.get("spark.app.name")).toOption.isEmpty)
      conf.setAppName(SparkSubmitService.spartaLocalAppName)
    conf
  }

  private[core] def stopStreamingContext(): Unit = {
    ssc.fold(log.info("Spark Streaming Context is empty")) { streamingContext =>
      try {
        synchronized {
          log.info(s"Stopping Streaming Context named: ${streamingContext.sparkContext.appName}")
          Try(streamingContext.stop(stopSparkContext = false, stopGracefully = false)) match {
            case Success(_) =>
              log.info("Streaming Context has been stopped")
            case Failure(error) =>
              log.error("Streaming Context not properly stopped", error)
          }
        }
      } finally {
        ssc = None
      }
    }
  }
}