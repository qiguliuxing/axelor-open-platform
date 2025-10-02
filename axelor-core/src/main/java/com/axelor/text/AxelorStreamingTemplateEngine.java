/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.text;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyCodeSource;
import groovy.lang.GroovyObject;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.Writable;
import groovy.text.StreamingTemplateEngine;
import groovy.text.Template;
import groovy.text.TemplateExecutionException;
import groovy.text.TemplateParseException;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.groovy.util.SystemUtil;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.ErrorCollector;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.control.messages.Message;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.runtime.StackTraceUtils;
import org.codehaus.groovy.syntax.SyntaxException;

/**
 * StreamingTemplateEngine implementation that allows to pass CompilerConfiguration.
 *
 * <p>This allows to apply custom script transformation (for scripting policy).
 *
 * <p>Also keep default of not reusing class loader so that each script class can individually be
 * garbage collected.
 */
public class AxelorStreamingTemplateEngine extends StreamingTemplateEngine {

  protected static final String TEMPLATE_SCRIPT_PREFIX = "StreamingTemplateScript";
  protected static final AtomicInteger COUNTER = new AtomicInteger(0);

  protected static final boolean REUSE_CLASS_LOADER =
      SystemUtil.getBooleanSafe("groovy.StreamingTemplateEngine.reuseClassLoader");

  protected final ClassLoader parentLoader;
  protected final CompilerConfiguration config;

  /**
   * Creates a StreamingTemplateEngine with the given parent class loader and compiler
   * configuration.
   *
   * @param parentLoader
   * @param config
   */
  public AxelorStreamingTemplateEngine(ClassLoader parentLoader, CompilerConfiguration config) {
    super(parentLoader);
    this.parentLoader = parentLoader;
    this.config = config;
  }

  @Override
  public Template createTemplate(final Reader reader)
      throws CompilationFailedException, ClassNotFoundException, IOException {
    return new AxelorStreamingTemplate(reader, parentLoader, config);
  }

  /** The class used to implement the Template interface for the StreamingTemplateEngine */
  private static class AxelorStreamingTemplate implements Template {
    /**
     * The 'header' we use for the resulting groovy script source.
     *
     * <p>Custom: moved setting delegate to {@link #make(Map)}
     */
    private static final String SCRIPT_HEAD =
        "package groovy.tmp.templates;"
            + "def getTemplate() { "
            // the below params are:
            //  _p - parent class, for handling exceptions
            //  _s - sections, string sections list
            //  out - out stream
            // the three first parameters will be curried in as we move along
            + "return { _p, _s, out ->"
            + "int _i = 0;"
            + "try {";

    /** The 'footer' we use for the resulting groovy script source */
    private static final String SCRIPT_TAIL =
        "} catch (Throwable e) { " + "_p.error(_i, _s, e);" + "}" + "}" + "}";

    private StringBuilder templateSource;

    // we use a hard index instead of incrementing the _i variable due to previous
    // bug where the increment was not executed when hitting non-executed if branch
    private int index = 0;
    final Closure template;

    String scriptSource;

    private static class FinishedReadingException extends Exception {
      private static final long serialVersionUID = -3786157136157691230L;
    }

    // WE USE THIS AS REUSABLE
    // CHECKSTYLE.OFF: ConstantNameCheck - special case with a reusable exception
    private static final FinishedReadingException finishedReadingException;
    // CHECKSTYLE.ON: ConstantNameCheck

    public static final StackTraceElement[] EMPTY_STACKTRACE = new StackTraceElement[0];

    static {
      finishedReadingException = new FinishedReadingException();
      finishedReadingException.setStackTrace(EMPTY_STACKTRACE);
    }

    private static final class Position {
      // CHECKSTYLE.OFF: VisibilityModifierCheck - special case, direct access for performance
      public int row;
      public int column;

      // CHECKSTYLE.ON: VisibilityModifierCheck

