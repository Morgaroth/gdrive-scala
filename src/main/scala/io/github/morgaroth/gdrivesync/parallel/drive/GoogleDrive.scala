package io.github.morgaroth.gdrivesync.parallel.drive

import java.io.{File, FileOutputStream, OutputStream}
import java.nio.file.Files

import akka.event.LoggingAdapter
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.media.MediaHttpUploader.MINIMUM_CHUNK_SIZE
import com.google.api.client.googleapis.media.MediaHttpUploader.UploadState.{INITIATION_COMPLETE, INITIATION_STARTED, MEDIA_COMPLETE, MEDIA_IN_PROGRESS}
import com.google.api.client.googleapis.media.{MediaHttpUploader, MediaHttpUploaderProgressListener}
import com.google.api.client.http.FileContent
import com.google.api.client.util.DateTime
import com.google.api.services.drive.model.{About, ParentReference}
import com.google.api.services.drive.{Drive, model}
import io.github.morgaroth.gdrivesync.parallel.models
import io.github.morgaroth.gdrivesync.parallel.models.{GFile, SyncPath}

import scala.collection.JavaConversions._
import scala.util.{Success, Try}


class FileHasntDownloadableLink(filePath: String) extends IllegalArgumentException(s"file $filePath hasn't downloadable link")

object GoogleDrive {
  def default = new GoogleDrive(Auth.tryLoadPreviouslySaved().get)
}

class GoogleDrive(credentials: Credential) {
  val drive: Drive = new Drive.Builder(Auth.transport, Auth.jacksonFactory, credentials).setApplicationName(AppName.name).build()
  implicit val service = this

  def listFiles: List[models.GFile] = {
    def getFiles(req: Drive#Files#List): List[models.GFile] = {
      val res = req.execute()
      val files = res.getItems.iterator().toList
      req.setPageToken(res.getNextPageToken)
      files ::: (if (req.getPageToken != null && req.getPageToken.length > 0) getFiles(req) else List.empty)
    }
    getFiles(drive.files().list())
  }

  val about: About = drive.about().get().execute()

  def detailedInfo(fileId: String): GFile = drive.files().get(fileId).execute()

  def detailedInfoOpt(fileId: String) = Try(drive.files().get(fileId).execute()).map(x => x: GFile)

  def rootGFile: GFile = detailedInfo(about.getRootFolderId)

  def rootDir: List[GFile] = rootGFile.children

  def update(gFile: GFile, newContent: File): Try[GFile] = Try {
    assert(newContent.exists() && newContent.isFile && newContent.canRead)
    val mime = Files.probeContentType(newContent.toPath)
    val mediaContent = new FileContent(mime, newContent)
    val request = drive.files().update(gFile.id, gFile._raw, mediaContent)
    request.set("uploadType", "resumable")
      .getMediaHttpUploader
      .setDirectUploadEnabled(false)
      .setChunkSize(2 * MINIMUM_CHUNK_SIZE)
      .setProgressListener(new MediaHttpUploaderProgressListener {
      override def progressChanged(uploader: MediaHttpUploader): Unit = {
        val state = uploader.getUploadState match {
          case INITIATION_STARTED => "in during initialization..."
          case INITIATION_COMPLETE => "initialized, uploading..."
          case MEDIA_IN_PROGRESS => s"progress: ${(uploader.getProgress * 100).toInt}%"
          case MEDIA_COMPLETE => "is completed."
          case another => another.name()
        }
        println(s"uploading ${newContent.getName} $state")
      }
    })
    request.execute()
  }

  def upload(file: File, parent: String = about.getRootFolderId): Try[GFile] =
    Try {
      assert(file.exists() && file.isFile && file.canRead)
      println(s"uploading file ${file.getAbsolutePath} to be under $parent, detailedParent = ${detailedInfo(parent)}")
      val mime = Files.probeContentType(file.toPath)
      val body = new model.File()
        .setTitle(file.getName).setMimeType(mime)
        .setParents(List(new ParentReference().setId(parent)))
      val mediaContent = new FileContent(mime, file)
      val insert: Drive#Files#Insert = drive.files().insert(body, mediaContent)
      insert.set("uploadType", "resumable")
        .getMediaHttpUploader
        .setDirectUploadEnabled(false)
        .setChunkSize(2 * MINIMUM_CHUNK_SIZE)
        .setProgressListener(new MediaHttpUploaderProgressListener {
        override def progressChanged(uploader: MediaHttpUploader): Unit = {
          val state = uploader.getUploadState match {
            case INITIATION_STARTED => "in during initialization..."
            case INITIATION_COMPLETE => "initialized, uploading..."
            case MEDIA_IN_PROGRESS => s"progress: ${(uploader.getProgress * 100).toInt}%"
            case MEDIA_COMPLETE => "is completed."
            case another => another.name()
          }
          println(s"uploading ${file.getName} $state")
        }
      })
      insert.execute()
    }

