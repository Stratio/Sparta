/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */

package com.stratio.sparta.plugin.workflow.output.postgres

import java.io.{InputStream, Serializable => JSerializable}
import java.sql.SQLException

import com.stratio.sparta.core.enumerators.SaveModeEnum
import com.stratio.sparta.core.enumerators.SaveModeEnum.SpartaSaveMode
import com.stratio.sparta.core.models.{ErrorValidations, WorkflowValidationMessage}
import com.stratio.sparta.core.properties.ValidatingPropertyMap._
import com.stratio.sparta.core.workflow.enumerators.ConstraintType
import com.stratio.sparta.core.workflow.step.OutputStep
import com.stratio.sparta.plugin.enumerations.TransactionTypes
import com.stratio.sparta.plugin.helper.SecurityHelper
import com.stratio.sparta.plugin.helper.SecurityHelper._
import com.stratio.sparta.serving.core.constants.AppConstant._
import com.stratio.sparta.serving.core.workflow.lineage.JdbcLineage
import org.apache.spark.sql._
import org.apache.spark.sql.catalyst.expressions.GenericRowWithSchema
import org.apache.spark.sql.crossdata.XDSession
import org.apache.spark.sql.execution.datasources.jdbc.JDBCOptions
import org.apache.spark.sql.jdbc.SpartaJdbcUtils._
import org.apache.spark.sql.jdbc._
import org.apache.spark.sql.json.RowJsonHelper
import org.apache.spark.sql.types.{ArrayType, MapType, NullType, StructType}
import org.postgresql.copy.CopyManager
import org.postgresql.core.BaseConnection

import scala.util.{Failure, Success, Try}

