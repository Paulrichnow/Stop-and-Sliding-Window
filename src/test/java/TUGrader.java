package test.java;

/**
 * DO NOT DISTRIBUTE.
 *
 * This code is intended to support the education of students associated
 * with the Tandy School of Computer Science at the University of Tulsa.
 * It is not intended for distribution and should remain within private
 * repositories that belong to Tandy faculty, students, and/or alumni.
 */
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

/**
 * An auto-grader for assignments delivered by the Tandy
 * School of Computer Science at TU.
 */
public class TUGrader {

  // **************************************************
  // Main and version
  // **************************************************

  /** The current version of TUGrader. */
  public static final String VERSION = "2023.08.30";

  /**
   * Executes TUGrader with the input clargs and exits
   * with status code {@code 0} if all tests are successful
   * and status code {@code 1} if any test fails.
   *
   * @param args  the command-line arguments
   */
  public static void main(String[] args) {
    System.exit((new TUGrader().run(args).wasSuccessful()) ? 0 : 1);
  }

  // **************************************************
  // Static fields
  // **************************************************

  private static final InputStream STDIN = System.in;
  private static final PrintStream STDOUT = System.out;

  // **************************************************
  // Public static methods
  // **************************************************

  /**
   * Returns an array buffer that captures all output on
   * System.out.
   *
   * @return an array buffer with all output on System.out
   */
  public static ByteArrayOutputStream captureStdout() {
    ByteArrayOutputStream stdoutCapture = new ByteArrayOutputStream();
    System.setOut(new PrintStream(stdoutCapture));
    return stdoutCapture;
  }

  /**
   * Outputs the input on System.in.
   *
   * @param input  the input to output to System.in
   */
  public static void flushToStdin(String input) {
    System.setIn(new ByteArrayInputStream(input.getBytes()));
  }

  /**
   * Silences all printing and logging on System.out. This
   * needs to be combined with {@link TUGrader#resetStdIO}
   * so that test reporting can be logged to console.
   */
  public static void silenceStdout() {
    System.setOut(
            new PrintStream(
                    new OutputStream() {
                      @Override
                      public void write(int b) {
                        return;
                      }
                    }));
  }

  /**
   * Resets System.out and System.in to stdout and stdin, so
   * that printing and keyboard input can work normally.
   */
  public static void resetStdIO() {
    System.setIn(TUGrader.STDIN);
    System.setOut(TUGrader.STDOUT);
  }

  // **************************************************
  // Fields
  // **************************************************

  private JUnitCore junit;
  private TUGraderListener listener;
  private Logger logger;
  private HashMap<String, Method> testCases;
  private HashMap<String, Class<?>> tests;

  // **************************************************
  // Constructors
  // **************************************************

  /**
   * Constructs a TUGrader with a pre-defined set of tests.
   *
   * @param tests  the set of tests to use for 'all'
   */
  public TUGrader(Class<?>... tests) {
    this(null, tests);
  }

  /**
   * Constructs a TUGrader with a custom logger.
   *
   * @param logger  the custom logger to use for reporting
   * @param tests   the set of tests to use for 'all'
   */
  public TUGrader(Logger logger, Class<?>... tests) {
    this.initJUnit();
    this.initLogger(logger);
    this.initTests(tests);
  }

  // **************************************************
  // Private methods
  // **************************************************

  private String getDisplayName(Method method) {
    if (method == null) {
      throw new RuntimeException("Unrecognized test case `null`");
    }
    if (method.isAnnotationPresent(DisplayName.class)) {
      return method.getAnnotation(DisplayName.class).value();
    }
    if (method.getName().contains("_")) {
      return method.getName().replace("_", " ");
    }
    return String.format("#%s", method.getName());
  }

  private Method[] getTestCasesToArray(Collection<Method> testCases) {
    if (testCases == null || testCases.isEmpty()) {
      return new Method[0];
    }
    return testCases.toArray(Method[]::new);
  }

  private Class<?>[] getTestsToArray(Collection<Class<?>> tests) {
    if (tests == null || tests.isEmpty()) {
      return new Class<?>[0];
    }
    return tests.toArray(Class<?>[]::new);
  }

  private void initJUnit() {
    this.junit = new JUnitCore();
    this.listener = new TUGraderListener();
    this.junit.addListener(this.listener);
  }

