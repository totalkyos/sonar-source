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
package org.sonar.java.se.crossprocedure;

import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import org.sonar.java.resolve.Symbols;
import org.sonar.java.se.ProgramState;
import org.sonar.java.se.constraint.BooleanConstraint;
import org.sonar.java.se.constraint.Constraint;
import org.sonar.java.se.constraint.ConstraintManager;
import org.sonar.java.se.constraint.ObjectConstraint;
import org.sonar.java.se.symbolicvalues.RelationalSymbolicValue;
import org.sonar.java.se.symbolicvalues.SymbolicExceptionValue;
import org.sonar.java.se.symbolicvalues.SymbolicValue;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.tree.MemberSelectExpressionTree;
import org.sonar.plugins.java.api.tree.MethodTree;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class MethodBehavior {

  private final Symbol methodSymbol;
  private final List<Symbol> parameters = new ArrayList<>();
  private final List<SymbolicValue> parameterValues = new ArrayList<>();
  private final List<MethodYield> yields = new ArrayList<>();
  private final List<PotentialNullPointer> potentialNullPointers = new ArrayList<>();
  private SymbolicValue methodResult;

  public MethodBehavior(Symbol symbol) {
    methodSymbol = symbol;
  }

  public MethodBehavior() {
    // Needed for EGW tests
    methodSymbol = Symbols.unknownMethodSymbol;
  }

  public void addYield(ProgramState programState, ConstraintManager constraintManager) {
    if (methodSymbol.isUnknown()) {
      return;
    }
    ProgramState.Pop pop = programState.unstackValue(1);
    ProgramState state = pop.state;
    SymbolicValue result = pop.values.get(0);
    if (SymbolicValue.NULL_LITERAL.equals(result)) {
      state = createResult(state, constraintManager, new ObjectConstraint(true, false, methodSymbol.declaration(), null));
    } else if (SymbolicValue.TRUE_LITERAL.equals(result)) {
      state = createResult(state, constraintManager, BooleanConstraint.TRUE);
    } else if (SymbolicValue.FALSE_LITERAL.equals(result)) {
      state = createResult(state, constraintManager, BooleanConstraint.FALSE);
    } else if (result instanceof SymbolicExceptionValue || parameterValues.contains(result)) {
      state = programState;
    } else if (result instanceof RelationalSymbolicValue) {
      state = state.removeConstraint(result);
      state = createResult(state, constraintManager, BooleanConstraint.TRUE);
      state = state.addConstraint(result, BooleanConstraint.TRUE);
      ProgramState falseState = state.addConstraint(methodResult, BooleanConstraint.FALSE);
      falseState = falseState.addConstraint(result, BooleanConstraint.FALSE);
      addVoidYield(falseState);
    } else if (methodResult == null) {
      methodResult = result;
      state = programState;
    } else {
      Constraint constraint = state.getConstraint(result);
      state = state.removeConstraint(result);
      state = createResult(state, constraintManager, constraint);
    }
    addVoidYield(state);
  }

  public void addVoidYield(ProgramState programState) {
    MethodYield newYield = new MethodYield(this, programState);
    for (MethodYield yield : yields) {
      if (yield.equivalentTo(newYield)) {
        return;
      }
    }
    yields.add(newYield);
  }

  @Nonnull
  private ProgramState createResult(ProgramState programState, ConstraintManager constraintManager, @Nullable Constraint objectConstraint) {
    if (methodResult == null) {
      methodResult = constraintManager.createSymbolicValue(methodSymbol.declaration());
    }
    ProgramState state = programState.stackValue(methodResult);
    return objectConstraint == null ? state : state.addConstraint(methodResult, objectConstraint);
  }

  public void addParameter(Symbol symbol, SymbolicValue sv) {
    parameters.add(symbol);
    parameterValues.add(sv);
  }

  public Set<Symbol> symbolSet() {
    return new HashSet<>(parameters);
  }

  Set<SymbolicValue> parameterSet() {
    return new HashSet<>(parameterValues);
  }

  public void pruneYields() {
    Set<SymbolicValue> values = new HashSet<>();
    for (MethodYield yield : yields) {
      values.addAll(yield.constrainedValues());
    }
    for (SymbolicValue value : values) {
      pruneYields(value);
    }
  }

  private void pruneYields(SymbolicValue value) {
    ListMultimap<Object, MethodYield> map = LinkedListMultimap.create();
    for (MethodYield yield : yields) {
      map.put(yield.constraintsWithout(value), yield);
    }
    yields.clear();
    Set<Object> keys = new HashSet<>(map.keySet());
    for (Object key : keys) {
      yields.addAll(pruneYields(value, map.get(key)));
    }
  }

  private Collection<MethodYield> pruneYields(SymbolicValue value, List<MethodYield> list) {
    List<MethodYield> result = new ArrayList<>();
    while (!list.isEmpty()) {
      MethodYield yield = list.remove(0);
      Constraint constraint = yield.constraint(value);
      for (Iterator<MethodYield> iterator = list.iterator(); iterator.hasNext();) {
        MethodYield methodYield = iterator.next();
        Constraint oredConstraint = ObjectConstraint.or(constraint, methodYield.constraint(value));
        if (!ObjectConstraint.CONFLICT.equals(oredConstraint)) {
          constraint = oredConstraint;
          iterator.remove();
        }
      }
      yield.setConstraint(value, constraint);
      result.add(yield);
    }
    return result;
  }

  public List<MethodInvocationYield> invocationYields(List<SymbolicValue> values, SymbolicValue resultValue, ConstraintManager constraintManager, MethodBehavior callingMethod) {
    ParameterValueAdapter adapter = new ParameterValueAdapter(parameterValues, values, constraintManager);
    List<PotentialNullPointer> convertedNPEs = new ArrayList<>(potentialNullPointers.size());
    for (PotentialNullPointer potentialNullPointer : potentialNullPointers) {
      PotentialNullPointer convertedPointer = potentialNullPointer.converted(adapter);
      convertedNPEs.add(convertedPointer);
      if (callingMethod.parameterValues.contains(convertedPointer.getValue())) {
        callingMethod.potentialNullPointers.add(convertedPointer);
      }
    }
    ArrayList<MethodInvocationYield> allowedYields = new ArrayList<>();
    for (MethodYield yield : yields) {
      MethodInvocationYield invocationYield = yield.asInvocationYield(adapter, resultValue);
      invocationYield.loadPotentialNullPointers(convertedNPEs);
      allowedYields.add(invocationYield);
    }
    return allowedYields;
  }

  public void notifyPotentialNullPointer(SymbolicValue value, MemberSelectExpressionTree syntaxNode) {
    int index = parameterValues.indexOf(value);
    if (index >= 0) {
      potentialNullPointers.add(new PotentialNullPointer(methodSymbol, value, syntaxNode, index + 1));
    }
  }

  public boolean hasBeenProcessed() {
    MethodTree tree = (MethodTree) methodSymbol.declaration();
    return tree.block() != null;
  }

  public boolean isConstructor() {
    return methodSymbol != null && "<init>".equals(methodSymbol.name());
  }

  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder();
    buffer.append("Method ");
    buffer.append(methodSymbol.name());
    buffer.append("\nParameters");
    String delimiter = ": ";
    Iterator<SymbolicValue> iterator = parameterValues.iterator();
    for (Symbol parameter : parameters) {
      buffer.append(delimiter);
      buffer.append(parameter.name());
      buffer.append('(');
      buffer.append(iterator.next());
      buffer.append(')');
      delimiter = ", ";
    }
    for (MethodYield programState : yields) {
      buffer.append("\n\t- ");
      buffer.append(programState);
    }
    return buffer.toString();
  }
}