      private Position(int row, int column) {
        this.row = row;
        this.column = column;
      }

      private Position(Position p) {
        set(p);
      }

      private void set(Position p) {
        this.row = p.row;
        this.column = p.column;
      }

      @Override
      public String toString() {
        return row + ":" + column;
      }
    }

    /**
     * A StringSection represent a section in the template source with only string data (i.e. no
     * branching, GString references, etc). As an example, the following template string:
     *
     * <pre>
     * Alice why is a $bird like a writing desk
     * </pre>
     *
     * <p>Would produce a string section "Alice why is a " followed by a dollar identifier
     * expression followed by another string section " like a writing desk".
     */
    private static final class StringSection {
      StringBuilder data;
      Position firstSourcePosition;
      Position lastSourcePosition;
      Position lastTargetPosition;

      private StringSection(Position firstSourcePosition) {
        this.data = new StringBuilder();
        this.firstSourcePosition = new Position(firstSourcePosition);
      }

      @Override
      public String toString() {
        return data.toString();
      }
    }

    /**
     * Called to handle the ending of a string section.
     *
     * @param sections The list of string sections. The current section gets added to this section.
     * @param currentSection The current string section.
     * @param templateExpressions Template expressions
     * @param lastSourcePosition The last read position in the source template stream.
     * @param targetPosition The last written to position in the target script stream.
     */
    private void finishStringSection(
        List<StringSection> sections,
        StringSection currentSection,
        StringBuilder templateExpressions,
        Position lastSourcePosition,
        Position targetPosition) {
      // when we get exceptions from the parseXXX methods in the main loop, we might try to
      // re-finish a section
      if (currentSection.lastSourcePosition != null) {
        return;
      }
      currentSection.lastSourcePosition = new Position(lastSourcePosition);
      sections.add(currentSection);
      append(templateExpressions, targetPosition, "out<<_s[_i=" + index++ + "];");
      currentSection.lastTargetPosition = new Position(targetPosition.row, targetPosition.column);
    }

    public void error(int index, List<StringSection> sections, Throwable e) throws Throwable {
      int i = Math.max(0, index);
      StringSection precedingSection = sections.get(i);
      int traceLine = -1;
      for (StackTraceElement element : e.getStackTrace()) {
        if (element.getClassName().contains(TEMPLATE_SCRIPT_PREFIX)) {
          traceLine = element.getLineNumber();
          break;
        }
      }

      if (traceLine != -1) {
        int actualLine = precedingSection.lastSourcePosition.row + traceLine - 1;
        String message =
            "Template execution error at line " + actualLine + ":\n" + getErrorContext(actualLine);
        TemplateExecutionException unsanitized =
            new TemplateExecutionException(actualLine, message, StackTraceUtils.sanitize(e));
        throw StackTraceUtils.sanitize(unsanitized);
      } else {
        throw e;
      }
    }

    @SuppressFBWarnings(
        value = "SR_NOT_CHECKED",
        justification = "safe to ignore return value of skip from reader backed by StringReader")
    private int getLinesInSource() throws IOException {
      int result;

      try (LineNumberReader reader =
          new LineNumberReader(new StringReader(templateSource.toString()))) {
        reader.skip(Long.MAX_VALUE); // SR_NOT_CHECKED
        result = reader.getLineNumber();
      }

      return result;
    }

    private String getErrorContext(int actualLine) throws IOException {
      int minLine = Math.max(0, actualLine - 1);
      int maxLine = Math.min(getLinesInSource(), actualLine + 1);

      LineNumberReader r = new LineNumberReader(new StringReader(templateSource.toString()));
      int lineNr;
      StringBuilder result = new StringBuilder();
      while ((lineNr = r.getLineNumber() + 1) <= maxLine) {
        String line = r.readLine();
        if (lineNr < minLine) continue;

        String nr = Integer.toString(lineNr);
        if (lineNr == actualLine) {
          nr = " --> " + nr;
        }

        result.append(padLeft(nr, 10));
        result.append(": ");
        result.append(line);
        result.append('\n');
      }

      return result.toString();
    }

