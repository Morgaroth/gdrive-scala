package io.github.morgaroth.gdrivesync.async

import java.io.File

import io.github.morgaroth.gdrivesync.async.models.{SyncPath, GFile}

import scala.util.Try

package object actors {

  //@formatter:off
  case class FilePair(local: File, remote: Option[GFile], localParent: File, remoteParent: GFile)
  case class SyncFile(pair: FilePair, path: SyncPath, service: GoogleDrive)
  case object Done
  //@formatter:on

}
