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
package com.stratio.sparta.serving.core.config

import com.typesafe.config.ConfigFactory
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.{Matchers, WordSpec}

@RunWith(classOf[JUnitRunner])
class SpartaConfigTest extends WordSpec with Matchers {

  "SpartaConfig class" should{

    "initMainConfig should return X" in {

      val config = ConfigFactory.parseString(
        """
          |sparta {
          |
          | "testKey" : "test"
          |}
        """.stripMargin)

      val res = SpartaConfig.initMainConfig(Some(config), SpartaConfigFactory(config)).get.toString
      res should be ("""Config(SimpleConfigObject({"testKey":"test"}))""")

    }
    "initApiConfig should return X" in {
      SpartaConfig.mainConfig = None

      val configApi = ConfigFactory.parseString(
        """
          | api {
          |       "host" : "localhost"
          |       "port" : 9090
          |      }
        """.stripMargin)

      val res = SpartaConfig.initApiConfig(SpartaConfigFactory(configApi)).get.toString
      res should be ("""Config(SimpleConfigObject({"host":"localhost","port":9090}))""")

    }

    "getHdfsConfig(Case: Some(config) should return hdfs config" in {
      SpartaConfig.mainConfig = None
      SpartaConfig.apiConfig = None

      val configHdfs = ConfigFactory.parseString(
        """
          |sparta {
          |  hdfs {
          |    "hadoopUserName" : "stratio"
          |    "hadoopConfDir" : "/home/ubuntu"
          |  }
          |  }
        """.stripMargin
      )

      SpartaConfig.initMainConfig(Some(configHdfs), SpartaConfigFactory(configHdfs))

      val hdfsConf = SpartaConfig.getHdfsConfig.get.toString

      hdfsConf should be ("""Config(SimpleConfigObject({"hadoopConfDir":"/home/ubuntu","hadoopUserName":"stratio"}))""")

    }
    "getHdfsConfig(Case: None) should return hdfs config" in {
      SpartaConfig.mainConfig = None
      SpartaConfig.apiConfig = None

      val hdfsConf = SpartaConfig.getHdfsConfig

      hdfsConf should be (None)

    }

    "getDetailConfig (Case: Some(Config) should return the config" in {
      SpartaConfig.mainConfig = None
      SpartaConfig.apiConfig = None

      val configDetail = ConfigFactory.parseString(
        """
          |sparta {
          |  config {
          |    "executionMode": "local"
          |    "rememberPartitioner": true
          |    "topGracefully": false
          |  }
          |  }
        """.stripMargin
      )

      SpartaConfig.initMainConfig(Some(configDetail), SpartaConfigFactory(configDetail))

      val detailConf = SpartaConfig.getDetailConfig.get.toString

      detailConf should be
      (
        """"Config(SimpleConfigObject({
          |"executionMode":"local",
          |"rememberPartitioner":true,
          |"topGracefully":false
          |}))"""".stripMargin)

    }
    "getDetailConfig (Case: None should return the config" in {
      SpartaConfig.mainConfig = None
      SpartaConfig.apiConfig = None

      val detailConf = SpartaConfig.getDetailConfig

      detailConf should be (None)
    }
    "getZookeeperConfig (Case: Some(config) should return zookeeper conf" in {
      SpartaConfig.mainConfig = None
      SpartaConfig.apiConfig = None

      val configZk = ConfigFactory.parseString(
        """
          |sparta {
          |  zookeeper {
          |    "connectionString" : "localhost:6666"
          |    "connectionTimeout": 15000
          |    "sessionTimeout": 60000
          |    "retryAttempts": 5
          |    "retryInterval": 10000
          |  }
          |  }
        """.stripMargin
      )

      SpartaConfig.initMainConfig(Some(configZk), SpartaConfigFactory(configZk))

      val zkConf = SpartaConfig.getZookeeperConfig.get.toString

      zkConf should be
      (
        """"Config(SimpleConfigObject(
          |{"connectionString":"localhost:6666",
          |"connectionTimeout":15000,
          |"retryAttempts":5,
          |"retryInterval":10000,
          |"sessionTimeout":60000
          |}))"""".stripMargin)

    }
    "getZookeeperConfig (Case: None) should return zookeeper conf" in {
      SpartaConfig.mainConfig = None
      SpartaConfig.apiConfig = None

      val zkConf = SpartaConfig.getZookeeperConfig

      zkConf should be (None)
    }

    "initOptionalConfig should return a config" in {

      val config = ConfigFactory.parseString(
        """
          |sparta {
          | testKey : "testValue"
          |}
        """.stripMargin)

      val spartaConfig = SpartaConfig.initOptionalConfig(
        node = "sparta",
        configFactory = SpartaConfigFactory(config))
      spartaConfig.get.getString("testKey") should be ("testValue")
    }

    "getOptionStringConfig should return None" in {

      val config = ConfigFactory.parseString(
        """
          |sparta {
          | testKey : "testValue"
          |}
        """.stripMargin)
      val res = SpartaConfig.getOptionStringConfig(
        node = "sparta",
        currentConfig = config)

      res should be (None)
    }
  }
}
