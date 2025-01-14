package twotm8.client

import scala.scalanative.unsafe.*
import scala.scalanative.libc.stdlib.{malloc, free}

private[client] opaque type Memory = (String, () => Unit)
private[client] object Memory:
  extension (f: Memory)
    def deallocate() =
      f._2()

private[client] type Captured[D] = D
private[client] object Captured:
  def unsafe[D <: AnyRef: Tag](value: D): (Ptr[Captured[D]], Memory) =
    import scalanative.runtime.*

    val rawptr = malloc(sizeof[Captured[D]])
    val mem = fromRawPtr[Captured[D]](rawptr)
    val deallocate: Memory =
      (
        value.toString(),
        () =>
          GCRoots.removeRoot(value.asInstanceOf[Object])
          free(toRawPtr[Captured[D]](mem))
      )

    Intrinsics.storeObject(rawptr, value)

    GCRoots.addRoot(value)

    (mem, deallocate)
  end unsafe

end Captured

private[client] object GCRoots:
  private val references = new java.util.IdentityHashMap[Object, Unit]
  def addRoot(o: Object): Unit = references.put(o, ())
  def removeRoot(o: Object): Unit = references.remove(o)
