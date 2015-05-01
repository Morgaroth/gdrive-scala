package io.github.morgaroth.gdrivesync.async

import java.io.File

import io.github.morgaroth.gdrivesync.api.Auth
import io.github.morgaroth.gdrivesync.async.models.GFile
import io.github.morgaroth.gdrivesync.helpers.hashable

import scala.collection.immutable.Iterable
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

case class State(remote: GFile, local: File, current: IndexedSeq[String])

object SyncLocalDrive {
  def synchronizeDrive(parent: File) = {
    Logic.serviceForDirectory(parent.getAbsolutePath).map(new GoogleDrive(_)) foreach { remote =>
      Logic.syncFolder(remote.rootGFile, parent, State(remote.rootGFile, parent, IndexedSeq("/")))
    }
  }
}

trait Logic extends hashable {

  def serviceForDirectory(parent: String) = Auth.authorizeUser(parent)

  def mkPath(segments: IndexedSeq[String]) = segments.mkString("/")

  def syncFolder(remote: GFile, local: File, state: State) {
    println(s"entering folder ${mkPath(state.current)} with remote $remote and local ${local.getAbsolutePath}")
    val (lF, lD) = local.listFiles().toList.partition(_.isFile)
    val (rF, rD) = remote.children.partition(_.isFile)
    synchronizeFiles(rF, lF, remote, local, state)
    val synced = syncFolders(rD, lD, remote, local, state)
    synced.foreach(x => syncFolder(x._1.updateInfo, x._2, state.copy(current = state.current :+ x._1.name)))
  }

  def syncFolders(remote: List[GFile], local: List[File], rParent: GFile, lParent: File, state: State) = {
    val r = remote.map(r => r.name -> r).toMap
    val l = local.map(l => l.getName -> l).toMap
    val toBeUploaded: List[File] = local.filter(l => !r.keySet.contains(l.getName))
    val toBeDownloaded: List[GFile] = remote.filter(r => !l.keySet.contains(r.name))
    val existsBoth: Map[String, (GFile, File)] = (r.keySet & l.keySet).map(rl => rl -> (r(rl) -> l(rl))).toMap

    existsBoth.keySet.foreach(x =>
      println(s"folder ${mkPath(state.current)}/$x exists both locally and remotely, no actions")
    )

    val createdRemotely: List[Try[(GFile, File)]] = toBeUploaded.map {
      l => rParent.mkDir(l.getName).map(g => g -> l) match {
        case succ@Success((s, f)) =>
          println(s"directory ${mkPath(state.current)}/${s.name} not exists remotely, created")
          succ
        case fail@Failure(t) =>
          println(s"directory ${mkPath(state.current)}/${l.getName} not exists remotely, and creation failed because $t ${t.getStackTrace.mkString("\n\t")}")
          fail
      }
    }
    val createdLocally: List[Try[(GFile, File)]] = toBeDownloaded.map { r =>
      Try {
        val file: File = new File(lParent, r.name)
        file.mkdir()
        assert(file.exists())
        r -> file
      } match {
        case succ@Success(s) => println(s"directory ${mkPath(state.current)}/${s._2.getName} not exists locally, created")
          succ
        case fail@Failure(t) =>
          println(s"directory ${mkPath(state.current)}/${r.name} not exists locally, and creation failed because $t ${t.getStackTrace.mkString("\n\t")}")
          fail
      }
    }

    val synchronizedDirectories: Map[String, (GFile, File)] = existsBoth ++ (for {
      remote <- createdRemotely
      succ <- remote.toOption
    } yield succ._1.name -> succ) ++ (
      for {
        local <- createdLocally
        succ <- local.toOption
      } yield succ._1.name -> succ)

    synchronizedDirectories.values.toList
  }


  def synchronizeFiles(remote: List[GFile], local: List[File], rParent: GFile, lParent: File, state: State) = {
    val remoteMap = remote.map(r => r.name -> r).toMap
    val localMap = local.map(l => l.getName -> l).toMap
    val toBeUploaded: List[File] = local.filter(l => !remoteMap.keySet.contains(l.getName))
    val toBeDownloaded: List[GFile] = remote.filter(r => !localMap.keySet.contains(r.name))
    val existsBoth: Map[String, (GFile, File)] = (remoteMap.keySet & localMap.keySet) map (rl => rl -> (remoteMap(rl) -> localMap(rl))) toMap

    val uploaded: List[Try[(GFile, File)]] = toBeUploaded.map {
      l => rParent.uploadToThisDir(l).map(g => g -> l) match {
        case succ@Success((s, f)) =>
          println(s"file ${mkPath(state.current)}/${s.name} not exists remotely, uploaded")
          succ
        case fail@Failure(t) =>
          println(s"file ${mkPath(state.current)}/${l.getName} not exists remotely, and creation failed because $t ${t.getStackTrace.mkString("\n\t")}")
          fail
      }
    }
    val downloaded: List[Try[(GFile, File)]] = toBeDownloaded.map { r =>
      Try {
        val target = new File(lParent, r.name)
        r downloadTo target
        r.lastModified = target.lastModified()
        r -> target
      } match {
        case succ@Success(s) => println(s"file ${mkPath(state.current)}/${s._2.getName} not exists locally, downloaded")
          succ
        case fail@Failure(t) =>
          println(s"file ${mkPath(state.current)}/${r.name} downloading failed because $t ${t.getStackTrace.mkString("\n\t")}")
          fail
      }
    }

    val exists: Iterable[Try[(GFile, File)]] = existsBoth.map {
      case (name, (r, l)) => Try {
          val rm = r.lastModified.getValue
          val lm = l.lastModified()
          if (rm != lm) {
            if (r.md5 == l.toMD5) {
              println(s"file ${mkPath(state.current)}/${r.name} has different modification time, but the same MD5 checksum")
            } else {
              if (rm > lm) {
                r downloadTo l
                r.lastModified = l.lastModified()
              } else {
                r.updateContent(l).recover {
                  case t => throw t
                }
              }
            }
          }
        r -> l
      } match {
        case succ@Success(s) => println(s"sync of ${mkPath(state.current)}/${s._2.getName} success")
          succ
        case fail@Failure(t) =>
          println(s"sync of file ${mkPath(state.current)}/${r.name} failed because $t ${t.getStackTrace.mkString("\n\t")}")
          fail
      }
    }.toList


    val synchronized: List[(String, (GFile, File))] = (for {
      trie <- exists ++ uploaded ++ downloaded
      succ <- trie.toOption
    } yield succ._1.name -> succ) toList

    synchronized
  }
}

object Logic extends Logic