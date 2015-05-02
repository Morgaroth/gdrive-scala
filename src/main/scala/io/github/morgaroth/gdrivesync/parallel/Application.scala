package io.github.morgaroth.gdrivesync.parallel

import java.io.File

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import io.github.morgaroth.gdrivesync.parallel.actors.{FilePair, Master, SyncFile}
import io.github.morgaroth.gdrivesync.parallel.drive.{Auth, GoogleDrive}
import io.github.morgaroth.gdrivesync.parallel.models.SyncPath

object Application {
  //noinspection UnitInMap
  def main(args: Array[String]) {
    val path = if (args.length > 0) args(0) else "."
    val localRoot: File = new File(path).getCanonicalFile
    localRoot.mkdirs()
    println(s"DEBUG: ${localRoot.getAbsolutePath}")
    val maybeCreds = Auth.authorizeUser(localRoot.getAbsolutePath)
    maybeCreds.map { creds =>
      val system = ActorSystem()
      val master = system actorOf Master.props(ConfigFactory.parseString( """"""))
      val service = new GoogleDrive(creds)
      val rootGFile = service.rootGFile
      master ! SyncFile(FilePair(localRoot, Some(rootGFile), localRoot, rootGFile), SyncPath(localRoot)(), service)
    }.getOrElse {
      println(s"Cannot determine auth credentials")
    }
  }
}
