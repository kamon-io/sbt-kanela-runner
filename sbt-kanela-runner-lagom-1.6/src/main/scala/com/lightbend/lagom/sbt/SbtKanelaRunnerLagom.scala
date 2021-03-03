package com.lightbend.lagom.sbt

import com.lightbend.lagom.dev.{MiniLogger, StaticServiceLocations}
import com.lightbend.lagom.dev.Reloader.DevServer
import com.lightbend.lagom.dev.Servers.ServerContainer
import com.lightbend.lagom.sbt.LagomPlugin.autoImport.{lagomCassandraPort, lagomKafkaAddress, lagomRun, lagomServiceGatewayAddress, lagomServiceGatewayImpl, lagomServiceGatewayPort, lagomServiceLocatorAddress, lagomServiceLocatorPort, lagomServiceLocatorStart, lagomServiceLocatorStop, lagomUnmanagedServices}
import com.lightbend.lagom.sbt.run.KanelaRunSupport
import com.lightbend.sbt.javaagent.JavaAgent
import com.lightbend.sbt.javaagent.JavaAgent.JavaAgentKeys.javaAgents
import kamon.instrumentation.sbt.{KanelaOnSystemClassLoader, SbtKanelaRunner}
import kamon.instrumentation.sbt.SbtKanelaRunner.Keys.kanelaVersion
import sbt.Def.Initialize
import sbt.Keys.{managedClasspath, name, state}
import sbt.{Def, inScope, _}

import java.io.Closeable
import java.net.{URI, URL, URLClassLoader}
import java.util.{Map => JMap}
import scala.collection.JavaConverters._

object SbtKanelaRunnerLagom extends AutoPlugin {

  //  override def trigger = AllRequirements
  override def requires = Lagom && SbtKanelaRunner && JavaAgent

  override def projectSettings: Seq[Setting[_]] = Seq(
    javaAgents += "io.kamon" % "kanela-agent" % kanelaVersion.value,
    lagomRun := {
      val service = runLagomTask.value
      // eagerly loads the service
      service.reload()
      // install a listener that will take care of reloading on classpath's changes
      service.addChangeListener(() => service.reload())
      (name.value, service)
    },
  )

  override def buildSettings: Seq[Def.Setting[_]] = inScope(ThisScope in LagomPlugin.extraProjects.head)(Seq(
    lagomServiceLocatorStart in ThisBuild := startServiceLocatorTask.value,
    lagomServiceLocatorStop in ThisBuild := ServiceLocator.tryStop(new SbtLoggerProxy(state.value.log))
  ))

  private lazy val runLagomTask: Initialize[Task[DevServer]] = Def.taskDyn {
    KanelaRunSupport.reloadRunTask(LagomPlugin.managedSettings.value)
  }

  private lazy val startServiceLocatorTask = Def.task {
    val unmanagedServices: Map[String, String] =
      StaticServiceLocations.staticServiceLocations(lagomCassandraPort.value, lagomKafkaAddress.value) ++ lagomUnmanagedServices.value

    val serviceLocatorAddress  = lagomServiceLocatorAddress.value
    val serviceLocatorPort     = lagomServiceLocatorPort.value
    val serviceGatewayAddress  = lagomServiceGatewayAddress.value
    val serviceGatewayHttpPort = lagomServiceGatewayPort.value
    val serviceGatewayImpl     = lagomServiceGatewayImpl.value
    val classpathUrls          = (managedClasspath in Compile).value.files.map(_.toURI.toURL).toArray
    val scalaInstance          = Keys.scalaInstance.value
    val log                    = new SbtLoggerProxy(state.value.log)

    ServiceLocator.start(
      log,
      scalaInstance.loader,
      classpathUrls,
      serviceLocatorAddress,
      serviceLocatorPort,
      serviceGatewayAddress,
      serviceGatewayHttpPort,
      unmanagedServices,
      serviceGatewayImpl
    )
  }

  class LagomServiceLocatorClassLoader(urls: Array[URL], parent: ClassLoader) extends URLClassLoader(urls, parent)

  object ServiceLocator extends ServerContainer {
    protected type Server = Closeable {
      def start(
          serviceLocatorAddress: String,
          serviceLocatorPort: Int,
          serviceGatewayAddress: String,
          serviceGatewayHttpPort: Int,
          unmanagedServices: JMap[String, String],
          gatewayImpl: String
      ): Unit
      def serviceLocatorAddress: URI
      def serviceGatewayAddress: URI
    }

    def start(
               log: MiniLogger,
               parentClassLoader: ClassLoader,
               classpath: Array[URL],
               serviceLocatorAddress: String,
               serviceLocatorPort: Int,
               serviceGatewayAddress: String,
               serviceGatewayHttpPort: Int,
               unmanagedServices: Map[String, String],
               gatewayImpl: String
             ): Closeable =
      synchronized {
        if (server == null) {
          withContextClassloader(new LagomServiceLocatorClassLoader(classpath, parentClassLoader)) { loader =>
            val serverClass = loader.loadClass("com.lightbend.lagom.registry.impl.ServiceLocatorServer")
            server = serverClass.getDeclaredConstructor().newInstance().asInstanceOf[Server]
            try {
              server.start(
                serviceLocatorAddress,
                serviceLocatorPort,
                serviceGatewayAddress,
                serviceGatewayHttpPort,
                unmanagedServices.asJava,
                gatewayImpl
              )
            } catch {
              case e: Exception =>
                val msg = "Failed to start embedded Service Locator or Service Gateway. " +
                  s"Hint: Are ports $serviceLocatorPort or $serviceGatewayHttpPort already in use?"
                stop()
                throw new RuntimeException(msg, e)
            }
          }
        }
        if (server != null) {
          log.info("Service locator is running at " + server.serviceLocatorAddress)
          // TODO: trace all valid locations for the service gateway.
          log.info("Service gateway is running at " + server.serviceGatewayAddress)
        }

        new Closeable {
          override def close(): Unit = stop(log)
        }
      }

    private def withContextClassloader[T](loader: ClassLoader)(body: ClassLoader => T): T = {
      val current = Thread.currentThread().getContextClassLoader
      try {
        Thread.currentThread().setContextClassLoader(loader)
        body(loader)
      } finally Thread.currentThread().setContextClassLoader(current)
    }

    protected def stop(log: MiniLogger): Unit =
      synchronized {
        if (server == null) {
          log.info("Service locator was already stopped")
        } else {
          log.info("Stopping service locator")
          stop()
        }
      }

    private def stop(): Unit =
      synchronized {
        try server.close()
        catch { case _: Exception => () }
        finally server = null
      }
  }
}