    private String padLeft(String s, int len) {
      StringBuilder b = new StringBuilder(s);
      while (b.length() < len) b.insert(0, " ");
      return b.toString();
    }

    /**
     * Turn the template into a writable Closure. When executed the closure evaluates all the code
     * embedded in the template and then writes a GString containing the fixed and variable items to
     * the writer passed as a parameter
     *
     * <p>For example:
     *
     * <pre>
     * '<%= "test" %> of expr and <% test = 1 %>${test} script.'
     * </pre>
     *
     * would compile into:
     *
     * <pre>
     * { out -> out << "${"test"} of expr and "; test = 1 ; out << "${test} script."}.asWritable()
     * </pre>
     *
     * @param source A reader into the template source data
     * @param parentLoader A class loader we use
     * @param config The compiler configuration
     * @throws CompilationFailedException
     * @throws ClassNotFoundException
     * @throws IOException
     */
    AxelorStreamingTemplate(
        final Reader source, final ClassLoader parentLoader, CompilerConfiguration config)
        throws CompilationFailedException, ClassNotFoundException, IOException {
      final StringBuilder target = new StringBuilder();
      List<StringSection> sections = new ArrayList<>();
      Position sourcePosition = new Position(1, 1);
      Position targetPosition = new Position(1, 1);
      Position lastSourcePosition = new Position(1, 1);
      StringSection currentSection = new StringSection(sourcePosition);
      templateSource = new StringBuilder();

      // we use the lookAhead to make sure that a template file ending in say "abcdef\\"
      // will give a result of "abcdef\\" even though we have special handling for \\
      StringBuilder lookAhead = new StringBuilder(10);

      append(target, targetPosition, SCRIPT_HEAD);
      try {
        int skipRead = -1;
        //noinspection InfiniteLoopStatement
        while (true) {
          lastSourcePosition.set(sourcePosition);

          int c = (skipRead != -1) ? skipRead : read(source, sourcePosition, lookAhead);
          skipRead = -1;

          if (c == '\\') {
            handleEscaping(source, sourcePosition, currentSection, lookAhead);
            continue;
          } else if (c == '<') {
            c = read(source, sourcePosition, lookAhead);
            if (c == '%') {
              c = read(source, sourcePosition);
              clear(lookAhead);
              if (c == '=') {
                finishStringSection(
                    sections, currentSection, target, lastSourcePosition, targetPosition);
                parseExpression(source, target, sourcePosition, targetPosition);
                currentSection = new StringSection(sourcePosition);
                continue;
              } else {
                finishStringSection(
                    sections, currentSection, target, lastSourcePosition, targetPosition);
                parseSection(c, source, target, sourcePosition, targetPosition);
                currentSection = new StringSection(sourcePosition);
                continue;
              }
            } else {
              currentSection.data.append('<');
            }
          } else if (c == '$') {
            c = read(source, sourcePosition);
            clear(lookAhead);
            if (c == '{') {
              finishStringSection(
                  sections, currentSection, target, lastSourcePosition, targetPosition);
              parseDollarCurlyIdentifier(source, target, sourcePosition, targetPosition);
              currentSection = new StringSection(sourcePosition);
              continue;
            } else if (Character.isJavaIdentifierStart(c)) {
              finishStringSection(
                  sections, currentSection, target, lastSourcePosition, targetPosition);
              skipRead = parseDollarIdentifier(c, source, target, sourcePosition, targetPosition);
              currentSection = new StringSection(sourcePosition);
              continue;
            } else {
              currentSection.data.append('$');
            }
          }
          currentSection.data.append((char) c);
          clear(lookAhead);
        }
      } catch (FinishedReadingException e) {
        if (lookAhead.length() > 0) {
          currentSection.data.append(lookAhead);
        }
        // Ignored here, just used for exiting the read loop. Yeah I know we don't like
        // empty catch blocks or expected behavior trowing exceptions, but this just cleaned out the
        // code
        // _so_ much that I thought it worth it...this once -Matias Bjarland 20100126
      }

      finishStringSection(sections, currentSection, target, sourcePosition, targetPosition);
      append(target, targetPosition, SCRIPT_TAIL);

      scriptSource = target.toString();

      this.template = createTemplateClosure(sections, parentLoader, config, target);
    }

