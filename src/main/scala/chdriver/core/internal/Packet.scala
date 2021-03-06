package chdriver.core.internal

import java.io.DataInputStream

import chdriver.core.ClickhouseVersionSpecific.DBMS_MIN_REVISION_WITH_TOTAL_ROWS_IN_PROGRESS

sealed trait Packet

case class DataPacket(block: Block) extends Packet

case class ExceptionPacket(text: String) extends Exception(text) with Packet

object ExceptionPacket {
  def from(in: DataInputStream): ExceptionPacket = {
    import chdriver.core.internal.Protocol.DataInputStreamOps
    val code = in.readInt32()
    val name = in.readString()
    val message = in.readString()
    val stackTrace = in.readString()
    val nestedException: Option[ExceptionPacket] =
      if (in.readUInt8() > 0) {
        Some(from(in))
      } else None
    val text =
      s"""
         |DRIVER EXCEPTION:
         |  code=$code
         |  name=$name
         |  message=$message
         |  stacktrace:
         |  $stackTrace
         |  ${nestedException.map(_.toString).getOrElse("")}
       """.stripMargin
    new ExceptionPacket(text)
  }
}

case class ProfileInfoPacket(rows: Int,
                             blocks: Int,
                             bytes: Int,
                             appliedLimit: Boolean,
                             rowsBeforeLimit: Int,
                             calculatedRowsBeforeLimit: Boolean)
    extends Packet

object ProfileInfoPacket {
  def from(in: DataInputStream): ProfileInfoPacket = {
    import chdriver.core.internal.Protocol.DataInputStreamOps
    ProfileInfoPacket(in.readAsUInt128(), in.readAsUInt128(), in.readAsUInt128(), in.readUInt8() > 0, in.readAsUInt128(), in.readUInt8() > 0)
  }
}

// todo C++ how these values are calculated on CH side
case class ProgressPacket(newRows: Int, newBytes: Int, newTotalRows: Int) extends Packet

case object ProgressPacket {
  def from(in: DataInputStream, serverRevision: Int): ProgressPacket = {
    import chdriver.core.internal.Protocol.DataInputStreamOps
    ProgressPacket(
      in.readAsUInt128(),
      in.readAsUInt128(),
      if (serverRevision > DBMS_MIN_REVISION_WITH_TOTAL_ROWS_IN_PROGRESS) in.readAsUInt128()
      else -1
    )
  }
}

case object EndOfStreamPacket extends Packet

case object UnrecognizedPacket extends Packet
