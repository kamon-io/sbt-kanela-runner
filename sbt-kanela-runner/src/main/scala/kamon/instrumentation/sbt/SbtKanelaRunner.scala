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

import sbt._
import Keys._
import net.bytebuddy.agent.ByteBuddyAgent

object SbtKanelaRunner extends AutoPlugin {

  val KanelaRunner = config("kanela-runner")
  val DefaultKanelaVersion = "1.0.18"
  val InstrumentationClassLoaderProp = "kanela.instrumentation.classLoader"

  object Keys {
    val kanelaVersion = SettingKey[String]("kanela-version")
    val kanelaAgentJar = TaskKey[File]("kanela-agent-jar")
    val kanelaRunnerJvmForkOptions = TaskKey[Seq[String]]("kanela-runner-options")
  }

  import Keys._

  override def trigger = AllRequirements
  override def requires = plugins.JvmPlugin
  override def projectConfigurations: Seq[Configuration] = Seq(KanelaRunner)

  override def projectSettings: Seq[Setting[_]] = Seq(
    kanelaVersion := DefaultKanelaVersion,
    kanelaAgentJar := findKanelaAgentJar.value,
    kanelaRunnerJvmForkOptions := jvmForkOptions.value,
    libraryDependencies += kanelaAgentDependency.value,
    Compile / run / runner := kanelaRunnerTask.value
  )

  private def kanelaAgentDependency = Def.setting {
    "io.kamon" % "kanela-agent" % kanelaVersion.value % KanelaRunner.name
  }

  private def findKanelaAgentJar = Def.task {
    update.value.matching(
      moduleFilter(organization = "io.kamon", name = "kanela-agent") &&
      artifactFilter(`type` = "jar")
    ).head
  }

  private def jvmForkOptions = Def.task {
    Seq(s"-javaagent:${kanelaAgentJar.value.getAbsolutePath}")
  }

  private def kanelaRunnerTask: Def.Initialize[Task[ScalaRun]] = Def.taskDyn {
    if ((run / fork).value) {
      Def.task {
        val currentForkOptions = forkOptions.value
        val runForkOptions = currentForkOptions.withRunJVMOptions {
          ((run / javaOptions).value ++ currentForkOptions.runJVMOptions).toVector
        }

        new ForkRun(runForkOptions)
      }
    } else {
        val kanelaJar = kanelaAgentJar.value
        val previousRun = (Compile / run / runner).value
        val trap = trapExit.value

        Def.task {

          val layeringStrategy = classLoaderLayeringStrategy.value
          if(layeringStrategy != ClassLoaderLayeringStrategy.Flat) {
            sLog.value.warn(
              "The Kanela instrumentation can only be attached on the current JVM when using the " +
              "ClassLoaderLayeringStrategy.Flat strategy but you are currently using [" + layeringStrategy +
              "]. The application will run without instrumentation and might fail to behave properly."
            )

            previousRun
          } else {
            previousRun match {
              case run: Run => new RunAndAttachKanelaOnResolvedClassLoader(kanelaJar: File, run, trap)
              case _        => previousRun
            }
          }
        }
    }
  }

  @volatile private var hasBeenAttached = false

  def attachWithInstrumentationClassLoader(kanelaAgentJar: File, instrumentationClassLoader: ClassLoader,
      clearRegistry: Boolean): Unit = {

    withInstrumentationClassLoader(instrumentationClassLoader) {
      if(!hasBeenAttached) {
        ByteBuddyAgent.attach(kanelaAgentJar, pid())
        hasBeenAttached = true

      } else {

        // We know that if the agent has been attached, its classes are in the System ClassLoader so we try to find
        // the Kanela class from there and reload it.
        Class.forName("kanela.agent.Kanela", true, ClassLoader.getSystemClassLoader)
          .getDeclaredMethod("reload", classOf[Boolean])
          .invoke(null, Boolean.box(clearRegistry))
      }
    }
  }

  private def withInstrumentationClassLoader[T](classLoader: ClassLoader)(thunk: => T): T = {
    try {
      System.getProperties.put(InstrumentationClassLoaderProp, classLoader)
      thunk
    } finally {
      System.getProperties.remove(InstrumentationClassLoaderProp)
    }
  }

  private def pid(): String = {
    val jvm = ManagementFactory.getRuntimeMXBean.getName
    jvm.substring(0, jvm.indexOf('@'))
  }
}