    private static void clear(StringBuilder lookAhead) {
      lookAhead.delete(0, lookAhead.length());
    }

    private void handleEscaping(
        final Reader source,
        final Position sourcePosition,
        final StringSection currentSection,
        final StringBuilder lookAhead)
        throws IOException, FinishedReadingException {
      // if we get here, we just read in a back-slash from the source, now figure out what to do
      // with it
      int c = read(source, sourcePosition, lookAhead);

      /*
      The _only_ special escaping this template engine allows is to escape the sequences:
      ${ and <% and potential slashes in front of these. Escaping in any other sections of the
      source string is ignored. The following is a source -> result mapping of a few values, assume a
      binding of [alice: 'rabbit'].

      Note: we don't do java escaping of slashes in the below
      example, i.e. the source string is what you would see in a text editor when looking at your template
      file:
      source string     result
      'bob'            -> 'bob'
      '\bob'           -> '\bob'
      '\\bob'          -> '\\bob'
      '${alice}'       -> 'rabbit'
      '\${alice}'      -> '${alice}'
      '\\${alice}'     -> '\rabbit'
      '\\$bob'         -> '\\$bob'
      '\\'             -> '\\'
      '\\\'             -> '\\\'
      '%<= alice %>'   -> 'rabbit'
      '\%<= alice %>'  -> '%<= alice %>'
      */
      if (c == '\\') {
        // this means we have received a double backslash sequence
        // if this is followed by ${ or <% we output one backslash
        // and interpret the following sequences with groovy, if followed by anything
        // else we output the two backslashes and continue as usual
        source.mark(3);
        int d = read(source, sourcePosition, lookAhead);
        c = read(source, sourcePosition, lookAhead);
        clear(lookAhead);
        if ((d == '$' && c == '{') || (d == '<' && c == '%')) {
          source.reset();
          currentSection.data.append('\\');
          return;
        } else {
          currentSection.data.append('\\');
          currentSection.data.append('\\');
          currentSection.data.append((char) d);
        }
      } else if (c == '$') {
        c = read(source, sourcePosition, lookAhead);
        if (c == '{') {
          currentSection.data.append('$');
        } else {
          currentSection.data.append('\\');
          currentSection.data.append('$');
        }
      } else if (c == '<') {
        c = read(source, sourcePosition, lookAhead);
        if (c == '%') {
          currentSection.data.append('<');
        } else {
          currentSection.data.append('\\');
          currentSection.data.append('<');
        }
      } else {
        currentSection.data.append('\\');
      }

      currentSection.data.append((char) c);
      clear(lookAhead);
    }

