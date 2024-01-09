package sbt

import kamon.instrumentation.sbt.SbtKanelaRunner

import scala.util.Try

/**
  * This class overrides the runWithLoader function introduced in SBT 1.3.x with the purpose of capturing the
  * ClassLoader and using it to attach the Kanela agent on the current JVM.
  */
class RunAndAttachKanelaOnResolvedClassLoader(kanelaJar: File, newLoader: Seq[File] => ClassLoader, trapExit: Boolean)
  extends Run(newLoader, trapExit) {

  def this(kanelaJar: File, run: Run, trapExit: Boolean) =
    this(kanelaJar, run.newLoader, trapExit)

  private[sbt] override def runWithLoader(
      loader: ClassLoader,
      classpath: Seq[File],
      mainClass: String,
      options: Seq[String],
      log: Logger
    ): Try[Unit] = {

    SbtKanelaRunner.attachWithInstrumentationClassLoader(kanelaJar, loader, true)
    super.runWithLoader(loader, classpath, mainClass, options, log)
  }
}