  private void initLogger(Logger logger) {
    if (logger != null) {
      this.logger = logger;
      return;
    }
    this.logger = Logger.getLogger(TUGrader.class.getName());
    this.logger.setLevel(Level.ALL);
    this.logger.addHandler(
            new Handler() {
              @Override
              public void close() {}

              @Override
              public void flush() {}

              @Override
              public void publish(LogRecord record) {
                System.out.println(record.getMessage());
              }
            });
  }

  private void initTests(Class<?>... tests) {
    if (this.tests == null) {
      this.tests = new HashMap<>();
    }
    if (this.testCases == null) {
      this.testCases = new HashMap<>();
    }
    for (Class<?> test : tests) {
      this.tests.put(test.getSimpleName(), test);
      for (Method method : test.getDeclaredMethods()) {
        if (!method.isAnnotationPresent(Test.class)) {
          continue;
        }
        if (!this.testCases.containsKey(method.getName())) {
          this.testCases.put(method.getName(), method);
        } else {
          throw new RuntimeException(
                  String.format("Duplicate method name `%s` @ %s", method.getName(), test.getName()));
        }
      }
    }
  }

  private void log(String format, Object... args) {
    if (args.length == 0) {
      this.logger.log(this.logger.getLevel(), format);
    } else {
      this.logger.log(this.logger.getLevel(), String.format(format, args));
    }
  }

  private void logFailure(String format, Object... args) {
    this.log(String.format("\u001B[31m\u2717 %s\u001B[0m", format), args);
  }

  private void logInfo(String format, Object... args) {
    this.log(String.format("\u001B[36m%s\u001B[0m", format), args);
  }

  private void logSuccess(String format, Object... args) {
    this.log(String.format("\u001B[32m\u2714 %s\u001B[0m", format), args);
  }

  private void logWarning(String format, Object... args) {
    this.log(String.format("\u001B[33m\u2717 %s\u001B[0m", format), args);
  }

