package io.github.morgaroth.gdrivesync.helpers

import io.github.morgaroth.gdrivesync.api.GoogleDrive
import io.github.morgaroth.gdrivesync.models.GFile


object tree {
  val identUnit = "   "

  private def printDir(files: List[GFile], ident: String = "") {
    files.foreach { f =>
      println(s"$ident${f.name} (${if (f.isDir) "dir" else f._raw.getMimeType})")
      if (f.isDir) {
        printDir(f.children, s"$ident$identUnit")
      }
    }
  }

  def apply(implicit service: GoogleDrive) = printDir(service.rootDir)
}
