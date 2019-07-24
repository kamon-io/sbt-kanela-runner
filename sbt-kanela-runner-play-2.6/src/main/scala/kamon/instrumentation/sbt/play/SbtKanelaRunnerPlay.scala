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

  class NamedKanelaAwareClassLoader(name: String, urls: Array[URL], parent: ClassLoader) extends KanelaOnSystemClassLoader(urls, parent) {
    override def toString = name + "{" + getURLs.map(_.toString).mkString(", ") + "}"
  }
}
