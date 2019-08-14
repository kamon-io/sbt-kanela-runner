/*
 * =========================================================================================
 * Copyright © 2013-2015 the kamon project <http://kamon.io/>
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

package kamon.instrumentation.sbt.play

import java.net.URL

import _root_.play.sbt.PlayImport.PlayKeys._
import _root_.play.sbt.{Colors, Play, PlayRunHook}
import com.lightbend.sbt.javaagent.JavaAgent
import com.lightbend.sbt.javaagent.JavaAgent.JavaAgentKeys.javaAgents
import kamon.instrumentation.sbt.SbtKanelaRunner.Keys.kanelaVersion
import kamon.instrumentation.sbt.{KanelaOnSystemClassLoader, SbtKanelaRunner}
import play.sbt.run.KanelaPlayRun
import sbt.Keys._
import sbt._

object SbtKanelaRunnerPlay extends AutoPlugin {

  override def trigger = AllRequirements
  override def requires = Play && SbtKanelaRunner && JavaAgent

  override def projectSettings: Seq[Setting[_]] = Seq(
    Keys.run in Compile := KanelaPlayRun.playWithKanelaRunTask.evaluated,
    playRunHooks += runningWithKanelaNotice.value,
    javaAgents += "io.kamon" % "kanela-agent" % kanelaVersion.value
  )

  def runningWithKanelaNotice: Def.Initialize[Task[RunningWithKanelaNotice]] = Def.task {
    new RunningWithKanelaNotice(streams.value.log)
  }

  class RunningWithKanelaNotice(log: Logger) extends PlayRunHook {
    override def beforeStarted(): Unit = {
      log.info(Colors.green("Running the application with the Kanela agent"))
    }
  }

  /**
    * This ClassLoader gives a special treatment to the H2 JDBC Driver classes so that we can both instrument them while
    * running on Development mode by loading the JDBC classes from the same ClassLoader as Kamon (the Application
    * ClassLoader) and at the same time, allow for the H2 data to persist across runs, by letting loading of all other
    * classes just bubble up to the common ClassLoader where the data is stored (in particlar, the Engine class which
    * has references to all databases).
    */
  class SbtKanelaClassLoader(name: String, urls: Array[URL], parent: ClassLoader, loadH2Driver: Boolean = false)
    extends KanelaOnSystemClassLoader(urls, parent) {

    override def toString =
      name + "{" + getURLs.map(_.toString).mkString(", ") + "}"

    override protected def loadClass(name: String, resolve: Boolean): Class[_] = {
      if(loadH2Driver && (name.equals("org.h2.Driver") || name.startsWith("org.h2.jdbc"))) {
        var loadedClass = findLoadedClass(name)
        if (loadedClass == null)
          loadedClass = findClass(name)

        if(resolve)
          resolveClass(loadedClass)

        loadedClass

      } else super.loadClass(name, resolve)
    }
  }
}
