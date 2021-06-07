package play.sbt.run

import kamon.instrumentation.sbt.SbtKanelaRunner
import kamon.instrumentation.sbt.play.KanelaReloader
import play.core.BuildLink
import play.dev.filewatch.{SourceModificationWatch => PlaySourceModificationWatch, WatchState => PlayWatchState}
import play.sbt.PlayImport.PlayKeys._
import play.sbt.PlayInternalKeys._
import play.sbt.run.PlayRun.{generatedSourceHandlers, getPollInterval, getSourcesFinder, sleepForPoolDelay}
import play.sbt.{Colors, PlayNonBlockingInteractionMode}
import sbt.Keys._
import sbt.{AttributeKey, Compile, Def, InputTask, Keys, Project, State, TaskKey, Watched}

import scala.annotation.tailrec

object KanelaPlayRun extends PlayRunCompat {

  // This file was copied and modified from the URL bellow since there was no other sensible way to our
  // current knowledge of changing the ClassLoaders.
  //
  // https://raw.githubusercontent.com/playframework/playframework/2.7.3/dev-mode/sbt-plugin/src/main/scala/play/sbt/run/PlayRun.scala

  val playWithKanelaRunTask = playRunTask(playRunHooks, playDependencyClasspath,
    playReloaderClasspath, playAssetsClassLoader)

  def playRunTask(
    runHooks: TaskKey[Seq[play.sbt.PlayRunHook]],
    dependencyClasspath: TaskKey[Classpath],
    reloaderClasspath: TaskKey[Classpath],
    assetsClassLoader: TaskKey[ClassLoader => ClassLoader]
  ): Def.Initialize[InputTask[Unit]] = Def.inputTask {

    val args = Def.spaceDelimited().parsed

    val state = Keys.state.value
    val scope = resolvedScoped.value.scope
    val interaction = playInteractionMode.value

    val reloadCompile = () => PlayReload.compile(
      () => Project.runTask(playReload in scope, state).map(_._2).get,
      () => Project.runTask(reloaderClasspath in scope, state).map(_._2).get,
      () => Project.runTask(streamsManager in scope, state).map(_._2).get.toEither.right.toOption,
      state, 
      scope
    )

    lazy val devModeServer = KanelaReloader.startDevMode(
      runHooks.value,
      (javaOptions in sbt.Runtime).value,
      playCommonClassloader.value,
      dependencyClasspath.value.files,
      reloadCompile,
      assetsClassLoader.value,
      // avoid monitoring same folder twice or folders that don't exist
      playMonitoredFiles.value.distinct.filter(_.exists()),
      fileWatchService.value,
      generatedSourceHandlers,
      playDefaultPort.value,
      playDefaultAddress.value,
      baseDirectory.value,
      devSettings.value,
      args,
      (mainClass in (Compile, Keys.run)).value.get,
      KanelaPlayRun,
      SbtKanelaRunner.Keys.kanelaAgentJar.value
    )

    interaction match {
      case nonBlocking: PlayNonBlockingInteractionMode =>
        nonBlocking.start(devModeServer)
      case blocking =>
        devModeServer

        println()
        println(Colors.green("(Server started, use Enter to stop and go back to the console...)"))
        println()

        val maybeContinuous: Option[Watched] = watchContinuously(state, Keys.sbtVersion.value)

        maybeContinuous match {
          case Some(watched) =>
            // ~ run mode
            interaction.doWithoutEcho {
              twiddleRunMonitor(watched, state, devModeServer.buildLink, Some(PlayWatchState.empty))
            }
          case None =>
            // run mode
            interaction.waitForCancel()
        }

        devModeServer.close()
        println()
    }
  }

  /**
    * Monitor changes in ~run mode.
    */
  @tailrec
  private def twiddleRunMonitor(watched: Watched, state: State, reloader: BuildLink, ws: Option[PlayWatchState] = None): Unit = {
    val ContinuousState = AttributeKey[PlayWatchState]("watch state", "Internal: tracks state for continuous execution.")
    def isEOF(c: Int): Boolean = c == 4

    @tailrec def shouldTerminate: Boolean = (System.in.available > 0) && (isEOF(System.in.read()) || shouldTerminate)

    val sourcesFinder: PlaySourceModificationWatch.PathFinder = getSourcesFinder(watched, state)
    val watchState                                            = ws.getOrElse(state.get(ContinuousState).getOrElse(PlayWatchState.empty))

    val (triggered, newWatchState, newState) =
      try {
        val (triggered: Boolean, newWatchState: PlayWatchState) = PlaySourceModificationWatch.watch(sourcesFinder, getPollInterval(watched), watchState)(shouldTerminate)
        (triggered, newWatchState, state)
      } catch {
        case e: Exception =>
          val log = state.log
          log.error("Error occurred obtaining files to watch.  Terminating continuous execution...")
          log.trace(e)
          (false, watchState, state.fail)
      }

    if (triggered) {
      //Then launch compile
      Project.synchronized {
        val start = System.currentTimeMillis
        Project.runTask(compile in Compile, newState).get._2.toEither.right.map { _ =>
          val duration = System.currentTimeMillis - start
          val formatted = duration match {
            case ms if ms < 1000 => ms + "ms"
            case seconds => (seconds / 1000) + "s"
          }
          println("[" + Colors.green("success") + "] Compiled in " + formatted)
        }
      }

      // Avoid launching too much compilation
      sleepForPoolDelay

      // Call back myself
      twiddleRunMonitor(watched, newState, reloader, Some(newWatchState))
    } else {
      ()
    }
  }

}
