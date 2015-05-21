package io.github.morgaroth.gdrivesync.parallel.actors

import akka.actor.{Actor, Props}
import io.github.morgaroth.gdrivesync.parallel.actors.DownloadingLogger.Log
import io.github.morgaroth.gdrivesync.parallel.models.Loggers

import scala.concurrent.duration._
import scala.language.postfixOps

object DownloadingLogger {

  private case object Log

  def props(path: String, loggers: Loggers) = Props(classOf[DownloadingLogger], loggers, path)
}

class DownloadingLogger(logging: Loggers, path: String) extends Actor {
  self ! Log

  override def receive: Receive = {
    case Log =>
      // TODO may comparing sizes of file remote and local may bring progress in percentage ?
      logging.progress.info(s"downloading file $path is in progress")
      context.system.scheduler.scheduleOnce(5 seconds, self, Log)
  }
}
