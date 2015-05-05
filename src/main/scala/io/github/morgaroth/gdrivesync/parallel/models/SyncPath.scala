package io.github.morgaroth.gdrivesync.parallel.models

import java.io.File

class SyncPath(val pathSegements: List[String], localRoot: File) {
  def :+(segment: String): SyncPath = new SyncPath(pathSegements :+ segment, localRoot)

  def :+(file: File): SyncPath = this :+ file.getName

  def :+(file: GFile): SyncPath = this :+ file.name

  override def toString: String = pathSegements.mkString("/", "/", "")
}

object SyncPath {
  def apply(localRoot: File)(segments: String*) = new SyncPath(segments.toList, localRoot)

  def empty(localRoot: File) = SyncPath(localRoot)()
}
