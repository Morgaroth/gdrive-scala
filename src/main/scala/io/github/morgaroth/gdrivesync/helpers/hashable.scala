package io.github.morgaroth.gdrivesync.helpers

import java.io.File

import scala.io.Source

trait hashable {

  def hasher = java.security.MessageDigest.getInstance("MD5")

  class HashingRichString(string: String) {
    def toMD5 = hasher.digest(string.getBytes)
  }

  class HashingRichFile(f: File) {
    def toMD5 = {
      val hash = Source.fromFile(f).iter.grouped(500).foldLeft(hasher) {
        case (h, line) =>
          h.update(line.toArray.map(_.toByte))
          h
      }
      hash.digest().map(0xFF & _).map("%02x".format(_)).mkString
    }
  }

  import scala.language.implicitConversions

  implicit def wrapAsHashableString(string: String): HashingRichString = new HashingRichString(string)

  implicit def wrapAsHashableFile(f: File): HashingRichFile = new HashingRichFile(f)
}

object hashable extends hashable