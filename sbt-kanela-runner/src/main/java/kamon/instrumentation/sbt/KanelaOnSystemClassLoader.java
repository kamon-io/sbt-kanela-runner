package kamon.instrumentation.sbt;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * ClassLoader that tries to resolve all classes via the provided URLs, but delegates all Kanela-related loading to the
 * System ClassLoader. We use this ClassLoader because once the Kanela agent has been attached to the JVM, all of its
 * classes will be present on the System ClassLoader only, but all instrumentation implementations will need to see some
 * of those classes (e.g. all inherit InstrumentationBuilder from Kanela), using this ClassLoader ensures visibility.
 *
 * This class is an almost verbatim copy of our "ChildFirstURLClassLoader" on Kanela, with just a few minor changes to
 * fit the purpose.
 */
public class KanelaOnSystemClassLoader extends URLClassLoader {

  static {
    registerAsParallelCapable();
  }

  private ClassLoader _systemClassLoader;

  public KanelaOnSystemClassLoader(URL[] classpath, ClassLoader parent) {
    super(classpath, parent);
    _systemClassLoader = getSystemClassLoader();
  }

  @Override
  protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    if(name.startsWith("kanela")) {
      return _systemClassLoader.loadClass(name);
    } else {
      return super.loadClass(name, resolve);
    }
  }

  @Override
  public URL getResource(String name) {
    if(name.startsWith("kanela")) {
      return _systemClassLoader.getResource(name);
    } else {
      return super.getResource(name);
    }
  }

}