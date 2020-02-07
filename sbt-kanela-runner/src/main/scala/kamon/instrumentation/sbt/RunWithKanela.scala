package sbt

import java.lang.reflect.Method
import java.lang.reflect.Modifier.{isPublic, isStatic}

import kamon.instrumentation.sbt.SbtKanelaRunner.attachWithInstrumentationClassLoader
import kamon.instrumentation.sbt.{SbtCross, SbtKanelaRunner}

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

/**
  * This class is a dirty copy of sbt.Run for SBT 1.2, with all required dependencies to make sure we can attach the
  * Kanela agent  on runtime.
  */
class RunAndAttachKanela(kanelaAgentJar: File, instance: SbtCross.ScalaInstance, trapExit: Boolean, nativeTmp: File)
  extends Run(instance, trapExit, nativeTmp) {

  /** Runs the class 'mainClass' using the given classpath and options using the scala runner.*/
  override def run(mainClass: String, classpath: Seq[File], options: Seq[String], log: Logger): Try[Unit] = {
    log.info("Running " + mainClass + " " + options.mkString(" "))

    def execute() =
      try { run0(mainClass, classpath, options, log) }
      catch { case e: java.lang.reflect.InvocationTargetException => throw e.getCause }

    if (trapExit) Run.executeTrapExit(execute(), log) else SbtCross.directExecute(execute(), log)
  }

  private def run0(mainClassName: String, classpath: Seq[File], options: Seq[String], log: Logger): Unit = {
    log.debug("  Classpath:\n\t" + classpath.mkString("\n\t"))
    val applicationLoader = makeLoader(classpath, instance, nativeTmp)
    attachWithInstrumentationClassLoader(kanelaAgentJar, applicationLoader, true)
    val main = getMainMethod(mainClassName, applicationLoader)
    invokeMain(applicationLoader, main, options)
  }

  private def createClasspathResources(appPaths: Seq[File], bootPaths: Seq[File]): Map[String, String] = {
    def make(name: String, paths: Seq[File]) = name -> Path.makeString(paths)
    Map(make(SbtCross.AppClassPath, appPaths), make(SbtCross.BootClassPath, bootPaths))
  }

  private def makeLoader(classpath: Seq[File], instance: SbtCross.ScalaInstance, nativeTemp: File): ClassLoader = {
    SbtCross.toLoader(classpath, createClasspathResources(classpath, instance.allJars), nativeTemp)
  }

  private def invokeMain(loader: ClassLoader, main: Method, options: Seq[String]): Unit = {
    val currentThread = Thread.currentThread
    val oldLoader = Thread.currentThread.getContextClassLoader
    currentThread.setContextClassLoader(loader)
    try { main.invoke(null, options.toArray[String]) }
    catch {
      case t: Throwable =>
        t.getCause match {
          case e: java.lang.IllegalAccessError =>
            val msg = s"Error running $main.\n$e\n" +
              "If using a layered classloader, this can occur if jvm package private classes are " +
              "accessed across layers. This can be fixed by changing to the Flat or " +
              "ScalaInstance class loader layering strategies."
            throw new IllegalAccessError(msg)
          case _ => throw t
        }
    }
    finally { currentThread.setContextClassLoader(oldLoader) }
  }

  override def getMainMethod(mainClassName: String, loader: ClassLoader) = {
    val mainClass = Class.forName(mainClassName, true, loader)
    val method = mainClass.getMethod("main", classOf[Array[String]])
    // jvm allows the actual main class to be non-public and to run a method in the non-public class,
    //  we need to make it accessible
    method.setAccessible(true)
    val modifiers = method.getModifiers
    if (!isPublic(modifiers)) throw new NoSuchMethodException(mainClassName + ".main is not public")
    if (!isStatic(modifiers)) throw new NoSuchMethodException(mainClassName + ".main is not static")
    method
  }
}
