package io.github.morgaroth.gdrivesync.parallel.actors

import java.io.File

import akka.actor.{Actor, ActorLogging, Props}
import akka.event.LoggingAdapter
import com.typesafe.config.Config
import io.github.morgaroth.gdrivesync.parallel.drive.GoogleDrive
import io.github.morgaroth.gdrivesync.parallel.models.{GFile, Loggers, SyncPath}
import io.github.morgaroth.gdrivesync.parallel.helpers.hashable._

import scala.util.{Failure, Try}

object Worker {
  def props(cfg: Config) = Props(classOf[Worker], Loggers.fromConfig(cfg))
}

class Worker(logging: Loggers) extends Actor with ActorLogging {

  override def receive: Receive = {
    case SyncFile(pair, path, service) =>
      val local = pair.local
      pair.remote match {
        case Some(remote) if local.isFile && remote.isFile =>
          if (local.toMD5 == remote.md5) {
            logging.infos.info(s"file $path is in sync")
          } else {
            if (remote.lastModified.getValue > local.lastModified()) {
              fetchFileFromDrive(remote, local, path, service)
            } else {
              updateFileInDrive(local, remote, path, service)
            }
          }
        case Some(remote) if !local.exists() && remote.isFile =>
          fetchFileFromDrive(remote, local, path, service)
        case Some(remote) if !local.exists() && remote.isDir =>
          createDirectoryLocally(remote, local, path, service, logging.confirmations) map { newPairs =>
            sender() ! newPairs
          }
        case None if local.isFile =>
          uploadFileToDrive(local, pair.remoteParent, path, service)
        case None if local.isDirectory =>
          createDirectoryRemotely(pair, path, service, local) map { newPairs =>
            sender() ! newPairs
          }
        case Some(remote) if local.exists() && remote.isDir =>
          logging.infos.info(s"directory $path is in sync")
          val nextPaths = readFilePairs(local, remote, path, service)
          logging.infos.info(s"from $path added ${nextPaths.length} new file pairs to sync queue")
          sender() ! nextPaths
        case anotherCase =>
          logging.confirmations.warning(s"another case of sync file $anotherCase, $pair, $path")
      }
      sender() ! Done
  }

  def readFilePairs(local: File, remote: GFile, path: SyncPath, service: GoogleDrive): List[SyncFile] = {
    val localFiles = local.listFiles().map(x => x.getName -> x).toMap

    val remoteOrBoth: Map[String, (File, Some[GFile])] = remote.children.map { r =>
      r.name ->(localFiles.getOrElse(r.name, new File(local, r.name)), Some(r))
    }.toMap

    val onlyLocal: Map[String, (File, Option[GFile])] = localFiles
      .filterKeys(x => !remoteOrBoth.keySet.contains(x))
      .mapValues(f => (f, None))

    (onlyLocal ++ remoteOrBoth map {
      case (_, (localChild, remoteChild)) =>
        SyncFile(FilePair(localChild, remoteChild, local, remote), path :+ localChild, service)
    }).toList
  }

  def createDirectoryRemotely(pair: FilePair, path: SyncPath, service: GoogleDrive, local: File): Try[List[SyncFile]] = {
    service.newMkDir(pair.remoteParent, local.getName) map { newRemote =>
      logging.confirmations.info(s"directory $path successfully created on remote")
      local.listFiles().toList.map { child =>
        SyncFile(FilePair(child, None, local, newRemote), path :+ child, service)
      }
    } map { nextPaths =>
      logging.infos.info(s"from $path added ${nextPaths.length} new file pairs to sync queue")
      nextPaths
    } recoverWith {
      case notReady: AssertionError =>
        logging.failures.error(s"creating directory $path failed because ${notReady.getMessage}")
        Failure(notReady)
      case another: Throwable =>
        logging.failures.error(s"creating directory $path failed because another error occurred ${another.getMessage}\n${another.getStackTrace.map(_.toString).mkString("\n")}")
        Failure(another)
    }
  }

  def createDirectoryLocally(from: GFile, toCreate: File, path: SyncPath, service: GoogleDrive, logger: LoggingAdapter): Try[List[SyncFile]] = {
    Try {
      toCreate.mkdirs()
      assert(toCreate.exists() && toCreate.isDirectory, "created directory not exists or isn't directory at all")
      logger.info(s"directory $path successfully created locally")
      from.children.map { child =>
        SyncFile(FilePair(new File(toCreate, child.name), Some(child), toCreate, from), path :+ child, service)
      }
    } map { nextPaths =>
      logging.infos.info(s"from $path added ${nextPaths.length} new file pairs to sync queue")
      nextPaths
    } recoverWith {
      case notReady: AssertionError =>
        logging.failures.error(s"creating directory $path failed because ${notReady.getMessage}")
        Failure(notReady)
      case another: Throwable =>
        logging.failures.error(s"creating directory $path failed because another error occurred ${another.getMessage}\n${another.getStackTrace.map(_.toString).mkString("\n")}")
        Failure(another)
    }
  }

  def fetchFileFromDrive(from: GFile, target: File, path: SyncPath, service: GoogleDrive): Try[File] = {
    logging.infos.info(s"downloading remote file(id=${from.id}}) to ${path :+ target} started...")
    val logged = service.newDownload(from, target, path) map { sent =>
      logging.confirmations.info(s"successful download of file ${path :+ target}")
      sent
    } recoverWith {
      case notReady: AssertionError =>
        logging.failures.error(s"download of file ${path :+ target} failed because ${notReady.getMessage}")
        Failure(notReady)
      case another: Throwable =>
        logging.failures.error(s"download of file ${path :+ target} failed because another error occurred ${another.getMessage}\n${another.getStackTrace.map(_.toString).mkString("\n")}")
        Failure(another)
    }
    logged
  }

  def updateFileInDrive(from: File, to: GFile, path: SyncPath, service: GoogleDrive): Try[GFile] = {
    logging.infos.info(s"updating file ${path :+ from} to be under parent (id: ${to.id}) started...")
    val loggedOut = service.newUpdate(from, to, path, logging.progress) map { uploaded =>
      logging.confirmations.info(s"successful update of file ${path :+ from}")
      uploaded
    } recoverWith {
      case notReady: AssertionError =>
        logging.failures.error(s"update of file ${path :+ from} failed because ${notReady.getMessage}")
        Failure(notReady)
      case another: Throwable =>
        logging.failures.error(s"update of file ${path :+ from} failed because another error occurred ${another.getMessage}\n${another.getStackTrace.map(_.toString).mkString("\n")}")
        Failure(another)
    }
    loggedOut
  }

  def uploadFileToDrive(from: File, parent: GFile, path: SyncPath, service: GoogleDrive): Try[GFile] = {
    logging.infos.info(s"uploading file ${path :+ from} to be under parent (id: ${parent.id}) started...")
    val loggedOut = service.newUpload(from, parent, path, logging.progress) map { uploaded =>
      logging.confirmations.info(s"successful upload of file ${path :+ from}")
      uploaded
    } recoverWith {
      case notReady: AssertionError =>
        logging.failures.error(s"upload of file ${path :+ from} failed because ${notReady.getMessage}")
        Failure(notReady)
      case another: Throwable =>
        logging.failures.error(s"upload of file ${path :+ from} failed because another error occurred ${another.getMessage}\n${another.getStackTrace.map(_.toString).mkString("\n")}")
        Failure(another)
    }
    loggedOut
  }
}