  private String[] parseVerbose(String[] args) {
    int numArgs = args.length;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("--verbose") || args[i].equals("--no-verbose")) {
        this.listener.verbose = args[i].equals("--verbose");
        for (int j = i; j < args.length - 1; j++) {
          args[j] = args[j + 1];
        }
        args[args.length - 1] = "";
        numArgs--;
      }
    }
    return (numArgs == args.length) ? args : Arrays.copyOf(args, numArgs);
  }

  private void report(Result result) {
    this.log("%===============%\n%  TEST REPORT  %\n%===============%");
    if (result.getPassingCount() > 0) {
      this.logInfo(
              "%d passing (%dms) %s\n",
              result.getPassingCount(), result.getRunTime(), new String(Character.toChars(0x1f44f)));
    } else {
      this.logInfo("%d passing (%dms)\n", result.getPassingCount(), result.getRunTime());
    }
    if (result.wasSuccessful()) {
      this.logSuccess(
              "ALL TESTS PASSED! %s %s %s\n",
              new String(Character.toChars(0x1f3c6)),
              new String(Character.toChars(0x1f389)),
              new String(Character.toChars(0x1f389)));
    } else {
      this.logFailure("%d TEST FAILURES\n", result.getFailureCount());
    }
  }

  private void reset() {
    this.listener.clear();
  }

  private Result run(Class<?> test, boolean report) {
    return this.run(Request.classes(test), report);
  }

  private Result run(Class<?>[] tests, boolean report) {
    if (tests.length == 0) {
      return new Result();
    }
    return this.run(Request.classes(tests), report);
  }

  private Result run(Method testCase, boolean report) {
    return this.run(Request.method(testCase.getDeclaringClass(), testCase.getName()), report);
  }

  private Result run(Request request, boolean report) {
    Result result = Result.from(this.junit.run(request));
    if (report) {
      this.report(result);
    }
    this.reset();
    return result;
  }

  private Result runArgs(String[] args, boolean report) {
    args = this.parseVerbose(args);
    if (args.length == 0) {
      return this.runTestConfiguration();
    } else if (args[0].toLowerCase().equals("all")) {
      return this.runAllTests(report);
    } else if (args[0].equals("-f") || args[0].equals("--file")) {
      if (args.length != 2) {
        throw new RuntimeException(
                String.format("Unrecognized grader configuration `%s`", Arrays.toString(args)));
      }
      return this.runTestConfiguration(args[1]);
    } else if (args[0].charAt(0) == '#' || args[0].charAt(0) == '.' || args[0].charAt(0) == '!') {
      return this.runTestsWithFilter(args, report);
    } else if (args[0].equals("-help") || args[0].equals("--help")) {
      return this.runHelp();
    } else if (args[0].equals("-i") || args[0].equals("--interactive")) {
      return this.runInteractively(args);
    } else if (args[0].equals("-v") || args[0].equals("--version")) {
      return this.runVersion();
    } else if (args[0].substring(0, 2).equals("--")) {
      return this.runCustomTests(args, report);
    } else {
      return this.runAllTests(args, report);
    }
  }

  private Result runAllTests(boolean report) {
    return this.runAllTests(new String[0], report);
  }

  private Result runAllTests(String[] args, boolean report) {
    if (args.length == 0) {
      return this.run(this.getTests(), report);
    }
    ArrayList<Class<?>> tests = new ArrayList<>();
    for (int i = 0; i < args.length; i++) {
      if (!this.tests.containsKey(args[i])) {
        try {
          Class<?> test = Class.forName(args[i]);
          tests.add(test);
        } catch (ClassNotFoundException e) {
          throw new RuntimeException(String.format("Unrecognized test `%s`", args[i]));
        }
      } else {
        tests.add(this.tests.get(args[i]));
      }
    }
    return this.run(this.getTestsToArray(tests), report);
  }

  private Result runCustomTests(String[] args, boolean report) {
    Result result = new Result();
    int i = 0;
    while (i < args.length) {
      if (!args[i].substring(0, 2).equals("--")) {
        throw new RuntimeException(String.format("Unrecognized test `%s`", args[i]));
      }
      String testName = args[i].substring(2);
      Class<?> test;
      if (this.tests.containsKey(testName)) {
        test = this.tests.get(testName);
      } else {
        try {
          test = Class.forName(testName);
        } catch (ClassNotFoundException e) {
          throw new RuntimeException(String.format("Unrecognized test `%s`", testName));
        }
      }
      int j = i + 1;
      while (j < args.length && !args[j].substring(0, 2).equals("--")) {
        j++;
      }
      if (j == i + 1) {
        result = result.add(this.run(test, false));
      } else {
        result =
                result.add(this.runTestsWithFilter(Arrays.copyOfRange(args, i + 1, j), test, false));
      }
      i = j;
    }
    if (report) {
      this.report(result);
    }
    return result;
  }

  private Result runHelp() {
    System.out.println(
            "TUG [all | [<tests>]+ | [#<testCase> | .<testGroup>]+ | [--<test> [#<testCase> |"
                    + " \\.<testGroup>]*]+ | (-help | --help) | (-i | --interactive) | ((-f | --filename)"
                    + " <filename>)]");
    return new Result();
  }

  private Result runInteractively(String[] args) {
    ArrayList<Class<?>> tests = new ArrayList<>();
    for (int i = 1; i < args.length; i++) {
      try {
        tests.add(Class.forName(args[i]));
      } catch (Exception e) {
        this.logFailure(e.getMessage());
      }
    }
    this.initTests(this.getTestsToArray(tests));
    try {
      BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
      Result result = new Result();
      while (true) {
        System.out.print(">> ");
        String input = stdin.readLine().trim();
        if (input.toLowerCase().equals("quit")) {
          break;
        }
        try {
          result = result.add(this.runArgs(input.split(" "), true));
        } catch (Exception e) {
          this.logFailure(e.getMessage());
        }
      }
      this.report(result);
      return result;
    } catch (Exception e) {
      throw new RuntimeException(e.toString());
    }
  }

  private Result runTestConfiguration() {
    return this.runTestConfiguration(".grader.conf");
  }

  private Result runTestConfiguration(String filename) {
    try {
      String[] lines = Files.lines(Path.of(filename)).toArray(String[]::new);
      Result result = new Result();
      for (int i = 0; i < lines.length; i++) {
        result = result.add(this.runArgs(lines[i].trim().split(" "), false));
        if (result.wasSuccessful()) {
          this.logSuccess(
                  "COMPLETED STEP %d of %d %s\n",
                  i + 1, lines.length, new String(Character.toChars(0x1f44d)));
        } else {
          this.logFailure("FAILED STEP %d of %d\n", i + 1, lines.length);
          break;
        }
      }
      this.report(result);
      return result;
    } catch (Exception e) {
      throw new RuntimeException(e.toString());
    }
  }

  private Result runTestsWithFilter(String[] args, boolean report) {
    return this.runTestsWithFilter(args, this.getTests(), report);
  }

  private Result runTestsWithFilter(String[] args, Class<?> test, boolean report) {
    return this.runTestsWithFilter(args, new Class<?>[] {test}, report);
  }

  private Result runTestsWithFilter(String[] args, Class<?>[] tests, boolean report) {
    ArrayList<String> testCases = new ArrayList<>();
    ArrayList<String> testGroups = new ArrayList<>();
    ArrayList<String> ignoreCases = new ArrayList<>();
    ArrayList<String> ignoreGroups = new ArrayList<>();
    for (int i = 0; i < args.length; i++) {
      if (args[i].isEmpty() || args[i].isBlank()) {
        throw new RuntimeException("Unrecognized test case/group because arg is blank");
      }
      if (args[i].charAt(0) == '#') {
        testCases.add(args[i].substring(1));
      } else if (args[i].charAt(0) == '.') {
        testGroups.add(args[i].substring(1));
      } else if (args[i].length() >= 2 && args[i].substring(0, 2).equals("!#")) {
        ignoreCases.add(args[i].substring(2));
      } else if (args[i].length() >= 2 && args[i].substring(0, 2).equals("!.")) {
        ignoreGroups.add(args[i].substring(2));
      } else {
        throw new RuntimeException(String.format("Unrecognized test case/group `%s`", args[i]));
      }
    }
    Result result =
            Arrays.stream(tests)
                    .flatMap(
                            (Class<?> test) -> {
                              return Arrays.stream(test.getDeclaredMethods());
                            })
                    .filter(
                            (Method testCase) -> {
                              if (ignoreCases.contains(testCase.getName())) {
                                return false;
                              }
                              if (testCases.contains(testCase.getName())) {
                                return true;
                              }
                              if (!testCase.isAnnotationPresent(TestGroup.class)) {
                                return testCases.isEmpty() && testGroups.isEmpty();
                              }
                              for (String group : testCase.getAnnotation(TestGroup.class).value()) {
                                if (ignoreGroups.contains(group)) {
                                  return false;
                                }
                              }
                              for (String group : testCase.getAnnotation(TestGroup.class).value()) {
                                if (testGroups.contains(group)) {
                                  return true;
                                }
                              }
                              return testCases.isEmpty() && testGroups.isEmpty();
                            })
                    .map(testCase -> this.run(testCase, false))
                    .reduce(new Result(), (r1, r2) -> r1.add(r2));
    if (report) {
      this.report(result);
    }
    return result;
  }

  private Result runVersion() {
    System.out.println(String.format("TUGrader v%s", TUGrader.VERSION));
    return new Result();
  }

  // **************************************************
  // Public methods
  // **************************************************

  /**
   * Returns the test cases in the tests provided to the
   * TUGrader when it was instantiated.
   * <p>
   * These are the test cases that will be run with the
   * 'all' command.
   *
   * @return the tests cases provided to this instance
   */
  public Method[] getTestCases() {
    return this.getTestCasesToArray(this.testCases.values());
  }

  /**
   * Returns the tests provided to the TUGrader when it was
   * instantiated.
   * <p>
   * These are the test cases that will be run with the
   * 'all' command.
   *
   * @return the tests provided to this instance
   */
  public Class<?>[] getTests() {
    return this.getTestsToArray(this.tests.values());
  }

  /**
   * Executes the tests provided to the TUGrader.
   *
   * @return the result of the test execution
   */
  public Result run() {
    return this.run(new String[0]);
  }

  /**
   * Executes tests according to the provided args.
   * <ul>
   * <li>java TUGrader // executes test configuration .grader.conf
   * <li>java TUGrader all // executes all tests specified in main
   * <li>java TUGrader #testCase // executes test case
   * <li>java TUGrader .testGroup // executes test group
   * <li>java TUGrader --test // executes all test cases in test
   * <li>java TUGrader --test #testCase // executes test case in test
   * <li>java TUGrader --test .testGroup // executes test group in test
   * <li>java TUGrader -i // executes TUGrader interactively
   * <li>java TUGrader -f filename // executes test configuration file line-by-line and fails eagerly
   * <li>java TUGrader --help // prints usage information for TUGrader
   * <li>java TUGrader --version // prints TUGrader version
   * </ul>
   *
   * @param args  the clargs to use for the test execution
   * @return the result of the test execution
   */
  public Result run(String[] args) {
    this.log(
            "\n"
                    + "%===========================================%\n"
                    + "            .  o ..\n"
                    + "            o . o o.o\n"
                    + "                 ...oo\n"
                    + "                   __[]__\n"
                    + "                __|_o_o_o\\__\n"
                    + "                \"\"\"\"\"\"\"\"\"\"/\n"
                    + "                 \\. ..  . /\n"
                    + "            ^^^^^^^^^^^^^^^^^^^^\n"
                    + "           TUG - The TU Auto-Grader\n"
                    + "%===========================================%\n");
    return this.runArgs(args, true);
  }

  /**
   * Annotation for the dependencies of a test, which are
   * reported when the test is reported. It is assumed that
   * all dependencies of the test function properly. If
   * this assumption fails, then the results of the test
   * cannot be trusted.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  public static @interface Deps {
    /**
     * Returns the list of test dependencies.
     *
     * @return the list of test dependencies
     */
    String[] value();
  }

  /**
   * Annotation for the display name of a test, which is
   * displayed on the console when the test is reported.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  public static @interface DisplayName {
    /**
     * Returns the test display name.
     *
     * @return the test display name
     */
    String value();
  }

  /**
   * Annotation for labeling the test groups of a test.
   * These groups can be used to execute tests in batches.
   */
  @Retention(RetentionPolicy.RUNTIME)
  @Target(ElementType.METHOD)
  public static @interface TestGroup {
    /**
     * Returns the list of test groups.
     *
     * @return the list of test groups
     */
    String[] value();
  }

  /**
   * A class to use for testing asynchronous methods and
   * lambdas. Eval employs a simple counter to count the
   * number of successful events and then provides
   * assertions over the number of ticks.
   */
  public static class Eval {

    private int ticks = 0;

    /**
     * Throws an assertion error if the Eval has {@code 0}
     * ticks and then resets the ticks to {@code 0}.
     */
    public void done() throws AssertionError {
      this.done("Assessment was not successfully completed");
    }

    /**
     * Throws an assertion error if the Eval has {@code 0}
     * ticks and then resets the ticks to {@code 0}.
     *
     * @param reason  the reason for the failure
     */
    public void done(String reason) throws AssertionError {
      if (this.ticks <= 0) {
        throw new AssertionError(reason);
      }
      this.ticks = 0;
    }

    /**
     * Throws an assertion error if the Eval does not have
     * the expected number of ticks.
     *
     * @param ticks   the expected number of ticks
     */
    public void expect(int ticks) throws AssertionError {
      this.expect(String.format("Expected %d ticks but had %d ticks", ticks, this.ticks), ticks);
    }

    /**
     * Throws an assertion error if the Eval does not have
     * the expected number of ticks.
     *
     * @param reason  the reason for the failure
     * @param ticks   the expected number of ticks
     */
    public void expect(String reason, int ticks) throws AssertionError {
      if (this.ticks != ticks) {
        throw new AssertionError(reason);
      }
    }

    /** Automatically fails the test. */
    public void fail() throws AssertionError {
      this.fail("Assessment was not successfully completed");
    }

    /**
     * Automatically fails the test.
     *
     * @param reason  the reason for the failure
     */
    public void fail(String reason) throws AssertionError {
      throw new AssertionError(reason);
    }

    /**
     * Increments the number of ticks when the current
     * number of ticks is {@code 0}, or throws an assertion
     * error, otherwise.
     */
    public void success() throws AssertionError {
      if (this.ticks != 0) {
        throw new AssertionError("Assessment cannot be marked successful if it has other checks");
      }
      this.ticks++;
    }

    /**
     * Increments the number of ticks.
     *
     * @return the current number of ticks
     */
    public int tick() {
      this.ticks++;
      return this.ticks;
    }
  }

  /**
   * A class for storing the results of a batch of tests.
   * Results are composable, so results from separate
   * batches can be combined.
   */
  public static class Result {

    private int failureCount;
    private int runCount;
    private long runTime;

    /**
     * Wraps a JUnit Result.
     *
     * @param result  the JUnit Result to wrap
     * @return the wrapper result
     */
    public static Result from(org.junit.runner.Result result) {
      return new Result(result.getRunCount(), result.getFailureCount(), result.getRunTime());
    }

    /** Constructs an empty Result. */
    public Result() {
      this(0, 0, 0);
    }

    /**
     * Constructs a Result with the provided metrics. Throws
     * a runtime exception if the failure count is larger
     * than the run count.
     *
     * @param runCount      the number of test cases
     * @param failureCount  the number of test failures
     * @param runTime       the time taken (in ms) to
     * execute the test cases
     */
    public Result(int runCount, int failureCount, long runTime) throws RuntimeException {
      if (runCount < failureCount) {
        throw new RuntimeException(
                String.format("Result expected %d failures to be leq %d runs", failureCount, runCount));
      }
      this.runCount = runCount;
      this.failureCount = failureCount;
      this.runTime = runTime;
    }

    /**
     * Returns a new Result that is the composition of
     * this result and the input result.
     *
     * @param result  the other set of test results
     * @return the combined result
     */
    public Result add(Result result) {
      return new Result(
              this.runCount + result.runCount,
              this.failureCount + result.failureCount,
              this.runTime + result.runTime);
    }

    /**
     * Returns the number of test failures.
     *
     * @return the number of test failures
     */
    public int getFailureCount() {
      return this.failureCount;
    }

    /**
     * Returns the number of test successes.
     * <p>
     * It must be the case that the number of test
     * successes is: {@code getRunCount() - getFailureCount()}.
     *
     *
     * @return the number of test successes
     */
    public int getPassingCount() {
      return this.runCount - this.failureCount;
    }

    /**
     * Returns the number of executed test cases.
     *
     * @return the number of executed test cases
     */
    public int getRunCount() {
      return this.runCount;
    }

    /**
     * Returns the time taken to execute the test cases.
     *
     * @return the time taken to execute the test cases
     */
    public long getRunTime() {
      return this.runTime;
    }

    /**
     * Returns {@code true} if all test cases are test successes.
     * <p>
     * If {@code wasSuccessful() == true}, then it must be
     * the case that: {@code getRunCount() == getSuccessCount()}
     *
     * @return {@code true} if all test cases are test successes
     */
    public boolean wasSuccessful() {
      return this.failureCount == 0;
    }
  }

  private class TUGraderListener extends RunListener {

    HashSet<String> failures = new HashSet<>();
    boolean verbose;

    public TUGraderListener() {
      this(false);
    }

    public TUGraderListener(boolean verbose) {
      this.verbose = verbose;
    }

    public void clear() {
      this.failures = new HashSet<>();
    }

    @Override
    public void testAssumptionFailure(Failure failure) {
      this.failures.add(failure.getDescription().getMethodName());
      this.logDescription(failure.getDescription());
      TUGrader.this.logWarning("WARNING: %s\n", failure.getTrimmedTrace());
    }

    @Override
    public void testFailure(Failure failure) throws Exception {
      this.failures.add(failure.getDescription().getMethodName());
      this.logDescription(failure.getDescription());
      TUGrader.this.logFailure("FAILED: %s\n", failure.getTrimmedTrace());
    }

    @Override
    public void testFinished(Description description) throws Exception {
      if (!this.failures.contains(description.getMethodName()) && this.verbose) {
        this.logDescription(description);
        TUGrader.this.logSuccess("PASSED %s\n", new String(Character.toChars(0x1f388)));
      }
    }

    @Override
    public void testStarted(Description description) {}

    private void logDescription(Description description) {
      try {
        Class<?> test = Class.forName(description.getClassName());
        Method testCase = test.getDeclaredMethod(description.getMethodName());
        if (testCase.isAnnotationPresent(Deps.class)) {
          TUGrader.this.logInfo(
                  "TEST: [%s] %% %s\ndependencies: %s",
                  test.getSimpleName(),
                  TUGrader.this.getDisplayName(testCase),
                  Arrays.toString(testCase.getAnnotation(Deps.class).value()));
        } else {
          TUGrader.this.logInfo(
                  "TEST: [%s] %% %s", test.getSimpleName(), TUGrader.this.getDisplayName(testCase));
        }
      } catch (Exception e) {
        TUGrader.this.logInfo(
                "TEST: [%s] %% %s", description.getDisplayName(), description.getMethodName());
      }
    }
  }
}