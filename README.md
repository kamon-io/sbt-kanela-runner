sbt-kanela-runner
=========
[![Build Status](https://travis-ci.org/kamon-io/sbt-kanela-runner.png)](https://travis-ci.org/kamon-io/sbt-kanela-runner)
[![Download](https://api.bintray.com/packages/kamon-io/sbt-plugins/sbt-kanela-runner/images/download.svg)](https://bintray.com/kamon-io/sbt-plugins/sbt-kanela-runner/_latestVersion)


This project contains two [sbt] plugins that automatically configure your build to perform enable the Kanela
instrumentation agent when running your application from within SBT, both for regular applications and Play Framework
projects [in development mode.

SBT versions 0.13.x and 1.x are supported.

## Why this plugin?

First and foremost, simplicity. Although adding the Kanela agent is just about adding the `-javaagent` option to the JVM,
doing so can be challenging when running from SBT. These plugins take care of the corner cases and ensure that hitting
`run` will just work, regardless your project type or whether you are forking the JVM or not.



## Regular Projects (non-Play)

### Configuring

Add the `sbt-kanela-runner` plugin to your `project/plugins.sbt` file using the code bellow:

```scala
addSbtPlugin("io.kamon" % "sbt-kanela-runner" % "2.0.2")
```

### Running

Just `run`, like you do all the time!

Here is what the plugin will do depending on your `fork` settings:
* **fork in run := true**: The forked process will run with the `-javaagent:<jarpath>` and that's all.
* **fork in run := false**: The plugin will try to attach the Kanela agent to the current process, targetting the
ClassLoader where your application is placed.


## Play Projects

### Configuring

For Play Framework 2.6 and 2.7 projects add the `sbt-kanela-runner-play-2.x` to your `project/plugins.sbt` file:

```scala
// For Play Framework 2.6
addSbtPlugin("io.kamon" % "sbt-kanela-runner-play-2.6" % "2.0.2")

// For Play Framework 2.7
addSbtPlugin("io.kamon" % "sbt-kanela-runner-play-2.7" % "2.0.2")
```

Then, you will need to enable the `JavaAgent` plugin on your Play project. Find your play project on your `build.sbt`
file and call `.enablePlugins(JavaAgent)` on it, it will probably look like this:

```scala
lazy val root = (project in file(".")).enablePlugins(PlayScala, JavaAgent)
```

This plugin has been tested with **Play 2.7.3** and **Play 2.6.23**.

### Running

Just `run`, like you do all the time! A notice will be shown saying that you are running your application with Kanela.



[sbt]: https://github.com/sbt/sbt
[play]: https://www.playframework.com
