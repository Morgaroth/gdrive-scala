package io.github.morgaroth.gdrivesync.parallel

import java.io.File

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.github.morgaroth.gdrivesync.parallel.actors.Master

object Application {
  def main(args: Array[String]) {
    val path = if (args.length > 0) args(0) else "."
    val localRoot: File = new File(path).getCanonicalFile
    localRoot.mkdirs()
    println(s"DEBUG: ${localRoot.getAbsolutePath}")
    val system = ActorSystem()
    val master = system actorOf Master.props(ConfigFactory.parseString( """"""))
    //    master ! FirstPairToSync
  }
}
