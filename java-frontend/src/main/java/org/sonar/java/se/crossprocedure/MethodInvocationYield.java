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

import org.sonar.java.se.CheckerContext;
import org.sonar.java.se.ProgramState;
import org.sonar.java.se.constraint.ObjectConstraint;
import org.sonar.java.se.symbolicvalues.SymbolicValue;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;

import javax.annotation.CheckForNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class MethodInvocationYield {

  private final SymbolicValue resultValue;
  private final List<MethodInvocationConstraint> constraints = new ArrayList<>();
  private final Map<SymbolicValue, PotentialNullPointer> potentialNullPointers = new HashMap<>();

  public MethodInvocationYield(SymbolicValue resultValue) {
    this.resultValue = resultValue;
  }

  public void addConstraint(MethodInvocationConstraint constraint) {
    constraints.add(constraint);
  }

  @CheckForNull
  public ProgramState compatibleState(MethodInvocationTree mit, ProgramState programState) {
    ProgramState state = programState;
    for (MethodInvocationConstraint constraint : constraints) {
      state = constraint.getState(state, mit);
      if (state == null) {
        return null;
      }
    }
    state = state.stackValue(resultValue);
    if (isNonNullMethod(mit.symbol())) {
      return state.addConstraint(resultValue, ObjectConstraint.NOT_NULL);
    }
    return state;
  }

  private static boolean isNonNullMethod(Symbol symbol) {
    return !symbol.isUnknown() && symbol.metadata().isAnnotatedWith("javax.annotation.Nonnull");
  }

  public void loadPotentialNullPointers(List<PotentialNullPointer> convertedNPEs) {
    Map<SymbolicValue, PotentialNullPointer> localMap = new HashMap<>();
    for (PotentialNullPointer potentialNullPointer : convertedNPEs) {
      localMap.put(potentialNullPointer.getValue(), potentialNullPointer);
    }
    for (MethodInvocationConstraint constraint : constraints) {
      PotentialNullPointer potentialNullPointer = constraint.matchingNullPointer(localMap);
      if (potentialNullPointer != null) {
        potentialNullPointers.put(potentialNullPointer.getValue(), potentialNullPointer);
      }
    }
  }

  public List<String> noYieldIssues(CheckerContext context, MethodInvocationTree mit) {
    ProgramState state = context.getState();
    List<PotentialNullPointer> localNullPointers = new ArrayList<>();
    for (MethodInvocationConstraint constraint : constraints) {
      PotentialNullPointer potentialNullPointer = potentialNullPointers.get(constraint.constrainedValue());
      if (potentialNullPointer == null) {
        state = constraint.getState(state, mit);
        if (state == null) {
          return Collections.emptyList();
        }
      } else {
        localNullPointers.add(potentialNullPointer);
      }
    }
    List<String> messages = new ArrayList<>();
    for (PotentialNullPointer potentialNullPointer : localNullPointers) {
      messages.add(potentialNullPointer.issueMessage(mit));
    }
    return messages;
  }

  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder();
    buffer.append(resultValue);
    String delimiter = "\twhen: ";
    for (MethodInvocationConstraint entry : constraints) {
      buffer.append(delimiter);
      buffer.append(entry);
      delimiter = ", ";
    }
    delimiter = "\tNPE: ";
    for (Entry<SymbolicValue, PotentialNullPointer> entry : potentialNullPointers.entrySet()) {
      buffer.append(delimiter);
      buffer.append(entry.getKey());
      buffer.append(':');
      buffer.append(entry.getValue());
      delimiter = ", ";
    }
    return buffer.toString();
  }
}