    private Closure createTemplateClosure(
        List<StringSection> sections,
        final ClassLoader parentLoader,
        CompilerConfiguration config,
        StringBuilder target)
        throws ClassNotFoundException {
      final GroovyClassLoader loader =
          REUSE_CLASS_LOADER && parentLoader instanceof GroovyClassLoader
              ? (GroovyClassLoader) parentLoader
              : createClassLoader(parentLoader, config);
      final Class<?> groovyClass;
      try {
        groovyClass =
            loader.parseClass(
                new GroovyCodeSource(
                    target.toString(),
                    TEMPLATE_SCRIPT_PREFIX + COUNTER.incrementAndGet() + ".groovy",
                    "x"));
      } catch (MultipleCompilationErrorsException e) {
        throw mangleMultipleCompilationErrorsException(e, sections);
      } catch (Exception e) {
        throw new GroovyRuntimeException(
            "Failed to parse template script (your template may contain an error or be trying to"
                + " use expressions not currently supported): "
                + e.getMessage());
      }

      Closure result;
      try {
        final GroovyObject object =
            (GroovyObject) groovyClass.getDeclaredConstructor().newInstance();
        // Custom: moved from getTemplate() script to avoid pre-execution policy check.
        Closure chicken = ((Closure) object.invokeMethod("getTemplate", null)).asWritable();
        // bind the two first parameters of the generated closure to this class and the sections
        // list
        result = chicken.curry(this, sections);
      } catch (InstantiationException
          | IllegalAccessException
          | NoSuchMethodException
          | InvocationTargetException e) {
        throw new ClassNotFoundException(e.getMessage());
      }

      return result;
    }

    @SuppressWarnings(
        "removal") // TODO a future Groovy version should create the loader not as a privileged
    // action
    private GroovyClassLoader createClassLoader(
        ClassLoader parentLoader, CompilerConfiguration config) {
      return java.security.AccessController.doPrivileged(
          (PrivilegedAction<GroovyClassLoader>) () -> new GroovyClassLoader(parentLoader, config));
    }

    /**
     * Parses a non-curly dollar preceded identifier of the type '$bird' in the following template
     * example:
     *
     * <pre>
     * Alice why is a $bird like a writing desk
     * </pre>
     *
     * <p>which would produce the following template data:
     *
     * <pre>
     * out << "Alice why is a ";
     * out << bird;
     * out << " like a writing desk";
     * </pre>
     *
     * <p>This method is given the 'b' in 'bird' in argument c, checks if it is a valid java
     * identifier start (we assume groovy did not mangle the java identifier rules). If so it
     * proceeds to parse characters from the input until it encounters a non-java-identifier
     * character. At that point
     *
     * @param c The first letter of the potential identifier, 'b' in the above example
     * @param reader The reader reading from the template source
     * @param target The target groovy script source we write to
     * @param sourcePosition The reader position in the source stream
     * @param targetPosition The writer position in the target stream
     * @return true if a valid dollar preceded identifier was found, false otherwise. More
     *     specifically, returns true if the first character after the dollar sign is a valid java
     *     identifier. Note that the dollar curly syntax is handled by another method.
     * @throws IOException
     * @throws FinishedReadingException If we encountered the end of the source stream.
     */
    private int parseDollarIdentifier(
        int c,
        final Reader reader,
        final StringBuilder target,
        final Position sourcePosition,
        final Position targetPosition)
        throws IOException, FinishedReadingException {
      append(target, targetPosition, "out<<");
      append(target, targetPosition, (char) c);

      while (true) {
        c = read(reader, sourcePosition);
        if (!Character.isJavaIdentifierPart(c) || c == '$') {
          break;
        }
        append(target, targetPosition, (char) c);
      }

      append(target, targetPosition, ";");

      return c;
    }

    /**
     * Parses a dollar curly preceded identifier of the type '${bird}' in the following template
     * example:
     *
     * <pre>
     * Alice why is a ${bird} like a writing desk
     * </pre>
     *
     * <p>which would produce the following template data:
     *
     * <pre>
     * out << "Alice why is a ";
     * out << """${bird}""";
     * out << " like a writing desk";
     * </pre>
     *
     * <p>This method is given the 'b' in 'bird' in argument c, checks if it is a valid java
     * identifier start (we assume groovy did not mangle the java identifier rules). If so it
     * proceeds to parse characters from the input until it encounters a non-java-identifier
     * character. At that point
     *
     * @param reader The reader reading from the template source
     * @param target The target groovy script source we write to
     * @param sourcePosition The reader position in the source stream
     * @param targetPosition The writer position in the target stream
     * @throws IOException
     * @throws FinishedReadingException
     */
    private void parseDollarCurlyIdentifier(
        final Reader reader,
        final StringBuilder target,
        final Position sourcePosition,
        final Position targetPosition)
        throws IOException, FinishedReadingException {
      append(target, targetPosition, "out<<\"\"\"${");

      while (true) {
        int c = read(reader, sourcePosition);
        append(target, targetPosition, (char) c);
        if (c == '}') break;
      }

      append(target, targetPosition, "\"\"\";");
    }

