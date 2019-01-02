/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.sparta.serving.api.helpers

import akka.actor.{ActorSystem, Props}
import akka.cluster.Cluster
import akka.event.slf4j.SLF4JLogging
import akka.io.IO
import com.stratio.sparta.serving.api.actor._
import com.stratio.sparta.serving.api.service.ssl.SSLSupport
import com.stratio.sparta.serving.core.actor._
import com.stratio.sparta.serving.core.config.SpartaConfig
import com.stratio.sparta.serving.core.constants.AkkaConstant._
import com.stratio.sparta.serving.core.constants.AppConstant
import com.stratio.sparta.serving.core.factory.PostgresFactory
import com.stratio.sparta.serving.core.helpers.SecurityManagerHelper
import com.stratio.sparta.serving.core.services.migration.hydra.HydraMigrationService
import com.stratio.sparta.serving.core.services.migration.orion.OrionMigrationService
import com.stratio.sparta.serving.core.utils.SpartaIgnite
import com.typesafe.config.ConfigFactory
import org.apache.ignite.Ignition
import spray.can.Http

import scala.util.Try


/**
  * Helper with common operations used to create a Sparta context used to run the application.
  */
object SpartaHelper extends SLF4JLogging with SSLSupport {

  //scalastyle:off
  /**
    * Initializes Sparta's akka system running an embedded http server with the REST API.
    *
    * @param appName with the name of the application.
    */
  def initSpartaAPI(appName: String): Unit = {
    if (
      SpartaConfig.getSpartaConfig().isDefined && SpartaConfig.getDetailConfig().isDefined &&
        SpartaConfig.getSparkConfig().isDefined && SpartaConfig.getPostgresConfig().isDefined &&
        SpartaConfig.getCrossdataConfig().isDefined && SpartaConfig.getOauth2Config().isDefined &&
        SpartaConfig.getZookeeperConfig().isDefined && SpartaConfig.getApiConfig().isDefined &&
        SpartaConfig.getSprayConfig().isDefined) {

      if (Try(SpartaConfig.getIgniteConfig().get.getBoolean(AppConstant.IgniteEnabled)).getOrElse(false)) {
        log.info("Initializing Sparta cache instance ...")
        SpartaIgnite.getAndOrCreateInstance()
      }

      log.info("Initializing Sparta Postgres schemas ...")
      PostgresFactory.invokeInitializationMethods()

      if (Try(SpartaConfig.getDetailConfig().get.getBoolean("migration.enable")).getOrElse(true)) {
        log.info("Initializing Sparta Postgres schema migration ...")
        val migrationOrion = new OrionMigrationService()
        val migrationHydra = new HydraMigrationService()
        migrationHydra.executePostgresMigration()

        log.info("Initializing Sparta Postgres data ...")
        PostgresFactory.invokeInitializationDataMethods()

        Thread.sleep(500)
        migrationOrion.executeMigration()
        migrationHydra.executeMigration()
      } else {
        log.info("Initializing Sparta Postgres data ...")
        PostgresFactory.invokeInitializationDataMethods()
      }

      log.info("Initializing Dyplon authorization plugins ...")
      implicit val secManager = SecurityManagerHelper.securityManager
      SecurityManagerHelper.initCrossdataSecurityManager()

      log.debug("Initializing Sparta system ...")
      implicit val system = ActorSystem(appName, SpartaConfig.getSpartaConfig().get.withFallback(ConfigFactory.load().getConfig("clusterSparta")))
      system.actorOf(Props[SpartaClusterNodeActor], "clusterNode")
      Cluster(system) registerOnMemberUp {

        system.actorOf(Props[TopLevelSupervisorActor])

        val controllerActor = system.actorOf(Props(new ControllerActor()), ControllerActorName)

        log.info("Binding Sparta API ...")
        IO(Http) ! Http.Bind(controllerActor,
          interface = SpartaConfig.getApiConfig().get.getString("host"),
          port = SpartaConfig.getApiConfig().get.getInt("port")
        )

        log.info("Sparta server initiated successfully")
      }
    }
    else log.info("Sparta configuration is not defined")

    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        SpartaIgnite.stopOrphanedNodes()
        SpartaIgnite.closeIgniteConnection()
        //Sure stopped?
        Ignition.stop(true)
      }
    })
  }
}
