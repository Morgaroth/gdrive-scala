package io.github.morgaroth.gdrivesync.parallel.models

import java.io.{File, FileOutputStream, OutputStream}

import com.google.api.client.util.DateTime
import com.google.api.services.drive.{Drive, model}
import io.github.morgaroth.gdrivesync.parallel.drive.{Mime, GoogleDrive}

import scala.collection.JavaConversions._
import scala.util.Try

class GFile(var _raw: model.File)(implicit _service: GoogleDrive) {
  val inTrash: Boolean = Option(_raw.getExplicitlyTrashed).exists(x => x)
  val notTrashed = !inTrash
  val md5 = _raw.getMd5Checksum
  val id = _raw.getId
  val name = _raw.getTitle
  val created: DateTime = _raw.getCreatedDate
  val isDirectory = _raw.getMimeType == Mime.dir
  val isDir = isDirectory
  val isFile = !isDirectory
  val lastModified = _raw.getModifiedDate
  lazy val downloadLink = Option(_raw.getDownloadUrl) flatMap {
    case url if url.length > 0 => Some(url)
    case _ => None
  }
  def children: List[GFile] = _service.drive.children().list(id).execute().getItems.map(x => _service.detailedInfo(x.getId)).toList

  override def toString = s"GFile(id=$id, name=$name, created=$created, lastModified=$lastModified, isDirectory=$isDirectory, mime=${_raw.getMimeType}})"
}

object GFile {
  implicit def convert(f: model.File)(implicit service: GoogleDrive) = new GFile(f)

  implicit def convertList(list: List[model.File])(implicit service: GoogleDrive): List[GFile] = list.map(x => x: GFile)
}
