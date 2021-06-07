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

inThisBuild(List(
  sbtPlugin := true,
  organization := "io.kamon",
))

def crossSbtDependency(module: ModuleID, sbtVersion: String, scalaVersion: String): ModuleID = {
  Defaults.sbtPluginExtra(module, sbtVersion, scalaVersion)
}

val playSbtPluginFor26 = "com.typesafe.play" % "sbt-plugin" % "2.6.25"
val playSbtPluginFor27 = "com.typesafe.play" % "sbt-plugin" % "2.7.9"
val playSbtPluginFor28 = "com.typesafe.play" % "sbt-plugin" % "2.8.8"
val lagomSbtPluginFor16 = "com.lightbend.lagom" % "lagom-sbt-plugin" % "1.6.4"


lazy val sbtKanelaRunner = Project("sbt-kanela-runner", file("."))
  .settings(
    noPublishing: _*
  ).aggregate(kanelaRunner, kanelaRunnerPlay26, kanelaRunnerPlay27, kanelaRunnerPlay28, kanelaRunnerLagom16)

lazy val kanelaRunner = Project("kanela-runner", file("sbt-kanela-runner"))
  .settings(
    moduleName := "sbt-kanela-runner",
    bintrayPackage := "sbt-kanela-runner",
    libraryDependencies += "net.bytebuddy" % "byte-buddy-agent" % "1.9.12"
  )

lazy val kanelaRunnerPlay26 = Project("kanela-runner-play-26", file("sbt-kanela-runner-play-2.6"))
  .dependsOn(kanelaRunner)
  .settings(
    name := "sbt-kanela-runner-play-2.6",
    moduleName := "sbt-kanela-runner-play-2.6",
    bintrayPackage := "sbt-kanela-runner-play-2.6",
    libraryDependencies ++= Seq(
      crossSbtDependency(playSbtPluginFor26, (sbtBinaryVersion in pluginCrossBuild).value, scalaBinaryVersion.value)
    )
  )

lazy val kanelaRunnerPlay27 = Project("kanela-runner-play-27", file("sbt-kanela-runner-play-2.7"))
  .dependsOn(kanelaRunner)
  .settings(
    name := "sbt-kanela-runner-play-2.7",
    moduleName := "sbt-kanela-runner-play-2.7",
    bintrayPackage := "sbt-kanela-runner-play-2.7",
    libraryDependencies ++= Seq(
      crossSbtDependency(playSbtPluginFor27, (sbtBinaryVersion in pluginCrossBuild).value, scalaBinaryVersion.value)
    )
  )

lazy val kanelaRunnerPlay28 = Project("kanela-runner-play-28", file("sbt-kanela-runner-play-2.8"))
  .dependsOn(kanelaRunner)
  .settings(
    name := "sbt-kanela-runner-play-2.8",
    moduleName := "sbt-kanela-runner-play-2.8",
    bintrayPackage := "sbt-kanela-runner-play-2.8",
    libraryDependencies ++= Seq(
      crossSbtDependency(playSbtPluginFor28, (sbtBinaryVersion in pluginCrossBuild).value, scalaBinaryVersion.value)
    )
  )

lazy val kanelaRunnerLagom16 = Project("kanela-runner-lagom-16", file("sbt-kanela-runner-lagom-1.6"))
  .dependsOn(kanelaRunner)
  .settings(
    name := "sbt-kanela-runner-lagom-1.6",
    moduleName := "sbt-kanela-runner-lagom-1.6",
    bintrayPackage := "sbt-kanela-runner-lagom-1.6",
    libraryDependencies ++= Seq(
      crossSbtDependency(lagomSbtPluginFor16, (sbtBinaryVersion in pluginCrossBuild).value, scalaBinaryVersion.value)
    )
  )

// remove this?
//crossSbtVersions := Seq("1.3.8")
