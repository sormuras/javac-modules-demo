// unnamed package

import java.lang.reflect.Method;
import java.util.stream.Stream;

interface Just {
  static void help() {
    Stream.of(Just.class.getMethods()).map(Method::getName).sorted().forEach(System.out::println);
  }

  static void clean() {
    System.out.println("TODO rm -rf build");
  }

  static void compile() {
    clean();
    javac(
        "-verbose",
        "-d", "build/javac",
        "--module-source-path=./*/src",
        "--module=example.mod,other.mod");
  }
}
