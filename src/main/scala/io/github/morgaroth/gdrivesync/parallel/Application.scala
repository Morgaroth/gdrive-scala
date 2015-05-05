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
    val maybeCreds = Auth.authorizeUser(localRoot.getAbsolutePath)
    maybeCreds.map { creds =>
      val service = new GoogleDrive(creds)
      val rootGFile = service.detailedInfoOpt(service.about.getRootFolderId)
      val system = ActorSystem()
      val master = system.actorOf(Master.props(ConfigFactory.load()))
      master ! SyncFile(FilePair(localRoot, Some(rootGFile.get), localRoot, rootGFile.get), SyncPath.empty(localRoot), service)
    }.getOrElse {
      println(s"Cannot determine auth credentials")
    }
  }
}
