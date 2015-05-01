package io.github.morgaroth.gdrivesync.async.models

import akka.event.{LoggingAdapter, NoLogging}
import com.typesafe.config.Config

class Loggers(
               val progress: LoggingAdapter = NoLogging,
               val resolve: LoggingAdapter = NoLogging,
               val infos: LoggingAdapter = NoLogging,
               val confirmations: LoggingAdapter = NoLogging,
               val failures: LoggingAdapter = NoLogging
               )

object Loggers {
  def fromConfig(conf: Config) = new Loggers
}