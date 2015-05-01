package io.github.morgaroth.gdrivesync.parallel

import java.io.File

object Application {
  def main(args: Array[String]) {
    val path = if (args.length > 0) args(0) else "."
    val localRoot: File = new File(path).getCanonicalFile
    localRoot.mkdirs()
    println(s"DEBUG: ${localRoot.getAbsolutePath}")

  }
}
