/*
 * SonarQube Java
 * Copyright (C) 2012-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.java.se;

import com.google.common.collect.Lists;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;
import org.sonar.java.ast.visitors.SubscriptionVisitor;
import org.sonar.java.se.crossprocedure.MethodBehavior;
import org.sonar.java.se.crossprocedure.MethodBehaviorRoster;
import org.sonar.java.se.symbolicvalues.BinaryRelation;
import org.sonar.plugins.java.api.JavaFileScanner;
import org.sonar.plugins.java.api.JavaFileScannerContext;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.Tree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SymbolicExecutionVisitor extends SubscriptionVisitor implements MethodBehaviorRoster {
  static final Logger LOG = Loggers.get(SymbolicExecutionVisitor.class);

  private final ExplodedGraphWalker.ExplodedGraphWalkerFactory egwFactory;
  private final List<Symbol> itemsToProcess = new ArrayList<>();
  private static final Map<Symbol, MethodBehavior> behaviors = new HashMap<>();

  public SymbolicExecutionVisitor(List<JavaFileScanner> executableScanners) {
    egwFactory = new ExplodedGraphWalker.ExplodedGraphWalkerFactory(executableScanners);
  }

  @Override
  public List<Tree.Kind> nodesToVisit() {
    return Lists.newArrayList(Tree.Kind.METHOD, Tree.Kind.CONSTRUCTOR);
  }

  @Override
  public void visitNode(Tree tree) {
    Symbol symbol = ((MethodTree) tree).symbol();
    itemsToProcess.add(symbol);
  }

  @Override
  public void scanFile(JavaFileScannerContext context) {
    super.scanFile(context);
    while (!itemsToProcess.isEmpty()) {
      Symbol symbol = itemsToProcess.remove(0);
      process(symbol);
    }
  }

  private void process(Symbol symbol) {
    Tree tree = symbol.declaration();
    try {
      MethodBehavior behavior = new MethodBehavior(symbol);
      ExplodedGraphWalker walker = egwFactory.createWalker(behavior, this);
      tree.accept(walker);
      if (behavior.hasBeenProcessed()) {
        behavior.pruneYields();
        behaviors.put(symbol, behavior);
      }
    } catch (ExplodedGraphWalker.MaximumStepsReachedException | ExplodedGraphWalker.ExplodedGraphTooBigException
      | BinaryRelation.TransitiveRelationExceededException exception) {
      LOG.debug("Could not complete symbolic execution: ", exception);
    }
  }

  @Override
  public MethodBehavior getReference(Symbol symbol) {
    if (itemsToProcess.remove(symbol) && !symbol.isAbstract()) {
      process(symbol);
    }
    return behaviors.get(symbol);
  }
}
