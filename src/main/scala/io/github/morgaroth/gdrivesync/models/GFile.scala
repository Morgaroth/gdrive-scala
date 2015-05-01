package io.github.morgaroth.gdrivesync.models

import java.io.{FileOutputStream, File, OutputStream}

import com.google.api.client.util.{Key, DateTime}
import com.google.api.services.drive.{model, Drive}
import io.github.morgaroth.gdrivesync.api.{Mime, GoogleDrive}

import scala.collection.JavaConverters._
import scala.language.implicitConversions
import scala.util.Try

class GFile(var _raw: model.File)(implicit _service: GoogleDrive) {
  def inTrash: Boolean = _raw.getExplicitlyTrashed

  def notTrashed = !inTrash

  def md5 = _raw.getMd5Checksum

  def updateInfo: GFile = _service.detailedInfo(id)

  def isDirectory = _raw.getMimeType == Mime.dir

  def isDir = isDirectory

  def isFile = !isDirectory

  def id = _raw.getId

  def name = _raw.getTitle

  def created: DateTime = _raw.getCreatedDate

  def lastModified = _raw.getModifiedDate

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

  def children: List[GFile] = _service.drive.children().list(id).execute().getItems.asScala.map(x => _service.detailedInfo(x.getId)).toList

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