    /**
     * Parse a &lt;% .... %&gt; section if we are writing a GString close and append ';' then write
     * the section as a statement
     */
    private void parseSection(
        final int pendingC,
        final Reader reader,
        final StringBuilder target,
        final Position sourcePosition,
        final Position targetPosition)
        throws IOException, FinishedReadingException {
      // the below is a quirk, we do this so that every non-string-section is prefixed by
      // the same number of characters (the others have "out<<\"\"\"${"), this allows us to
      // figure out the exception row and column later on
      append(target, targetPosition, "          ");
      append(target, targetPosition, (char) pendingC);

      readAndAppend(reader, target, sourcePosition, targetPosition);

      append(target, targetPosition, ';');
    }

    private void readAndAppend(
        Reader reader, StringBuilder target, Position sourcePosition, Position targetPosition)
        throws IOException, FinishedReadingException {
      while (true) {
        int c = read(reader, sourcePosition);
        if (c == '%') {
          c = read(reader, sourcePosition);
          if (c == '>') break;
          append(target, targetPosition, '%');
        }
        append(target, targetPosition, (char) c);
      }
    }

    /** Parse a &lt;%= .... %&gt; expression */
    private void parseExpression(
        final Reader reader,
        final StringBuilder target,
        final Position sourcePosition,
        final Position targetPosition)
        throws IOException, FinishedReadingException {
      append(target, targetPosition, "out<<\"\"\"${");

      readAndAppend(reader, target, sourcePosition, targetPosition);

      append(target, targetPosition, "}\"\"\";");
    }

    @Override
    public Writable make() {
      return make(null);
    }

    @Override
    public Writable make(final Map map) {
      // Custom: moved from getTemplate() script to avoid pre-execution policy check.
      final Closure templateClosure = ((Closure) this.template.clone());
      Binding binding = new Binding(map);
      templateClosure.setDelegate(binding);
      return (Writable) templateClosure;
    }

    /*
     * Create groovy assertion style error message for template error. Example:
     *
     * Error parsing expression on line 71 column 15, message: no such property jboss for class DUMMY
     * templatedata${jboss}templateddatatemplateddata
     *             ^------^
     *                 |
     *           syntax error
     */
    private RuntimeException mangleMultipleCompilationErrorsException(
        MultipleCompilationErrorsException e, List<StringSection> sections) {
      RuntimeException result = e;

      ErrorCollector collector = e.getErrorCollector();
      @SuppressWarnings({"unchecked"})
      List<Message> errors = (List<Message>) collector.getErrors();
      if (!errors.isEmpty()) {
        Message firstMessage = errors.get(0);
        if (firstMessage instanceof SyntaxErrorMessage) {
          @SuppressWarnings({"ThrowableResultOfMethodCallIgnored"})
          SyntaxException syntaxException = ((SyntaxErrorMessage) firstMessage).getCause();
          Position errorPosition =
              new Position(syntaxException.getLine(), syntaxException.getStartColumn());

          // find the string section which precedes the row/col of the thrown exception
          StringSection precedingSection = findPrecedingSection(errorPosition, sections);

          // and now use the string section to mangle the line numbers so that they refer to the
          // appropriate line in the source template data
          if (precedingSection != null) {
            // if the error was thrown on the same row as where the last string section
            // ended, fix column value
            offsetPositionFromSection(errorPosition, precedingSection);
            // the below being true indicates that we had an unterminated ${ or <% sequence and
            // the column is thus meaningless, we reset it to where the %{ or <% starts to at
            // least give the user a sporting chance
            if (sections.get(sections.size() - 1) == precedingSection) {
              errorPosition.column = precedingSection.lastSourcePosition.column;
            }

            String message = mangleExceptionMessage(e.getMessage(), errorPosition);
            result =
                new TemplateParseException(message, e, errorPosition.row, errorPosition.column);
          }
        }
      }

      return result;
    }

