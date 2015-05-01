package io.github.morgaroth.gdrivesync.async.models

import java.io.{File, FileOutputStream, OutputStream}

import com.google.api.client.util.DateTime
import com.google.api.services.drive.{Drive, model}
import io.github.morgaroth.gdrivesync.api.Mime
import io.github.morgaroth.gdrivesync.async.GoogleDrive

import scala.collection.JavaConversions._
import scala.util.Try

class GFile(var _raw: model.File)(implicit _service: GoogleDrive) {
  val inTrash: Boolean = _raw.getExplicitlyTrashed
  val notTrashed = !inTrash
  val md5 = _raw.getMd5Checksum
  val id = _raw.getId
  val name = _raw.getTitle
  val created: DateTime = _raw.getCreatedDate
  val updateInfo: GFile = _service.detailedInfo(id)
  val isDirectory = _raw.getMimeType == Mime.dir
  val isDir = isDirectory
  val isFile = !isDirectory
  val lastModified = _raw.getModifiedDate
  def updateContent(newContent: File) = _service.update(this, newContent)
  def lastModified_= = (dateMillis: Long) => {
    val req: Drive#Files#Patch = _service.drive.files().patch(id, new model.File().setModifiedDate(new DateTime(dateMillis)))
    req.setSetModifiedDate(true)
    _raw = req.execute()
  }

  lazy val downloadLink = Option(_raw.getDownloadUrl) flatMap {
    case url if url.length > 0 => Some(url)
    case _ => None
  }

  def updateInfoInThis() = _raw = _service.detailedInfo(id)._raw

  def children: List[GFile] = _service.drive.children().list(id).execute().getItems.map(x => _service.detailedInfo(x.getId)).toList

  def mkDir(childName: String) = {
    val result = _service.mkDir(id, childName)
    _raw = _service.detailedInfo(id)._raw
    result
  }

  def uploadToThisDir(f: File) = _service.upload(f: File, id)

  def downloadTo(target: OutputStream): Try[OutputStream] = _service.download(this, target)

  def downloadTo(target: File): Try[OutputStream] = downloadTo(new FileOutputStream(target))


  override def toString = s"GFile(id=$id, name=$name, created=$created, lastModified=$lastModified, isDirectory=$isDirectory, mime=${_raw.getMimeType}})"
}

object GFile {
  implicit def convert(f: model.File)(implicit service: GoogleDrive) = new GFile(f)

  implicit def convertList(list: List[model.File])(implicit service: GoogleDrive): List[GFile] = list.map(x => x: GFile)
}
