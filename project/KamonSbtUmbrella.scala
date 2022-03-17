import java.util.Calendar
import sbt.{Def, _}
import Keys._
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport._

import de.heikoseeberger.sbtheader.{HeaderPlugin, License}
import sbt.plugins.JvmPlugin
import sbtdynver.DynVerPlugin.autoImport.dynver

object KamonSbtUmbrella extends AutoPlugin {

  object autoImport {
    val kanelaAgentVersion = settingKey[String]("Kanela Agent version")
    val kanelaAgentJar = taskKey[File]("Kanela Agent jar")

    def compileScope(deps: ModuleID*): Seq[ModuleID]  = deps map (_ % "compile")
    def testScope(deps: ModuleID*): Seq[ModuleID]     = deps map (_ % "test")
    def providedScope(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "provided")
    def optionalScope(deps: ModuleID*): Seq[ModuleID] = deps map (_ % "compile,optional")

    val noPublishing = Seq(
      skip in publish := true,
      publishLocal := {},
      publishArtifact := false
    )
  }

  override def requires: Plugins      = JvmPlugin && HeaderPlugin
  override def trigger: PluginTrigger = allRequirements

  import autoImport._
  override def projectSettings: Seq[_root_.sbt.Def.Setting[_]] = Seq(
    // We are replacing the "date-time" part of the version generated by sbt-dynver so that we
    // get the same version value, even when re-applying settings during cross actions that
    // might take longer than a minute to complete.
    version ~= (_.replaceAll("[+]\\d{8}[-]\\d{4}[-]SNAPSHOT", "-dirty")),
    dynver ~= (_.replaceAll("[+]\\d{8}[-]\\d{4}[-]SNAPSHOT", "-dirty")),
    organization := "io.kamon",
    organizationName := "The Kamon Project",
    organizationHomepage := Some(url("https://kamon.io/")),
    startYear := Some(2013),
    headerLicense := licenseTemplate(startYear.value),
    licenses += (("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))),
    scalacOptions := Seq(
      "-encoding",
      "utf8",
      "-g:vars",
      "-feature",
      "-unchecked",
      "-deprecation",
      "-language:postfixOps",
      "-language:implicitConversions",
      "-Xlog-reflective-calls",
      "-Ywarn-dead-code",
      "-target:jvm-1.8"
    ),
    javacOptions := Seq(
      "-Xlint:-options", "-source", "1.8", "-target", "1.8"
    ),
    crossPaths := true,
    pomIncludeRepository := { _ => false },
    publishArtifact in Test := false,
    publishMavenStyle := true,
    pomExtra := defaultPomExtra(name.value),
    kanelaAgentVersion := "1.0.10",
    kanelaAgentJar := findKanelaAgentJar.value
  )

  private def licenseTemplate(startYear: Option[Int]) = {
    val fromYear = startYear.getOrElse(2013)
    val thisYear = Calendar.getInstance().get(Calendar.YEAR)

    Some(License.Custom(
      s"""
         | ==========================================================================================
         | Copyright © $fromYear-$thisYear The Kamon Project <https://kamon.io/>
         |
         | Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
         | except in compliance with the License. You may obtain a copy of the License at
         |
         |     http://www.apache.org/licenses/LICENSE-2.0
         |
         | Unless required by applicable law or agreed to in writing, software distributed under the
         | License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
         | either express or implied. See the License for the specific language governing permissions
         | and limitations under the License.
         | ==========================================================================================
      """.trim().stripMargin
    ))
  }

  def findKanelaAgentJar = Def.task {
    update.value.matching {
      moduleFilter(organization = "io.kamon", name = "kanela-agent") &&
        artifactFilter(`type` = "jar")
    }.head
  }

  private def defaultPomExtra(projectName: String) = {
    <url>http://kamon.io</url>
      <developers>
        <developer><id>ivantopo</id><name>Ivan Topolnjak</name><url>https://twitter.com/ivantopo</url></developer>
        <developer><id>dpsoft</id><name>Diego Parra</name><url>https://twitter.com/diegolparra</url></developer>
      </developers>
  }
}