class PostgresOutputStep(name: String, xDSession: XDSession, properties: Map[String, JSerializable])
  extends OutputStep(name, xDSession, properties) with JdbcLineage {

  lazy val url = properties.getString("url", "")
  lazy val delimiter = cleanPropertyChars(properties.getString("delimiter", "\t"))
  lazy val newLineSubstitution = cleanPropertyChars(properties.getString("newLineSubstitution", " "))
  lazy val quotesSubstitution = cleanPropertyChars(properties.getString("newQuotesSubstitution", "\b"))
  lazy val encoding = properties.getString("encoding", "UTF8")
  lazy val postgresSaveMode = TransactionTypes.withName(properties.getString("postgresSaveMode", "CopyIn").toUpperCase)
  lazy val isCaseSensitive = Try(properties.getBoolean("caseSensitiveEnabled")).getOrElse(false)
  lazy val failFast = Try(properties.getBoolean("failFast")).getOrElse(false)
  lazy val dropTemporalTableSuccess = Try(properties.getBoolean("dropTemporalTableSuccess")).getOrElse(true)
  lazy val dropTemporalTableFailure = Try(properties.getBoolean("dropTemporalTableFailure")).getOrElse(false)
  lazy val createSchemaIfNotExists = Try(properties.getBoolean("createSchemaIfNotExists")).getOrElse(true)

  val sparkConf = xDSession.conf.getAll
  val securityUri = getDataStoreUri(sparkConf)
  lazy val urlWithUser = addUserToConnectionURI(spartaTenant, url)
  val urlWithSSL = if (tlsEnable) urlWithUser + securityUri else url

  override lazy val lineageResource = ""

  override lazy val lineageUri: String = url

  override lazy val tlsEnable = Try(properties.getBoolean("tlsEnabled")).getOrElse(false)


  override def validate(options: Map[String, String] = Map.empty[String, String]): ErrorValidations = {
    var validation = ErrorValidations(valid = true, messages = Seq.empty)

    if (url.isEmpty)
      validation = ErrorValidations(valid = false, messages = validation.messages :+ WorkflowValidationMessage(s"the url must be provided", name))
    if (tlsEnable && securityUri.isEmpty)
      validation = ErrorValidations(
        valid = false,
        messages = validation.messages :+ WorkflowValidationMessage(s"when TLS is enabled, the security options inside sparkConf must be filled", name)
      )

    validation
  }

  override def supportedSaveModes: Seq[SpartaSaveMode] =
    Seq(SaveModeEnum.Append, SaveModeEnum.Overwrite, SaveModeEnum.Upsert, SaveModeEnum.Delete)

  //scalastyle:off
  private[postgres] def constraintExists(connectionProperties: JDBCOptions, uniqueConstraintName: String, outputName: String, dialect: JdbcDialect): Boolean = {
    synchronized {
      val conn = getConnection(connectionProperties, outputName)
      var exists = false

      val schemaToQuery = inferSchema(connectionProperties.table, conn, SpartaPostgresDialect)

      val statement = conn.prepareStatement(s"SELECT true FROM pg_catalog.pg_constraint con INNER JOIN pg_catalog.pg_class rel ON rel.oid = con.conrelid " +
        s"INNER JOIN pg_catalog.pg_namespace nsp  ON nsp.oid = connamespace WHERE nsp.nspname = '$schemaToQuery' " +
        s"AND con.conname = '$uniqueConstraintName'")
      try {
        val rs = statement.executeQuery()
        while (rs.next())
          exists = rs.getBoolean(1)
      } catch {
        case e: SQLException =>
          log.error(s"Unique Constraint $uniqueConstraintName does not exist in table ${connectionProperties.table}, will be created", e)
      } finally {
        statement.close()
      }
      exists
    }
  }

  override def lineageProperties(): Map[String, String] = getJdbcLineageProperties(OutputStep.StepType)

  //scalastyle:off
  private[postgres] def constraintSql(df: DataFrame, properties: JDBCOptions, searchFields: Seq[String], uniqueConstraintName: String, uniqueConstraintFields: Seq[String], outputName: String,
                                      isNewTable: Boolean, dialect: JdbcDialect)(placeHolders: String, upsertFields: Option[Seq[String]], primaryKeyField : String, primaryKeyWithFunctions: Boolean) = {
    val schema = df.schema

    val (columns, valuesPlaceholders) = {
      val upsertFieldsSql = upsertFields.getOrElse(schema.fields.map(_.name).toSeq)
      (schema.fields.map(_.name).toSeq.map(dialect.quoteIdentifier(_)).mkString(","), upsertFieldsSql.map(field => s"${dialect.quoteIdentifier(field)} = EXCLUDED.${dialect.quoteIdentifier(field)}").mkString(","))
    }

    if (uniqueConstraintName.nonEmpty) {
      //If is a new table OR constraint does not exists, constraint is created with constraint fields
      val constraintName = if (isNewTable || !constraintExists(properties, uniqueConstraintName, outputName, dialect)) {
        val uniqueConstraintFieldsSql =  uniqueConstraintFields.map(f => dialect.quoteIdentifier(f)).mkString(",")
        SpartaJdbcUtils.createConstraint(properties, outputName, uniqueConstraintName, uniqueConstraintFieldsSql, ConstraintType.Unique)
      } else {
        uniqueConstraintName
      }

      s"INSERT INTO ${properties.table} ($columns) $placeHolders ON CONFLICT ON CONSTRAINT $constraintName " +
        s"DO UPDATE SET $valuesPlaceholders"
    } else if(!primaryKeyWithFunctions) {
      //If is a new table, writer primaryKey is used for pk index creation, with a random name to avoid failures when upsert will we executed
      if (isNewTable) {
        val constraintFields = searchFields.map(field => dialect.quoteIdentifier(field)).mkString(",")
        SpartaJdbcUtils.createConstraint(properties, outputName, s"pk_${properties.table.replace('.','_')}_${uniqueConstraintName}_${System.currentTimeMillis()}", constraintFields, ConstraintType.PrimaryKey)
      }
      s"INSERT INTO ${properties.table} ($columns) $placeHolders ON CONFLICT (${searchFields.map(field => dialect.quoteIdentifier(field)).mkString(",")}) " +
        s"DO UPDATE SET $valuesPlaceholders"
    } else {
      s"INSERT INTO ${properties.table} ($columns) $placeHolders ON CONFLICT ($primaryKeyField) DO UPDATE SET $valuesPlaceholders"
    }
  }

  //scalastyle:on

  //scalastyle:off
  private def upsert(df: DataFrame, properties: JDBCOptions, searchFields: Seq[String], uniqueConstraintName: String, uniqueConstraintFields: Seq[String], outputName: String, txSaveMode: TxSaveMode,
                     isNewTable: Boolean, dialect: JdbcDialect, upsertFields: Option[Seq[String]], primaryKeyField: String, primaryKeyWithFunctions: Boolean): Unit = {
    //only pk
    val schema = df.schema
    val nullTypes = schema.fields.map { field =>
      SpartaJdbcUtils.getJdbcType(field.dataType, dialect).jdbcNullType
    }

    val placeHolders = s"VALUES(${schema.fields.map(_ => "?").mkString(",")})"

    val upsertSql = constraintSql(df, properties, searchFields, uniqueConstraintName, uniqueConstraintFields, outputName, isNewTable, dialect)(placeHolders, upsertFields, primaryKeyField, primaryKeyWithFunctions)

    val repartitionedDF = properties.numPartitions match {
      case Some(n) if n <= 0 => throw new IllegalArgumentException(
        s"Invalid value `$n` for parameter `${JDBCOptions.JDBC_NUM_PARTITIONS}` in table writing " +
          "via JDBC. The minimum value is 1.")
      case Some(n) if n < df.rdd.getNumPartitions => df.coalesce(n)
      case _ => df
    }
    repartitionedDF.foreachPartition { iterator =>
      if (iterator.hasNext) {
        Try {
          SpartaJdbcUtils.nativeUpsertPartition(properties, upsertSql, iterator, schema, nullTypes, dialect, schema.fields.length, outputName, txSaveMode)
        } match {
          case Success(_) =>
            log.debug(s"Upsert partition correctly on table ${properties.table} and output $outputName")
          case Failure(e) =>
            log.error(s"Upsert partition with errors on table ${properties.table} and output $outputName." +
              s" Error: ${e.getLocalizedMessage}")
            throw e
        }
      } else log.debug(s"Upsert partition with empty rows")
    }
  }

  //scalastyle:on

  //scalastyle:off
  override def save(dataFrame: DataFrame, saveMode: SaveModeEnum.Value, options: Map[String, String]): Unit = {
    require(url.nonEmpty, "Postgres url must be provided")
    require(!((postgresSaveMode == TransactionTypes.COPYIN || postgresSaveMode == TransactionTypes.ONE_TRANSACTION) && saveMode == SaveModeEnum.Delete),
      s"Writer SaveMode Delete could not be used with Postgres save mode $postgresSaveMode")
    validateSaveMode(saveMode)
    require(saveMode != SaveModeEnum.Ignore, s"Postgres saveMode $saveMode not supported")


    if (dataFrame.schema.fields.nonEmpty) {
      val tableName = getTableNameFromOptions(options)
      val sparkSaveMode = getSparkSaveMode(saveMode)
      val jdbcPropertiesMap = propertiesWithCustom.mapValues(_.toString).filter(_._2.nonEmpty) + ("driver" -> "org.postgresql.Driver")

      lazy val quotedTable =
        quoteTable(tableName, getConnection(new JDBCOptions(urlWithSSL, tableName, jdbcPropertiesMap), name))

      val connectionProperties = new JDBCOptions(
        urlWithSSL,
        if (isCaseSensitive) quotedTable else tableName,
        jdbcPropertiesMap
      )

      val dialect = JdbcDialects.get(connectionProperties.url)
      Try {
        if(createSchemaIfNotExists)
          SpartaJdbcUtils.createSchemaIfNotExist(connectionProperties, name, tableName)

        if (sparkSaveMode == SaveMode.Overwrite)
          SpartaJdbcUtils.truncateTable(connectionProperties, name)

        synchronized {
          SpartaJdbcUtils.tableExists(connectionProperties, dataFrame, name)
        }
      } match {
        case Success((tableExists, isNewTable)) =>
          try {
            if (tableExists) {
              lazy val updatePrimaryKey = getPrimaryKeyOptions(options).getOrElse("")
              lazy val primaryKeyWithFunctions = updatePrimaryKey.contains("(") || updatePrimaryKey.contains(")")
              lazy val updatePrimaryKeyFields = {
                if(updatePrimaryKey.nonEmpty)
                  updatePrimaryKey.split(",").map(_.trim).toSeq
                else Seq.empty[String]
              }
              val uniqueConstraintName = getUniqueConstraintNameOptions(options) match {
                case Some(pk) => pk.trim
                case None => ""
              }
              val uniqueConstraintFields = getUniqueConstraintFieldsOptions(options).map{ uconstr =>
                uconstr.split(",").map(_.trim).toSeq
              }.getOrElse(Seq.empty)

              val upsertFields = getUpdateFieldsOptions(options) match {
                case Some(fields) => Some(fields.split(",").map(f => f.trim).toSeq)
                case None => None
              }
              val txSaveMode = TxSaveMode(postgresSaveMode, failFast)
              if (saveMode == SaveModeEnum.Delete) {
                require(updatePrimaryKeyFields.nonEmpty, "The primary key fields must be provided")
                require(!primaryKeyWithFunctions && updatePrimaryKeyFields.forall(dataFrame.schema.fieldNames.contains(_)),
                  "All the primary key fields should be present in the dataFrame schema")
                SpartaJdbcUtils.deleteTable(dataFrame, connectionProperties, updatePrimaryKeyFields, name, txSaveMode)
              } else if (saveMode == SaveModeEnum.Upsert && postgresSaveMode != TransactionTypes.ONE_TRANSACTION) {
                if (uniqueConstraintName.isEmpty && updatePrimaryKeyFields.isEmpty)
                  require(uniqueConstraintName.nonEmpty, "The Unique Constraint Name must be provided")
                else if (uniqueConstraintName.nonEmpty && !constraintExists(connectionProperties, uniqueConstraintName, name, dialect))
                  require(uniqueConstraintFields.nonEmpty, "The Unique Constraint Fields must be provided, because constraint does not exist in database and should be created")
                else if (!primaryKeyWithFunctions && updatePrimaryKeyFields.nonEmpty) {
                  require(updatePrimaryKeyFields.nonEmpty, "The primary key fields must be provided")
                  require(updatePrimaryKeyFields.forall(dataFrame.schema.fieldNames.contains(_)), "All the primary key fields should be present in the dataFrame schema")
                }

                if (!primaryKeyWithFunctions && updatePrimaryKeyFields.nonEmpty && upsertFields.nonEmpty) {
                  require(upsertFields.get.forall(dataFrame.schema.fieldNames.contains(_)), "All the update fields should be present in the dataFrame schema")
                } else if (uniqueConstraintName.nonEmpty && upsertFields.nonEmpty) {
                  require(upsertFields.get.forall(dataFrame.schema.fieldNames.contains(_)), "All the update fields should be present in the dataFrame schema")
                }

                upsert(dataFrame, connectionProperties, updatePrimaryKeyFields, uniqueConstraintName, uniqueConstraintFields, name, txSaveMode, isNewTable, dialect, upsertFields, updatePrimaryKey, primaryKeyWithFunctions)
              }
              else if (postgresSaveMode == TransactionTypes.COPYIN) {
                val schema = dataFrame.schema
                dataFrame.foreachPartition { rows =>
                  if(rows.nonEmpty) {
                    val (rowsStream, empty) = rowsToInputStream(rows, schema)
                    if(!empty) {
                      val cm = {
                        val conn = getConnection(connectionProperties, name)
                        new CopyManager(conn.asInstanceOf[BaseConnection])
                      }
                      val copySentence = s"""COPY $tableName (${schema.fields.map(field => dialect.quoteIdentifier(field.name)).mkString(",")}) FROM STDIN WITH (NULL 'null', ENCODING '$encoding', FORMAT CSV, DELIMITER E'$delimiter', QUOTE E'$quotesSubstitution')"""
                      cm.copyIn(copySentence, rowsStream)
                    }
                  }
                }
              } else {
                val txOne = if (txSaveMode.txType == TransactionTypes.ONE_TRANSACTION) {
                  val tempTable = SpartaJdbcUtils.createTemporalTable(connectionProperties)
                  Some(TxOneValues(tempTable._1, tempTable._2, tempTable._3))
                } else None
                Try {
                  SpartaJdbcUtils.saveTable(dataFrame, connectionProperties, name, txSaveMode, txOne.map(_.temporalTableName))
                } match {
                  //If all partitions were ok, and is oneTx type, drop temp table
                  case Success(_) =>
                    if (txSaveMode.txType == TransactionTypes.ONE_TRANSACTION) {
                      try {
                        val sqlUpsert =
                          if (saveMode == SaveModeEnum.Upsert) {
                          val placeHolders = s"SELECT * FROM ${txOne.map(_.temporalTableName).get}"
                            constraintSql(dataFrame, connectionProperties, updatePrimaryKeyFields, uniqueConstraintName, uniqueConstraintFields, name, isNewTable, dialect)(placeHolders, None, updatePrimaryKey, primaryKeyWithFunctions)
                          } else {
                            s"INSERT INTO ${connectionProperties.table} SELECT * FROM ${txOne.map(_.temporalTableName).get} ON CONFLICT DO NOTHING"
                          }
                        txOne.map(_.connection).get.prepareStatement(sqlUpsert).execute()
                        txOne.map(_.connection).get.commit()
                      } catch {
                        case e: Exception =>
                          if (txSaveMode.txType == TransactionTypes.ONE_TRANSACTION)
                            txOne.map(_.connection).get.rollback(txOne.map(_.savePoint).get)
                          throw e
                      } finally {
                        try {
                          if (txSaveMode.txType == TransactionTypes.ONE_TRANSACTION && dropTemporalTableSuccess)
                            SpartaJdbcUtils.dropTable(connectionProperties, name, Some(txOne.map(_.temporalTableName).get))
                        } catch {
                          case e: Exception =>
                            throw e
                        } finally {
                          closeConnection(s"${connectionProperties.table}_temporal")
                          closeConnection(name)
                        }
                      }
                    }
                  case Failure(e) =>
                    if (txSaveMode.txType == TransactionTypes.ONE_TRANSACTION) {
                      try {
                        txOne.map(_.connection).get.rollback(txOne.map(_.savePoint).get)
                        if (dropTemporalTableFailure)
                          SpartaJdbcUtils.dropTable(connectionProperties, name, Some(txOne.map(_.temporalTableName).get))
                      } catch {
                        case e: Exception =>
                          throw e
                      } finally {
                        closeConnection(s"${connectionProperties.table}_temporal")
                      }
                    } else closeConnection(name)
                    throw e
                }
              }
            } else log.debug(s"Table not created in Postgres: $tableName")
          } catch {
            case e: Exception =>
              val (schema, tableN) =  SpartaJdbcUtils.getSchemaAndTableName(tableName)
              log.error(s"Error creating/dropping table ${ if(schema.isDefined) s"$tableN with schema ${schema.get}" else s"$tableName"} with Error: ${e.getLocalizedMessage}", e)
              throw e
          } finally {
            closeConnection(name)
          }
        case Failure(e) =>
          closeConnection(name)
          val (schema, tableN) =  SpartaJdbcUtils.getSchemaAndTableName(tableName)
          log.error(s"Error creating/dropping table ${ if(schema.isDefined) s"$tableN with schema ${schema.get}" else s"$tableName"} with Error: ${e.getLocalizedMessage}", e)
          throw e
      }
    }
  }

  def rowsToInputStream(rows: Iterator[Row], schema: StructType): (InputStream, Boolean) = {
    val fieldsCount = schema.fields.length
    val bytes = rows.flatMap { inputRow =>
      val row = schema.fields.toSeq.map { field =>
        val fieldIndex = inputRow.fieldIndex(field.name)
        val value = inputRow.get(fieldIndex)
        def jsonValue = {
          val newRow = new GenericRowWithSchema(
            Array(value),
            StructType(inputRow.schema.fields(fieldIndex) :: Nil)
          )
          RowJsonHelper.toValueAsJSON(newRow, Map.empty)
        }

        val newValue = field.dataType match {
          case _: MapType =>
            jsonValue
          case _: ArrayType =>
            jsonValue
              .replace("[\"", "{{")
              .replace("[", "{{")
              .replace("\"]", "}}")
              .replace("]", "}}")
              .replace("\",\"", "}#{")
              .replace(",", "}#{")
              .replace("}#{", "},{")
          case _: StructType =>
            jsonValue
          case _: NullType => "null"
          case _ => Option(value).fold("null") {_.toString}
        }

        if(newValue.contains(delimiter))
          throw new RuntimeException(s"Row with value[$newValue] contains the delimiter string [${delimiter}]")

        newValue
      }

      if(row.nonEmpty){
        if (row.length != fieldsCount)
          throw new RuntimeException(s"Row with values [${row.mkString(",")}] has discrepancy with the schema fields $fieldsCount")

        val text = row.mkString(delimiter).replace("\n", newLineSubstitution) + "\n"
        text.getBytes(encoding)
      } else {
        "".getBytes(encoding)
      }
    }
    val bytesEmpty = bytes.isEmpty

    (new InputStream {
      override def read(): Int =
        if (bytes.hasNext) bytes.next & 0xff
        else -1
    }, bytesEmpty)
  }

  //scalastyle:on

  override def cleanUp(options: Map[String, String]): Unit = {
    log.info(s"Closing connections in Postgres Output: $name")
    closeConnection(name)
  }

  private def cleanPropertyChars(property: String): String = {
    property match {
      case """\n""" => "\n"
      case """\b""" => "\b"
      case """\t""" => "\t"
      case """\r""" => "\r"
      case """\f""" => "\f"
      case _ => property
    }
  }
}

object PostgresOutputStep {

  def getSparkSubmitConfiguration(configuration: Map[String, JSerializable]): Seq[(String, String)] = {
    SecurityHelper.dataStoreSecurityConf(configuration)
  }
}