    private String mangleExceptionMessage(String original, Position p) {
      String result = original;
      int idx = result.indexOf("@ line ");
      if (idx != -1) {
        result = result.substring(0, idx);
      }

      int count = 0;
      idx = 0;
      for (char c : result.toCharArray()) {
        if (c == ':') {
          count++;
          if (count == 3) {
            result = result.substring(idx + 2);
            break;
          }
        }
        idx++;
      }

      String msg =
          "Template parse error '" + result + "' at line " + p.row + ", column " + p.column;
      try {
        msg += "\n" + getErrorContext(p.row);
      } catch (IOException e) {
        // we opt for not doing anything here...we just do not get context if
        // this happens
      }

      return msg;
    }

    private void offsetPositionFromSection(Position p, StringSection s) {
      if (p.row == s.lastTargetPosition.row) {
        // The number 8 below represents the number of characters in the header of a
        // non-string-section such as
        // <% ... %>. A section like this is represented in the target script as:
        // out<<"""......."""
        // 12345678
        p.column -= s.lastTargetPosition.column + 8;
        p.column += s.lastSourcePosition.column;
      }

      p.row += s.lastSourcePosition.row - 1;
    }

    private StringSection findPrecedingSection(Position p, List<StringSection> sections) {
      StringSection result = null;
      for (StringSection s : sections) {
        if (s.lastTargetPosition.row > p.row
            || (s.lastTargetPosition.row == p.row && s.lastTargetPosition.column > p.column)) {
          break;
        }
        result = s;
      }

      return result;
    }

    private void append(final StringBuilder target, Position targetPosition, char c) {
      if (c == '\n') {
        targetPosition.row++;
        targetPosition.column = 1;
      } else {
        targetPosition.column++;
      }

      target.append(c);
    }

    private void append(final StringBuilder target, Position targetPosition, String s) {
      int len = s.length();
      for (int i = 0; i < len; i++) {
        append(target, targetPosition, s.charAt(i));
      }
    }

    private int read(final Reader reader, Position position, StringBuilder lookAhead)
        throws IOException, FinishedReadingException {
      int c = read(reader, position);
      lookAhead.append((char) c);
      return c;
    }

    // SEE BELOW
    boolean useLastRead = false;
    private int lastRead = -1;

    /* All \r\n sequences are treated as a single \n. By doing this we
     * produce the same output as the GStringTemplateEngine. Otherwise, some
     * of our output is on a newline when it should not be.
     *
     * Instead of using a pushback reader, we just keep a private instance
     * variable 'lastRead'.
     */
    private int read(final Reader reader, Position position)
        throws IOException, FinishedReadingException {
      int c;

      if (useLastRead) {
        // use last one if we stored a character
        c = lastRead;
        // reset last
        useLastRead = false;
        lastRead = -1;
      } else {
        c = read(reader);
        if (c == '\r') {
          // IF CRLF JUST KEEP LF
          c = read(reader);
          if (c != '\n') {
            // ELSE keep original char
            // and pushback the one we just read
            lastRead = c;
            useLastRead = true;
            c = '\r';
          }
        }
      }

      if (c == -1) {
        throw finishedReadingException;
      }

      if (c == '\n') {
        position.row++;
        position.column = 1;
      } else {
        position.column++;
      }

      return c;
    }

    private int read(final Reader reader) throws IOException {
      int c = reader.read();
      templateSource.append((char) c);
      return c;
    }
  }
}
