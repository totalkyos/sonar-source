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

import com.google.common.collect.ImmutableList;
import org.sonar.java.se.ProgramState;
import org.sonar.java.se.constraint.BooleanConstraint;
import org.sonar.java.se.constraint.Constraint;
import org.sonar.java.se.symbolicvalues.BinaryRelation;
import org.sonar.java.se.symbolicvalues.RelationalSymbolicValue;
import org.sonar.java.se.symbolicvalues.SymbolicExceptionValue;
import org.sonar.java.se.symbolicvalues.SymbolicValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

public class MethodYield {

  private final Map<SymbolicValue, Constraint> constraints = new HashMap<>();
  final SymbolicValue result;
  private final boolean unknownResult;

  public MethodYield(MethodBehavior crossProceduralReference, ProgramState state, SymbolicValue methodResult) {
    SymbolicValue actualResult = state.peekValue();
    Set<SymbolicValue> valueSet = crossProceduralReference.parameterSet();
    boolean keepResult;
    if (SymbolicValue.PROTECTED_SYMBOLIC_VALUES.contains(actualResult)) {
      unknownResult = false;
      keepResult = true;
    } else {
      unknownResult = valueSet.add(actualResult);
      keepResult = !unknownResult || (methodResult == null) || (actualResult instanceof SymbolicExceptionValue);
    }
    List<BinaryRelation> relations = new ArrayList<>();
    for (SymbolicValue value : state.getConstrainedValues()) {
      Constraint constraint = state.getConstraint(value);
      if (valueSet.contains(value)) {
        constraints.put(value, constraint);
      } else if (value instanceof RelationalSymbolicValue) {
        RelationalSymbolicValue relationalValue = (RelationalSymbolicValue) value;
        RelationalSymbolicValue.AtomicConstraint atomic = relationalValue.convertToAtomic(state.getConstraint(value));
        if (atomic == null) {
          relations.add(createRelation(relationalValue, constraint));
        } else {
          atomic.storeInto(constraints);
        }
      }
    }
    reduceRelations(relations, valueSet);
    for (BinaryRelation relation : relations) {
      constraints.put(relation.asValue(), BooleanConstraint.TRUE);
    }
    if (keepResult) {
      result = actualResult;
    } else {
      convertResultConstraints(methodResult, actualResult);
      result = methodResult;
    }
  }

  private void convertResultConstraints(SymbolicValue newValue, SymbolicValue oldValue) {
    Constraint resultConstraint = constraints.remove(oldValue);
    if (resultConstraint != null) {
      constraints.put(newValue, resultConstraint);
    }
    for (SymbolicValue value : constraints.keySet()) {
      if (value instanceof RelationalSymbolicValue) {
        ((RelationalSymbolicValue) value).exchange(oldValue, newValue);
      }
    }
  }

  private BinaryRelation createRelation(RelationalSymbolicValue relationalValue, Constraint constraint) {
    BinaryRelation relation = relationalValue.binaryRelation();
    if (BooleanConstraint.TRUE.equals(constraint)) {
      return relation;
    }
    return relation.inverse();
  }

  private void reduceRelations(List<BinaryRelation> relations, Set<SymbolicValue> valueSet) {
    BinaryRelation relation = removeRelationWithUnusedValue(relations, valueSet);
    while (relation != null) {
      removeUnusedValue(relation, relations);
      relation = removeRelationWithUnusedValue(relations, valueSet);
    }
  }

  private BinaryRelation removeRelationWithUnusedValue(List<BinaryRelation> relations, Set<SymbolicValue> valueSet) {
    for (Iterator<BinaryRelation> iterator = relations.iterator(); iterator.hasNext();) {
      BinaryRelation relation = iterator.next();
      if (!valueSet.contains(relation.leftOp()) || !valueSet.contains(relation.rightOp())) {
        iterator.remove();
        return relation;
      }
    }
    return null;
  }

  private void removeUnusedValue(BinaryRelation relationToRemove, List<BinaryRelation> relations) {
    for (int i = 0; i < relations.size(); i++) {
      BinaryRelation relation = relations.get(i);
      BinaryRelation combination = relationToRemove.combineUnordered(relation);
      if (combination != null && !combination.leftOp().equals(combination.rightOp())) {
        relations.set(i, combination);
      }
    }
  }

  public MethodInvocationYield asInvocationYield(ParameterValueAdapter adapter, SymbolicValue resultValue) {
    if (result instanceof SymbolicExceptionValue) {
      adapter.setResultValue(result, adapter.convertValue(result));
    } else if (unknownResult) {
      adapter.setResultValue(result, resultValue);
    }
    MethodInvocationYield invocationYield = new MethodInvocationYield(adapter.convertValue(result));
    for (Map.Entry<SymbolicValue, Constraint> entry : constraints.entrySet()) {
      invocationYield.addConstraint(new MethodInvocationConstraint(adapter.convertValue(entry.getKey()), entry.getValue()));
    }
    if (unknownResult) {
      adapter.removeResultValue(result);
    }
    return invocationYield;
  }

  public boolean equivalentTo(MethodYield yield) {
    if (!Objects.equals(result, yield.result)) {
      return false;
    }
    Map<SymbolicValue, Constraint> myConstraints = new HashMap<>(constraints);
    Map<SymbolicValue, Constraint> itsConstraints = new HashMap<>(yield.constraints);
    if (!Objects.equals(myConstraints.remove(result), itsConstraints.remove(yield.result))) {
      return false;
    }
    for (Entry<SymbolicValue, Constraint> myEntry : myConstraints.entrySet()) {
      if (!myEntry.getValue().equals(itsConstraints.remove(myEntry.getKey()))) {
        return false;
      }
    }
    return itsConstraints.isEmpty();
  }

  SymbolicValue genericResult() {
    if (result instanceof SymbolicExceptionValue) {
      return null;
    }
    return unknownResult ? result : null;
  }

  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder();
    buffer.append(result);
    String delimiter = "\twhen: ";
    for (Map.Entry<SymbolicValue, Constraint> entry : constraints.entrySet()) {
      buffer.append(delimiter);
      buffer.append(entry.getKey());
      buffer.append("->");
      buffer.append(entry.getValue());
      delimiter = ", ";
    }
    return buffer.toString();
  }

  Object constraintsWithout(SymbolicValue value) {
    Map<SymbolicValue, Constraint> map = new HashMap<>(constraints);
    map.remove(value);
    return result == null ? map : ImmutableList.<Object>of(result, map);
  }

  Constraint constraint(SymbolicValue value) {
    return constraints.get(value);
  }

  public void setConstraint(SymbolicValue value, Constraint constraint) {
    if (constraint == null) {
      constraints.remove(value);
    } else {
      constraints.put(value, constraint);
    }
  }

  public Collection<? extends SymbolicValue> constrainedValues() {
    return new ArrayList<>(constraints.keySet());
  }
}
