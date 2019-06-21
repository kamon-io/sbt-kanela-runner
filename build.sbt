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

import sbt._
import sbt.Keys._

def crossSbtDependency(module: ModuleID, sbtVersion: String, scalaVersion: String): ModuleID = {
  Defaults.sbtPluginExtra(module, sbtVersion, scalaVersion)
}

val playSbtPluginFor26 = "com.typesafe.play" % "sbt-plugin" % "2.6.11"
val playSbtPluginFor27 = "com.typesafe.play" % "sbt-plugin" % "2.7.0"


lazy val sbtKanelaRunner = Project("sbt-kanela-runner", file("."))
  .settings(noPublishing: _*)
  .aggregate(kanelaRunner, kanelaRunnerPlay26, kanelaRunnerPlay27)

lazy val kanelaRunner = Project("kanela-runner", file("sbt-kanela-runner"))
  .settings(
    sbtPlugin := true,
    moduleName := "sbt-kanela-runner",
    libraryDependencies += "net.bytebuddy" % "byte-buddy-agent" % "1.9.12"
  )

lazy val kanelaRunnerPlay26 = Project("kanela-runner-play-26", file("sbt-aspectj-runner-play-2.6"))
  .dependsOn(kanelaRunner)
  .settings(
    sbtPlugin := true,
    name := "sbt-kanela-runner-play-2.6",
    moduleName := "sbt-kanela-runner-play-2.6",
    libraryDependencies ++= Seq(
      crossSbtDependency(playSbtPluginFor26, (sbtBinaryVersion in pluginCrossBuild).value, scalaBinaryVersion.value)
    )
  )

lazy val kanelaRunnerPlay27 = Project("kanela-runner-play-27", file("sbt-aspectj-runner-play-2.7"))
  .dependsOn(kanelaRunner)
  .settings(
    sbtPlugin := true,
    name := "sbt-kanela-runner-play-2.7",
    moduleName := "sbt-kanela-runner-play-2.7",
    libraryDependencies ++= Seq(
      crossSbtDependency(playSbtPluginFor27, (sbtBinaryVersion in pluginCrossBuild).value, scalaBinaryVersion.value)
    )
  )

crossSbtVersions := Seq("0.13.18", "1.2.8")
//workaround for https://github.com/sbt/sbt/issues/3749
scalaVersion in ThisBuild := {
  if((sbtBinaryVersion in pluginCrossBuild).value.startsWith("0.")) "2.10.7" else "2.12.8"
}
