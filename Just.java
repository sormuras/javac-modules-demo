// unnamed package

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.module.ModuleFinder;
import java.lang.reflect.Method;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

interface Just {
  static void help() {
    Stream.of(Just.class.getMethods()).map(Method::getName).sorted().forEach(System.out::println);
  }

  static void build() {
    compile();
    pack();
  }

  static void rebuild() {
    clean();
    build();
  }

  static void clean() {
    delete(Path.of("build"));
  }

  static void compile() {
    call(
        "javac",
        "-d",
        "build/javac",
        "--module-source-path=" + "./*/src".replace('/', File.separatorChar),
        "--module=example.mod,other.mod");
  }

  static void pack() {
    call(
        "jar",
        "--create",
        "--file=build/jar/example.mod.jar",
        "--main-class=example.mod.A",
        "-C",
        "build/javac/example.mod",
        ".",
        "-C",
        "example.mod/res",
        ".");
    call("jar", "--create", "--file=build/jar/other.mod.jar", "-C", "build/javac/other.mod", ".");
  }

  static void run() {
    if (ModuleFinder.of(Path.of("build/jar")).find("example.mod").isEmpty()) {
      build();
    }
    execute("java", "--module-path=build/jar", "--module=example.mod");
  }

  static void main(String... args) {
    if (args.length == 0) {
      build();
      return;
    }
    if (args.length == 1) {
      switch (args[0]) {
        case "help" -> help();
        case "build" -> build();
        case "rebuild" -> rebuild();
        case "compile" -> compile();
        case "pack" -> pack();
        case "run" -> run();
      }
    }
  }

  private static void call(String tool, String... args) {
    var provider = ToolProvider.findFirst(tool).orElseThrow();
    var status = provider.run(System.out, System.err, args);
    if (status == 0) return;
    throw new RuntimeException(tool + " failed with error code: " + status);
  }

  private static void delete(Path file) {
    if (!Files.exists(file)) return;
    try {
      try {
        Files.deleteIfExists(file); // delete a regular file or an empty directory
      } catch (DirectoryNotEmptyException exception) {
        try (var stream = Files.walk(file)) {
          var paths = stream.sorted(Comparator.reverseOrder()).toList();
          for (var path : paths) {
            Files.deleteIfExists(path);
          }
        }
      }
    } catch (Exception exception) {
      throw exception instanceof RuntimeException re ? re : new RuntimeException(exception);
    }
  }

  private static void execute(String program, String... args) {
    record LinePrinter(InputStream stream, Consumer<String> writer) implements Runnable {
      @Override
      public void run() {
        new BufferedReader(new InputStreamReader(stream)).lines().forEach(writer);
      }
    }
    var builder = new ProcessBuilder(program);
    builder.command().addAll(List.of(args));
    try {
      var process = builder.start();
      Thread.ofVirtual().start(new LinePrinter(process.getInputStream(), System.out::println));
      Thread.ofVirtual().start(new LinePrinter(process.getErrorStream(), System.err::println));
      var status = process.waitFor();
      if (status == 0) return;
      throw new RuntimeException(program + " failed with error code: " + status);
    } catch (Exception exception) {
      throw exception instanceof RuntimeException re ? re : new RuntimeException(exception);
    }
  }
}
