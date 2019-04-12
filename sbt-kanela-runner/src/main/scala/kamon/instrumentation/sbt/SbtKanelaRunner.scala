/*
 * =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.instrumentation.sbt

import java.lang.management.ManagementFactory
import java.lang.reflect.Method
import java.lang.reflect.Modifier.{isPublic, isStatic}

import sbt._
import Keys._
import net.bytebuddy.agent.ByteBuddyAgent

object SbtKanelaRunner extends AutoPlugin {

  val KanelaRunner = config("kanela-runner")
  val DefaultAspectJVersion = "0.0.17"
  val SecondaryClassLoaderProp = "kanela.instrumentation.secondaryClassLoader"

  object Keys {
    val kanelaVersion = SettingKey[String]("kanela-version")
    val kanelaAgentJar = TaskKey[File]("kanela-agent-jar")
    val kanelaRunnerJvmForkOptions = TaskKey[Seq[String]]("aspectj-runner-options")
  }

  import Keys._

  override def trigger = AllRequirements
  override def requires = plugins.JvmPlugin
  override def projectConfigurations: Seq[Configuration] = Seq(KanelaRunner)

  override def projectSettings: Seq[Setting[_]] = Seq(
    kanelaVersion := DefaultAspectJVersion,
    kanelaAgentJar := findKanelaAgentJar.value,
    kanelaRunnerJvmForkOptions := jvmForkOptions.value,
    libraryDependencies += kanelaAgentDependency.value,
    runner in run in Compile := kanelaRunner.value
  )

  def kanelaAgentDependency = Def.setting {
    "io.kamon" % "kanela-agent" % kanelaVersion.value % KanelaRunner.name
  }

  def findKanelaAgentJar = Def.task {
    update.value.matching(
      moduleFilter(organization = "io.kamon", name = "kanela-agent") &&
      artifactFilter(`type` = "jar")
    ).head
  }

  def jvmForkOptions = Def.task {
    Seq(s"-javaagent:${kanelaAgentJar.value.getAbsolutePath}")
  }

  def kanelaRunner: Def.Initialize[Task[ScalaRun]] = Def.taskDyn {
    if ((fork in run).value) {
      Def.task {
        val forkOptions = ForkOptions(
          javaHome = javaHome.value,
          outputStrategy = outputStrategy.value,
          bootJars = Vector.empty[java.io.File],
          workingDirectory = Some(baseDirectory.value),
          runJVMOptions = (javaOptions.value ++ kanelaRunnerJvmForkOptions.value).toVector,
          connectInput = connectInput.value,
          envVars = Map.empty[String, String]
        )
        new ForkRun(forkOptions)
      }
    } else {
      Def.task {
        new RunAndAttachKanela(kanelaAgentJar.value, scalaInstance.value, trapExit.value, taskTemporaryDirectory.value)
      }
    }
  }

  @volatile private var hasBeenAttached = false

  def attachWithInstrumentationClassLoader(kanelaAgentJar: File, instrumentationClassLoader: ClassLoader): Unit = {

    withSecondaryClassLoader(instrumentationClassLoader) {
      if(!hasBeenAttached) {
        println("TRYING TO ATTACH KANELA!!!")
        ByteBuddyAgent.attach(kanelaAgentJar, pid())
        hasBeenAttached = true

      } else {
        println("TRYING TO RELOAD KANELA!!!")
        Class.forName("kanela.agent.Kanela", true, ClassLoader.getSystemClassLoader)
          .getDeclaredMethod("reload")
          .invoke(null)
      }
    }



  }

  private def withSecondaryClassLoader[T](classLoader: ClassLoader)(thunk: => T): T = {
    try {
      System.getProperties.put(SecondaryClassLoaderProp, classLoader)
      thunk
    } finally {
      System.getProperties.remove(SecondaryClassLoaderProp)
    }
  }

  private def pid(): String = {
    val jvm = ManagementFactory.getRuntimeMXBean.getName
    jvm.substring(0, jvm.indexOf('@'))
  }

  /**
    * This class is a dirty copy of sbt.Run, with all required dependencies to make sure we can attach the Kanela agent
    * on runtime.
    */
  class RunAndAttachKanela(kanelaAgentJar: File, instance: SbtCross.ScalaInstance, trapExit: Boolean, nativeTmp: File)
      extends Run(instance, trapExit, nativeTmp) {

    /** Runs the class 'mainClass' using the given classpath and options using the scala runner.*/
    override def run(mainClass: String, classpath: Seq[File], options: Seq[String], log: Logger) = {
      log.info("Running " + mainClass + " " + options.mkString(" "))

      def execute() =
        try { run0(mainClass, classpath, options, log) }
        catch { case e: java.lang.reflect.InvocationTargetException => throw e.getCause }

      if (trapExit) Run.executeTrapExit(execute(), log) else SbtCross.directExecute(execute(), log)
    }

    private def run0(mainClassName: String, classpath: Seq[File], options: Seq[String], log: Logger): Unit = {
      log.debug("  Classpath:\n\t" + classpath.mkString("\n\t"))
      val applicationLoader = makeLoader(classpath, instance, nativeTmp)

      //printHierarchy(applicationLoader)

      attachWithInstrumentationClassLoader(kanelaAgentJar, applicationLoader)
      val main = getMainMethod(mainClassName, applicationLoader)
      invokeMain(applicationLoader, main, options)
    }

    var depth = 0
    var printed = 0

    def printHierarchy(classLoader: ClassLoader): Unit = {
      if(classLoader.getParent != null) {
        depth += 1
        printed += 1
        printHierarchy(classLoader.getParent)
      }

      val padding = "=" * (depth - printed)
      printed -= 1
      println(s"$padding ClassLoader: ${classLoader.toString}, implementation class: ${classLoader.getClass.toString}")
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

}