  def setModifiedDate(id: String, dateMillis: Long): GFile = {
    val req = drive.files().patch(id, new model.File().setModifiedDate(new DateTime(dateMillis)))
    req.setSetModifiedDate(true)
    req.execute()
  }


  def download(file: GFile, path: String): Try[OutputStream] = download(file, new FileOutputStream(path))

  def download(file: GFile, output: OutputStream): Try[OutputStream] = Try {
    file.downloadLink.map { link =>
      drive.files().get(file.id).executeMediaAndDownloadTo(output)
      output
    }.getOrElse(throw new IllegalArgumentException("Google File hasn't downloadable link"))
  }

  def mkDir(parentId: String, name: String) = {
    val existing = detailedInfo(parentId).children.find(_.name == name)
    existing.map(Success(_)).getOrElse(Try {
      val body = new model.File().setTitle(name).setMimeType(Mime.dir).setParents(List(new ParentReference().setId(parentId)))
      val execute: GFile = drive.files().insert(body).execute()
      execute: GFile
    })
  }

  // --------------------------------------------------------------
  // new API
  // --------------------------------------------------------------

  private def uploadListener(action: String, progress: LoggingAdapter): MediaHttpUploaderProgressListener = new MediaHttpUploaderProgressListener {
    override def progressChanged(uploader: MediaHttpUploader): Unit = {
      val state = uploader.getUploadState match {
        case INITIATION_STARTED => "in during initialization..."
        case INITIATION_COMPLETE => "initialized, uploading..."
        case MEDIA_IN_PROGRESS => s"progress: ${(uploader.getProgress * 100).toInt}%"
        case MEDIA_COMPLETE => "is completed."
        case another => another.name()
      }
      progress.info(s"$action $state")
    }
  }

  def newUpload(file: File, parent: GFile, path: SyncPath, progress: LoggingAdapter): Try[GFile] =
    Try {
      assert(file.canRead && file.isFile && file.exists(), s"file isn't ready to upload (exists=${file.exists()}, canRead=${file.canRead}, isFile=${file.isFile})")
      val mime = Files.probeContentType(file.toPath)
      val body = new model.File()
        .setTitle(file.getName).setMimeType(mime)
        .setParents(List(new ParentReference().setId(parent.id)))
      val mediaContent = new FileContent(mime, file)
      val uploadRequest = drive.files().insert(body, mediaContent)
      uploadRequest.set("uploadType", "resumable")
        .getMediaHttpUploader
        .setDirectUploadEnabled(false)
        .setChunkSize(2 * MINIMUM_CHUNK_SIZE)
        .setProgressListener(uploadListener(s"uploading of ${file.getName}", progress))
      uploadRequest.execute()
    }

  def newDownload(fromRemote: GFile, to: File, path: SyncPath): Try[File] =
    Try {
      fromRemote.downloadLink.map { link =>
        to.createNewFile()
        assert(to.isFile && to.canWrite)
        drive.files().get(fromRemote.id).executeMediaAndDownloadTo(new FileOutputStream(to))
        to
      }.getOrElse(throw new IllegalArgumentException("Google File hasn't downloadable link"))
    }

  def newUpdate(from: File, to: GFile, path: SyncPath, progress: LoggingAdapter): Try[GFile] =
    Try {
      assert(from.exists() && from.isFile && from.canRead)
      val mime = Files.probeContentType(from.toPath)
      val mediaContent = new FileContent(mime, from)
      val updateRequest = drive.files().update(to.id, to._raw, mediaContent)
      updateRequest.set("uploadType", "resumable")
        .getMediaHttpUploader
        .setDirectUploadEnabled(false)
        .setChunkSize(2 * MINIMUM_CHUNK_SIZE)
        .setProgressListener(uploadListener(s"updating of ${path :+ from}", progress))
      updateRequest.execute()
    }

  def newMkDir(parent: GFile, name: String): Try[GFile] = {
    val existing = detailedInfo(parent.id).children.find(_.name == name)
    existing.map(Success(_)).getOrElse(Try {
      val body = new model.File()
        .setTitle(name)
        .setMimeType(Mime.dir)
        .setParents(List(new ParentReference().setId(parent.id)))
      val execute: GFile = drive.files().insert(body).execute()
      execute: GFile
    })
  }
}