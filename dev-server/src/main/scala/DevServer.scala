import java.io.File

@main def hello =
  val unitdCommand = sys.env.getOrElse(
    "TWOTM8_UNITD_COMMAND",
    sys.error("TWOTM8_UNITD_COMMAND is missing from env")
  )
  val cwd = sys.env.getOrElse(
    "TWOTM8_SERVER_CWD",
    sys.error("TWOTM8_SERVER_CWD is missing from env")
  )

  import sys.process.*

  val bgProc = Process(unitdCommand, cwd = new File(cwd)).run()

  sys.addShutdownHook {
    println("Caught shutdown, killing process")
    bgProc.destroy
  }
  val ev = bgProc.exitValue
  println(s"Process finished naturally with code $ev")
end hello
