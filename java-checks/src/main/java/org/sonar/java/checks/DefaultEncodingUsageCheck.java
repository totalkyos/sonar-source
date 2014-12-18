/*
 * SonarQube Java
 * Copyright (C) 2012 SonarSource
 * dev@sonar.codehaus.org
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
package org.sonar.java.checks;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import org.sonar.check.Priority;
import org.sonar.check.Rule;
import org.sonar.java.checks.methods.AbstractMethodDetection;
import org.sonar.java.checks.methods.MethodInvocationMatcher;
import org.sonar.java.model.AbstractTypedTree;
import org.sonar.java.model.expression.MethodInvocationTreeImpl;
import org.sonar.java.model.expression.NewClassTreeImpl;
import org.sonar.java.resolve.Symbol.MethodSymbol;
import org.sonar.java.resolve.Type;
import org.sonar.plugins.java.api.JavaFileScannerContext;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.NewClassTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.VariableTree;

import java.util.List;
import java.util.Set;

@Rule(
  key = "S1943",
  priority = Priority.MAJOR,
  tags = {"bug"})
public class DefaultEncodingUsageCheck extends AbstractMethodDetection {

  private static final String[] FORBIDDEN_TYPES = {"java.io.FileReader", "java.io.FileWriter"};

  private Set<Tree> excluded = Sets.newHashSet();

  @Override
  public void scanFile(JavaFileScannerContext context) {
    super.scanFile(context);
    excluded.clear();
  }

  @Override
  public List<Tree.Kind> nodesToVisit() {
    return ImmutableList.of(Tree.Kind.METHOD_INVOCATION, Tree.Kind.NEW_CLASS, Tree.Kind.VARIABLE);
  }

  @Override
  public void visitNode(Tree tree) {
    if (!excluded.contains(tree)) {
      super.visitNode(tree);
      if (tree.is(Tree.Kind.VARIABLE)) {
        VariableTree variableTree = (VariableTree) tree;
        boolean foundIssue = checkForbiddenTypes(tree, (AbstractTypedTree) variableTree.type());
        if (foundIssue) {
          excluded.add(variableTree.initializer());
        }
      } else if (tree.is(Tree.Kind.METHOD_INVOCATION)) {
        checkForbiddenTypes(tree, (MethodInvocationTreeImpl) tree);
      }
    }
  }

  private boolean checkForbiddenTypes(Tree tree, AbstractTypedTree typedTree) {
    boolean foundIssue = false;
    Type symbolType = typedTree.getSymbolType();
    for (String forbiddenType : FORBIDDEN_TYPES) {
      if (symbolType.is(forbiddenType)) {
        addIssue(tree, "Remove this use of \"" + forbiddenType + "\"");
        foundIssue = true;
      }
    }
    return foundIssue;
  }

  @Override
  protected List<MethodInvocationMatcher> getMethodInvocationMatchers() {
    return ImmutableList.of(
      method("java.lang.String", "getBytes"),
      method("java.lang.String", "getBytes", "int", "int", "byte[]", "int"),
      constructor("java.lang.String", "byte[]"),
      constructor("java.lang.String", "byte[]", "int", "int"),
      method("java.io.ByteArrayOutputStream", "toString"),
      constructor("java.io.FileReader").withNoParameterConstraint(),
      constructor("java.io.FileWriter").withNoParameterConstraint(),
      constructor("java.io.InputStreamReader", "java.io.InputStream"),
      constructor("java.io.OutputStreamWriter", "java.io.OutputStream"),
      constructor("java.io.PrintStream", "java.io.File"),
      constructor("java.io.PrintStream", "java.io.OutputStream"),
      constructor("java.io.PrintStream", "java.io.OutputStream", "boolean"),
      constructor("java.io.PrintStream", "java.lang.String"),
      constructor("java.io.PrintWriter", "java.io.File"),
      constructor("java.io.PrintWriter", "java.io.OutputStream"),
      constructor("java.io.PrintWriter", "java.io.OutputStream", "boolean"),
      constructor("java.io.PrintWriter", "java.lang.String"),
      constructor("java.util.Formatter", "java.lang.String"),
      constructor("java.util.Formatter", "java.io.File"),
      constructor("java.util.Formatter", "java.io.OutputStream"),
      constructor("java.util.Scanner", "java.io.File"),
      constructor("java.util.Scanner", "java.nio.file.Path"),
      constructor("java.util.Scanner", "java.io.InputStream"));
  }

  private MethodInvocationMatcher method(String type, String methodName, String... argTypes) {
    MethodInvocationMatcher matcher = MethodInvocationMatcher.create().typeDefinition(type).name(methodName);
    for (String argType : argTypes) {
      matcher = matcher.addParameter(argType);
    }
    return matcher;
  }

  private MethodInvocationMatcher constructor(String type, String... argTypes) {
    return method(type, "<init>", argTypes);
  }

  @Override
  protected void onMethodFound(MethodInvocationTree mit) {
    MethodInvocationTreeImpl methodInvocationTreeImpl = (MethodInvocationTreeImpl) mit;
    String methodName = methodInvocationTreeImpl.getSymbol().getName();
    addIssue(mit, "Remove this use of \"" + methodName + "\"");
  }

  @Override
  protected void onConstructorFound(NewClassTree newClassTree) {
    NewClassTreeImpl newClassTreeImpl = (NewClassTreeImpl) newClassTree;
    IdentifierTree constructorIdentifier = newClassTreeImpl.getConstructorIdentifier();
    MethodSymbol constructor = (MethodSymbol) getSemanticModel().getReference(constructorIdentifier);
    List<Type> parametersTypes = constructor.getParametersTypes();
    String signature = constructor.owner().getName() + "(" + Joiner.on(',').join(parametersTypes) + ")";
    addIssue(newClassTree, "Remove this use of constructor \"" + signature + "\"");
  }

}
