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
package org.sonar.java.checks.methods;

import com.google.common.collect.ImmutableList;
import org.sonar.java.checks.SubscriptionBaseVisitor;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.NewClassTree;
import org.sonar.plugins.java.api.tree.Tree;

import java.util.List;

public abstract class AbstractMethodDetection extends SubscriptionBaseVisitor {

  private List<MethodInvocationMatcher> matchers;

  @Override
  public List<Tree.Kind> nodesToVisit() {
    return ImmutableList.of(Tree.Kind.METHOD_INVOCATION, Tree.Kind.NEW_CLASS);
  }

  @Override
  public void visitNode(Tree tree) {
    if (hasSemantic()) {
      for (MethodInvocationMatcher invocationMatcher : matchers()) {
        checkInvocation(tree, invocationMatcher);
      }
    }
  }

  private void checkInvocation(Tree tree, MethodInvocationMatcher invocationMatcher) {
    if (tree.is(Tree.Kind.METHOD_INVOCATION)) {
      MethodInvocationTree mit = (MethodInvocationTree) tree;
      if (invocationMatcher.matches(mit, getSemanticModel())) {
        onMethodFound(mit);
      }
    } else if (tree.is(Tree.Kind.NEW_CLASS)) {
      NewClassTree newClassTree = (NewClassTree) tree;
      if (invocationMatcher.matches(newClassTree, getSemanticModel())) {
        onConstructorFound(newClassTree);
      }
    }
  }

  protected abstract List<MethodInvocationMatcher> getMethodInvocationMatchers();

  protected void onMethodFound(MethodInvocationTree mit) {
    //Do nothing by default
  }

  protected void onConstructorFound(NewClassTree newClassTree) {
    // Do nothing by default
  }

  private List<MethodInvocationMatcher> matchers() {
    if (matchers == null) {
      matchers = getMethodInvocationMatchers();
    }
    return matchers;
  }
}
