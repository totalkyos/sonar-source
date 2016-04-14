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

import org.sonar.java.se.ProgramState;
import org.sonar.java.se.constraint.ConstraintManager;
import org.sonar.java.se.symbolicvalues.SymbolicValue;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.tree.MemberSelectExpressionTree;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class MethodBehavior {

  private Symbol methodSymbol = null;
  private final List<Symbol> parameters = new ArrayList<>();
  private final List<SymbolicValue> parameterValues = new ArrayList<>();
  private final List<MethodYield> yields = new ArrayList<>();
  private final List<PotentialNullPointer> potentialNullPointers = new ArrayList<>();

  public void setMethodSymbol(Symbol symbol) {
    methodSymbol = symbol;
  }

  public Symbol getMethodSymbol() {
    return methodSymbol;
  }

  public void addYield(ProgramState state) {
    yields.add(new MethodYield(this, state));
  }

  public void addParameter(Symbol symbol, SymbolicValue sv) {
    parameters.add(symbol);
    parameterValues.add(sv);
  }

  public Iterable<SymbolicValue> parameterValues() {
    return parameterValues;
  }

  public Set<Symbol> symbolSet() {
    return new HashSet<>(parameters);
  }

  public List<SymbolicValue> parameterValues(ProgramState state) {
    List<SymbolicValue> values = new ArrayList<>();
    for (Symbol symbol : parameters) {
      if (!symbol.isVariableSymbol()) {
        values.add(state.getValue(symbol));
      }
    }
    return values;
  }

  public boolean hasYield() {
    return !yields.isEmpty();
  }

  Set<SymbolicValue> parameterSet() {
    return new HashSet<>(parameterValues);
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

  public void notifyPotentialNullPointer(SymbolicValue value, MemberSelectExpressionTree syntaxNode) {
    int index = parameterValues.indexOf(value);
    if (index >= 0) {
      potentialNullPointers.add(new PotentialNullPointer(methodSymbol, value, syntaxNode, index + 1));
    }
  }
}
