/*
 * © 2017 Stratio Big Data Inc., Sucursal en España. All rights reserved.
 *
 * This software – including all its source code – contains proprietary information of Stratio Big Data Inc., Sucursal en España and may not be revealed, sold, transferred, modified, distributed or otherwise made available, licensed or sublicensed to third parties; nor reverse engineered, disassembled or decompiled, without express written authorization from Stratio Big Data Inc., Sucursal en España.
 */
package com.stratio.sparta.serving.core.error

import java.util.Date

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}
import akka.event.Logging
import akka.event.Logging.LogLevel
import akka.event.slf4j.{Logger, SLF4JLogging}
import com.stratio.sparta.core.enumerators.PhaseEnum
import com.stratio.sparta.core.helpers.ExceptionHelper
import com.stratio.sparta.core.models.WorkflowError
import com.stratio.sparta.serving.core.constants.AppConstant
import com.stratio.sparta.serving.core.exception.ErrorManagerException
import com.stratio.sparta.serving.core.factory.PostgresDaoFactory
import com.stratio.sparta.serving.core.models.enumerators.WorkflowStatusEnum.NotDefined
import com.stratio.sparta.serving.core.models.workflow.{ExecutionStatus, ExecutionStatusUpdate, Workflow}

trait NotificationManager extends SLF4JLogging {

  override lazy val log = Logger(s"${this.getClass.getPackage.getName}.NotificationManager")

  val workflow: Workflow

  val defaultLogLevel: LogLevel

  val defaultLogErrorLevel: LogLevel

  val LogMessagePattern = "An error was detected : {}"

  def traceFunction[T](
                        code: PhaseEnum.Value,
                        okMessage: String,
                        errorMessage: String,
                        logLevel: LogLevel = defaultLogLevel,
                        step: Option[String] = None
                      )(f: => T): T = {
    Try(f) match {
      case Success(result) =>
        logLevel.asInt match {
          case 1 => log.error(okMessage)
          case 2 => log.warn(okMessage)
          case 3 => log.info(okMessage)
          case _ => log.debug(okMessage)
        }

        result
      case Failure(ex: Exception) =>
        throw logAndCreateEx(code, ex, workflow, errorMessage, step)
    }
  }

  def clearError(): Unit

  protected def traceError(error: WorkflowError): Unit

  private def logAndCreateEx(
                              code: PhaseEnum.Value,
                              exception: Exception,
                              workflow: Workflow,
                              message: String,
                              step: Option[String] = None
                            ): Throwable = {
    val workflowError = WorkflowError(
      message = message,
      phase = code,
      exceptionMsg = exception.toString,
      localizedMsg = ExceptionHelper.toPrintableException(exception),
      date = new Date,
      step = step
    )

    defaultLogErrorLevel.asInt match {
      case 1 => log.error(LogMessagePattern, workflowError)
      case 2 => log.warn(LogMessagePattern, workflowError)
      case 3 => log.info(LogMessagePattern, workflowError)
      case _ => log.debug(LogMessagePattern, workflowError)
    }

    Try {
      traceError(workflowError)
    } recover {
      case e => log.error(s"Error while persisting error: $workflowError", e)
    }

    ErrorManagerException(s"$message. Message: ${ExceptionHelper.toPrintableException(exception)}", exception, message)
  }
}

trait PostgresNotificationManager extends NotificationManager {

  val defaultLogLevel = Logging.InfoLevel

  val defaultLogErrorLevel = Logging.ErrorLevel

  lazy val executionService = PostgresDaoFactory.executionPgService

  def traceError(error: WorkflowError): Unit =
    workflow.executionId.foreach { executionId =>
      executionService.updateStatus(ExecutionStatusUpdate(
        executionId,
        ExecutionStatus(
          state = NotDefined
        )), error)
    }


  def clearError(): Unit =
    workflow.executionId.foreach { executionId =>
      executionService.clearLastError(executionId)
    }

}

trait PostgresDebugNotificationManager extends NotificationManager {

  val defaultLogLevel = Logging.DebugLevel

  val defaultLogErrorLevel = Logging.DebugLevel

  lazy val debugService = PostgresDaoFactory.debugWorkflowPgService

  def traceError(error: WorkflowError): Unit =
    workflow.id.foreach { id =>
      Await.result(debugService.setError(id, Option(error)), AppConstant.maxDebugWriteErrorTimeout milliseconds)
    }

  def clearError(): Unit =
    workflow.id.foreach { id =>
      Await.result(debugService.clearLastError(id), AppConstant.maxDebugWriteErrorTimeout milliseconds)
    }

}

case class PostgresNotificationManagerImpl(workflow: Workflow) extends PostgresNotificationManager

case class PostgresDebugNotificationManagerImpl(workflow: Workflow) extends PostgresDebugNotificationManager

trait LogNotificationManager extends NotificationManager with SLF4JLogging {

  def traceError(error: WorkflowError): Unit = log.error(s"This error was not saved to Postgres : $error")

  def clearError(): Unit = log.error(s"Cleaned errors")
}
