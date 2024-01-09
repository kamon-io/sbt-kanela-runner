sbt-kanela-runner
=========

This project contains [sbt] plugins that automatically configures your build to enable the Kanela instrumentation agent 
when running your application from within SBT. It works with regular applications with a `main` method, Play Framework, 
and Lagom projects in development mode.

Starting on version 2.1.x this plugin requires SBT 1.9.0+. See details below for compatibility with older SBT and 
Play/Lagom versions.


## Why this plugin?

First and foremost, simplicity. Although adding the Kanela agent is just about adding the `-javaagent` option to the JVM,
doing so can be challenging when running from SBT. These plugins take care of the corner cases and ensure that hitting
`run` will just work, regardless your project type or whether you are forking the JVM or not.


## Regular Projects (non-Play)

### Configuring

Add the `sbt-kanela-runner` plugin to your `project/plugins.sbt` file using the code bellow:

```scala
addSbtPlugin("io.kamon" % "sbt-kanela-runner" % "2.1.0")
```

#### Important
This plugin will only work properly on SBT 1.3+ if the `classLoaderLayeringStrategy` setting is set to `Flat` or you set
`fork in run := true`. 

### Running

Just `run`, like you do all the time!

Here is what the plugin will do depending on your `fork` settings:
* **fork in run := true**: The forked process will run with the `-javaagent:<jarpath>` and that's all.
* **fork in run := false**: The plugin will try to attach the Kanela agent to the current process, targeting the
ClassLoader where your application is placed.


## Play Projects

### Installing the Plugin 

For Play Framework 2.9 and 3.0, add the appropriate `sbt-kanela-runner-play-x.x` to your `project/plugins.sbt` file:

```scala
// For Play Framework 2.9
addSbtPlugin("io.kamon" % "sbt-kanela-runner-play-2.9" % "2.1.0")

// For Play Framework 3.0
addSbtPlugin("io.kamon" % "sbt-kanela-runner-play-3.0" % "2.1.0")
```

For older Play Framework versions:

```scala
// For Play Framework 2.6
addSbtPlugin("io.kamon" % "sbt-kanela-runner-play-2.6" % "2.0.14")

// For Play Framework 2.7
addSbtPlugin("io.kamon" % "sbt-kanela-runner-play-2.7" % "2.0.14")

// For Play Framework <=2.8.7
addSbtPlugin("io.kamon" % "sbt-kanela-runner-play-2.8" % "2.0.10")

// For Play Framework 2.8.8
addSbtPlugin("io.kamon" % "sbt-kanela-runner-play-2.8" % "2.0.14")
```

Then, you will need to enable the `JavaAgent` plugin on your Play project. Find your play project on your `build.sbt`
file and call `.enablePlugins(JavaAgent)` on it, it will probably look like this:

```scala
lazy val root = (project in file(".")).enablePlugins(PlayScala, JavaAgent)
```

This plugin has been tested with **Play Framework 2.9.1**, **3.0.1**, **2.8.8**, **2.7.3**, and **2.6.23**.

### Running

Just `run`, like you do all the time! A notice will be shown saying that you are running your application with Kanela.


## Lagom Projects

### Installing the Plugin

For Lagom Framework 1.6 add the `sbt-kanela-runner-lagom-1.6` plugin to your `project/plugins.sbt` file:

```scala
// For Lagom Framework Framework <=1.6.4
addSbtPlugin("io.kamon" % "sbt-kanela-runner-lagom-1.6" % "2.0.13")

// For Lagom Framework Framework >1.6.5
addSbtPlugin("io.kamon" % "sbt-kanela-runner-lagom-1.6" % "2.0.14")
```

Then you will need to explicitly enable the `JavaAgent` and `SbtKanelaRunnerLagom` plugins in one (and only one) of your 
Lagom implementation projects in the `build.sbt` file. Beware that Kanela can only instrument one service at a time:

```scala
lazy val `hello-impl` = (project in file("hello-impl"))
  .enablePlugins(LagomScala, JavaAgent, SbtKanelaRunnerLagom)
```

### Running
Execute `runAll` as you normally would.

[sbt]: https://github.com/sbt/sbt
[play]: https://www.playframework.com
