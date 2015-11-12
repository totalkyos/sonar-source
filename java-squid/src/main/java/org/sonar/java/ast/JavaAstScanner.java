/*
 * SonarQube Java
 * Copyright (C) 2012 SonarSource
 * sonarqube@googlegroups.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.java.ast;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.sonar.sslr.api.RecognitionException;
import com.sonar.sslr.api.typed.ActionParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.java.JavaConfiguration;
import org.sonar.java.ast.parser.JavaParser;
import org.sonar.java.model.InternalVisitorsBridge;
import org.sonar.java.model.VisitorsBridge;
import org.sonar.plugins.java.api.JavaFileScanner;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.squidbridge.ProgressReport;
import org.sonar.squidbridge.api.AnalysisException;
import org.sonar.squidbridge.api.SourceCode;
import org.sonar.squidbridge.api.SourceCodeSearchEngine;
import org.sonar.squidbridge.api.SourceFile;
import org.sonar.squidbridge.api.SourceProject;
import org.sonar.squidbridge.indexer.QueryByType;
import org.sonar.squidbridge.indexer.SquidIndex;

import javax.annotation.Nullable;

import java.io.File;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class JavaAstScanner {

  private static final Logger LOG = LoggerFactory.getLogger(JavaAstScanner.class);

  private final SquidIndex index;
  private final ActionParser<Tree> parser;
  private InternalVisitorsBridge visitor;

  public JavaAstScanner(ActionParser<Tree> parser) {
    this.parser = parser;
    this.index = new SquidIndex();
  }

  /**
   * Takes parser and index from another instance of {@link JavaAstScanner}
   */
  public JavaAstScanner(JavaAstScanner astScanner) {
    this.parser = astScanner.parser;
    this.index = astScanner.index;
  }

  public void scan(Iterable<File> files) {
    SourceProject project = new SourceProject("Java Project");
    index.index(project);
    project.setSourceCodeIndexer(index);

    simpleScan(files);

  }

  /**
   * Used to do scan of test files.
   *
   * @param files
   */
  public void simpleScan(Iterable<File> files) {
    ExecutorService executor = createExecutor();
    ProgressReport progressReport = new ProgressReport("Report about progress of Java AST analyzer", TimeUnit.SECONDS.toMillis(10));
    progressReport.start(Lists.newArrayList(files));
    Map<File, Future<Void>> results = new LinkedHashMap<>();

    for (File file : files) {
      try {
        // TODO: try parallelize this too
        Tree ast = parser.parse(file);
        results.put(file, executor.submit(new FileScanner(file, ast)));
      } catch (RecognitionException e) {
        LOG.error("Unable to parse source file : " + file.getAbsolutePath());
        LOG.error(e.getMessage());

        parseErrorWalkAndVisit(e, file);
      } catch (Exception e) {
        throw new AnalysisException(getSubmissionExceptionMessage(file), e);
      } finally {
        // TODO change progress report to not log in this case, but still stop the thread. a cancel() is available in SSLR bridge 2.7.
        progressReport.stop();
      }
    }

    waitTermination(executor, results);
    progressReport.stop();
  }

  private void waitTermination(ExecutorService executor, Map<File, Future<Void>> results) {
    LOG.info("Waiting termination");

    Iterator<Entry<File, Future<Void>>> it = results.entrySet().iterator();
    while (it.hasNext()) {
      //TODO: progress report here. The message of the progress report should be changed.
      Entry<File, Future<Void>> entry = it.next();
      try {
        entry.getValue().get();
      } catch (Exception e) {
        throw new AnalysisException(getAnalyisExceptionMessage(entry.getKey()), e);
      }
    }

    executor.shutdown();
    try {
      boolean success = executor.awaitTermination(5, TimeUnit.MINUTES);
      if (!success) {
        executor.shutdownNow();
        throw new IllegalStateException("Timed out waiting for analysis");
      }
    } catch (InterruptedException e) {
      // send interrupt to other threads too
      executor.shutdownNow();
      throw new IllegalStateException("Thread interrupted - analysis cancelled");
    }
  }

  private ExecutorService createExecutor() {
    // think about limited size queue (blocking and waiting if full). It's more complicated to do it than it might look.
    BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();
    int numThreads = Runtime.getRuntime().availableProcessors() + 1;
    return new ThreadPoolExecutor(numThreads, numThreads, 0L, TimeUnit.MILLISECONDS, queue, new ThreadFactoryBuilder().setNameFormat("file-scan-%d").build());
  }

  private class FileScanner implements Callable<Void> {
    private File file;
    private Tree ast;

    FileScanner(File file, Tree ast) {
      this.ast = ast;
      this.file = file;
    }

    @Override
    public Void call() throws Exception {
      visitor.visitFile(ast, file);
      return null;
    }
  }

  private void parseErrorWalkAndVisit(RecognitionException e, File file) {
    try {
      // Process the exception
      visitor.visitFile(null, null);
      visitor.processRecognitionException(e);
    } catch (Exception e2) {
      throw new AnalysisException(getAnalyisExceptionMessage(file), e2);
    }
  }

  private static String getAnalyisExceptionMessage(File file) {
    return "SonarQube is unable to analyze file : '" + file.getAbsolutePath() + "'";
  }

  private static String getSubmissionExceptionMessage(File file) {
    return "Failed to submit file for analysis : '" + file.getAbsolutePath() + "'";
  }

  public void setVisitorBridge(InternalVisitorsBridge visitor) {
    this.visitor = visitor;
  }

  public SourceCodeSearchEngine getIndex() {
    return index;
  }

  /**
   * Helper method for testing checks without having to deploy them on a Sonar instance.
   * Can be dropped when support for CheckMessageVerifier will be dropped.
   *
   * @deprecated As of release 3.6, should use {@link org.sonar.java.checks.verifier.JavaCheckVerifier#verify(String filename, JavaFileScanner check)} for rules unit tests.
   */
  @VisibleForTesting
  @Deprecated
  public static SourceFile scanSingleFile(File file, VisitorsBridge visitorsBridge) {
    if (!file.isFile()) {
      throw new IllegalArgumentException("File '" + file + "' not found.");
    }
    JavaAstScanner scanner = create(new JavaConfiguration(Charset.forName("UTF-8")), visitorsBridge);

    scanner.scan(Collections.singleton(file));
    Collection<SourceCode> sources = scanner.getIndex().search(new QueryByType(SourceFile.class));
    if (sources.size() != 1) {
      throw new IllegalStateException("Only one SourceFile was expected whereas " + sources.size() + " has been returned.");
    }
    return (SourceFile) sources.iterator().next();
  }

  @VisibleForTesting
  public static void scanSingleFileForTests(File file, VisitorsBridge visitorsBridge) {
    scanSingleFileForTests(file, visitorsBridge, new JavaConfiguration(Charset.forName("UTF-8")));
  }

  @VisibleForTesting
  public static void scanSingleFileForTests(File file, VisitorsBridge visitorsBridge, JavaConfiguration conf) {
    if (!file.isFile()) {
      throw new IllegalArgumentException("File '" + file + "' not found.");
    }
    JavaAstScanner scanner = create(conf, visitorsBridge);

    scanner.scan(Collections.singleton(file));
    Collection<SourceCode> sources = scanner.getIndex().search(new QueryByType(SourceFile.class));
    if (sources.size() != 1) {
      throw new IllegalStateException("Only one SourceFile was expected whereas " + sources.size() + " has been returned.");
    }
  }

  private static JavaAstScanner create(JavaConfiguration conf, @Nullable VisitorsBridge visitorsBridge) {
    JavaAstScanner astScanner = new JavaAstScanner(JavaParser.createParser(conf.getCharset()));
    if (visitorsBridge != null) {
      visitorsBridge.setCharset(conf.getCharset());
      visitorsBridge.setJavaVersion(conf.javaVersion());
      astScanner.setVisitorBridge(visitorsBridge);
    }
    return astScanner;
  }

}
