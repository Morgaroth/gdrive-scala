package io.github.morgaroth.gdrivesync.parallel.helpers

import java.io.{FileInputStream, BufferedInputStream, File}

import scala.io.Source
import scala.language.postfixOps
import scala.util.Try

trait hashable {

  def hasher = java.security.MessageDigest.getInstance("MD5")

  class HashingRichString(string: String) {
    def toMD5 = hasher.digest(string.getBytes)
  }

  class HashingRichFile(f: File) {
    def toMD5 = toMD5Binary2

    def toMD5Text = {
      val hash = Source.fromFile(f).iter.grouped(500).foldLeft(hasher) {
        case (h, line) =>
          h.update(line.toArray.map(_.toByte))
          h
      }
      hash.digest().map(0xFF & _).map("%02x".format(_)).mkString
    }

    def toMD5Binary = Try {
      val hash = hasher
      val bis = new BufferedInputStream(new FileInputStream(f))
      val dim = Array.ofDim[Byte](512)
      var skip = 0
      Stream.continually(bis.read()).takeWhile(_ >= 0).map(_.toByte).grouped(500).map(_.toArray).foreach(hash.update)
      hash.digest().map(0xFF & _).map("%02x".format(_)).mkString
    } recover {
      case th: Throwable =>
        throw new IllegalArgumentException(s"calculating md5 from file ${f.getAbsolutePath} (canonical ${f.getAbsolutePath}) failed", th)
    } get

    def toMD5Binary2 = Try {
      val hash = hasher
      val is = new BufferedInputStream(new FileInputStream(f))
      val buf = Array.ofDim[Byte](512)
      var read = -1
      do {
        read = is.read(buf)
        if (read > 0) hash.update(buf.take(read))
      } while (read >= 0)
      hash.digest().map(0xFF & _).map("%02x".format(_)).mkString
    } recover {
      case th: Throwable =>
        throw new IllegalArgumentException(s"calculating md5 from file ${f.getAbsolutePath} (canonical ${f.getAbsolutePath}) failed", th)
    } get
  }

  import scala.language.implicitConversions

  implicit def wrapAsHashableString(string: String): HashingRichString = new HashingRichString(string)

  implicit def wrapAsHashableFile(f: File): HashingRichFile = new HashingRichFile(f)
}

object hashable extends hashable