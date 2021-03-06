package io.asyncdb
package nio

import scala.collection.mutable.ArrayBuffer

/**
 * ByteVector for bytes reading, allow composite two or more [[ByteBuffers]]
 */
trait ByteVector {
  def size: Long
  def take(n: Int): Array[Byte]
  def takeWhile(p: Byte => Boolean): Array[Byte]
  def ++(r: ByteVector): ByteVector = new ByteVector.Append(this, r)
  def array: Array[Byte]
  def bufs: Vector[Buf]
  protected def takeWhile0(p: Byte => Boolean): (Array[Byte], Boolean)
}

object ByteVector {

  def apply(bytes: Array[Byte]): ByteVector =
    apply(java.nio.ByteBuffer.wrap(bytes))

  def apply(buf: Buf): ByteVector = new ByteVector {

    val slice = buf.slice()

    val init = slice.duplicate()

    def array = init.array()

    def bufs = Vector(buf)

    def size = init.remaining()

    def take(n: Int) = {
      val arr = Array.ofDim[Byte](n)
      buf.get(arr)
      arr
    }

    def takeWhile(p: Byte => Boolean) = takeWhile0(p)._1

    def takeWhile0(p: Byte => Boolean) = {

      @scala.annotation.tailrec
      def loop(
        matches: Boolean,
        rs: ArrayBuffer[Byte]): (Boolean, ArrayBuffer[Byte]) = {
        if (buf.hasRemaining()) {
          buf.mark()
          val b = slice.get()
          if (p(b)) {
            buf.reset()
            (true, rs += b)
          } else {
            rs += b
            loop(false, rs)
          }
        } else {
          (matches, rs)
        }

      }

      val abf          = new ArrayBuffer[Byte]
      val (matches, _) = loop(false, abf)
      (abf.toArray, matches)
    }
  }

  private class Append(left: ByteVector, right: ByteVector) extends ByteVector {
    def array = left.array ++ right.array
    def size  = left.size + right.size
    def take(n: Int): Array[Byte] = {
      val leftTaked = left.take(n)
      if (leftTaked.size < n) {
        leftTaked ++ right.take(n - leftTaked.size)
      } else leftTaked
    }
    def bufs = left.bufs ++ right.bufs
    def takeWhile(p: Byte => Boolean) = {
      val (bytes, isMatched) = left.takeWhile0(p)
      if (!isMatched) bytes ++ right.takeWhile(p) else bytes
    }
    def takeWhile0(p: Byte => Boolean) = {
      val (bytes, isMatched) = left.takeWhile0(p)
      if (isMatched) {
        (bytes, isMatched)
      } else {
        val (rBytes, isMatched) = right.takeWhile0(p)
        (bytes ++ rBytes, isMatched)
      }
    }
  }
}
