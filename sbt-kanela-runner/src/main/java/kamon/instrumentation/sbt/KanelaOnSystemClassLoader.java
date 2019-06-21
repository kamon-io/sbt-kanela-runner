package kamon.instrumentation.sbt;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

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


//    // First, check if the class has already been loaded
//    Class<?> c = findLoadedClass(name);
//    if (c == null) {
//      if (_systemClassLoader != null && name.startsWith("kanela")) {
//        try {
//          // checking _systemClassLoader: jvm classes, endorsed, cmd classpath, etc.
//          c = _systemClassLoader.loadClass(name);
//        }catch (ClassNotFoundException ignored) {}
//      }
//      if (c == null) {
//        try {
//          // checking local
//          c = findClass(name);
//        } catch (ClassNotFoundException e) {
//          // checking parent
//          // This call to loadClass may eventually call findClass again, in case the parent doesn't find anything.
//          c = super.loadClass(name, resolve);
//        }
//      }
//    }
//    if (resolve) {
//      resolveClass(c);
//    }
//    return c;
  }

  @Override
  public URL getResource(String name) {
    if(name.startsWith("kanela")) {
      return _systemClassLoader.getResource(name);
    } else {
      return super.getResource(name);
    }


//    URL url = null;
//
//    if (_systemClassLoader != null && name.startsWith("kanela")) {
//      url = _systemClassLoader.getResource(name);
//    }
//
//    if (url == null) {
//      url = findResource(name);
//      if (url == null) {
//        // This call to getResource may eventually call findResource again, in case the parent doesn't find anything.
//        url = super.getResource(name);
//      }
//    }
//    return url;
  }

}