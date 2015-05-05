package io.github.morgaroth.gdrivesync.parallel.models

import akka.actor.ActorSystem
import akka.event.{Logging, LoggingAdapter, NoLogging}
import com.typesafe.config.Config

class Loggers(
               val progress: LoggingAdapter = NoLogging,
               val resolve: LoggingAdapter = NoLogging,
               val infos: LoggingAdapter = NoLogging,
               val confirmations: LoggingAdapter = NoLogging,
               val failures: LoggingAdapter = NoLogging,
               val debug: LoggingAdapter = NoLogging
               )

object Loggers {
  def fromConfig(conf: Config, system: ActorSystem) = new Loggers(
    Logging.getLogger(system, "progress"),
    Logging.getLogger(system, "resolve"),
    Logging.getLogger(system, "infos"),
    Logging.getLogger(system, "confirmations"),
    Logging.getLogger(system, "failures"),
    Logging.getLogger(system, "debug")
  )
}