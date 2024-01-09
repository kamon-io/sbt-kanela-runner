/*
 * =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
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

import sbt.Keys._
import sbt._

import java.net.URL

inThisBuild(List(
  sbtPlugin := true,
  organization := "io.kamon",
  scmInfo := Some(ScmInfo(
    new URL("https://github.com/kamon-io/sbt-kanela-runner"),
    "scm:git:https://github.com/kamon-io/sbt-kanela-runner.git"
  ))
))

def crossSbtDependency(module: ModuleID, sbtVersion: String, scalaVersion: String): ModuleID = {
  Defaults.sbtPluginExtra(module, sbtVersion, scalaVersion)
}

val playSbtPluginFor29 = "com.typesafe.play" % "sbt-plugin" % "2.9.1"
val playSbtPluginFor30 = "org.playframework" % "sbt-plugin" % "3.0.1"
val sbtJavaAgentPlugin = "com.github.sbt" % "sbt-javaagent" % "0.1.8"

lazy val sbtKanelaRunner = Project("sbt-kanela-runner", file("."))
  .settings(
    noPublishing: _*
  ).aggregate(kanelaRunner, kanelaRunnerPlay29, kanelaRunnerPlay30)

lazy val kanelaRunner = Project("kanela-runner", file("sbt-kanela-runner"))
  .settings(
    moduleName := "sbt-kanela-runner",
    libraryDependencies += "net.bytebuddy" % "byte-buddy-agent" % "1.14.2"
  )


lazy val kanelaRunnerPlay29 = Project("kanela-runner-play-29", file("sbt-kanela-runner-play-2.9"))
  .dependsOn(kanelaRunner)
  .settings(
    name := "sbt-kanela-runner-play-2.9",
    moduleName := "sbt-kanela-runner-play-2.9",
    libraryDependencies += crossSbtDependency(playSbtPluginFor29, (pluginCrossBuild / sbtBinaryVersion).value, scalaBinaryVersion.value),
    libraryDependencies += crossSbtDependency(sbtJavaAgentPlugin, (pluginCrossBuild / sbtBinaryVersion).value, scalaBinaryVersion.value)
  )

lazy val kanelaRunnerPlay30 = Project("kanela-runner-play-30", file("sbt-kanela-runner-play-3.0"))
  .dependsOn(kanelaRunner)
  .settings(
    name := "sbt-kanela-runner-play-3.0",
    moduleName := "sbt-kanela-runner-play-3.0",
    libraryDependencies += crossSbtDependency(playSbtPluginFor30, (pluginCrossBuild / sbtBinaryVersion).value, scalaBinaryVersion.value),
    libraryDependencies += crossSbtDependency(sbtJavaAgentPlugin, (pluginCrossBuild / sbtBinaryVersion).value, scalaBinaryVersion.value)
  )
