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
import org.sonar.java.se.constraint.BooleanConstraint;
import org.sonar.java.se.constraint.Constraint;
import org.sonar.java.se.symbolicvalues.RelationalSymbolicValue;
import org.sonar.java.se.symbolicvalues.RelationalSymbolicValue.AtomicConstraint;
import org.sonar.java.se.symbolicvalues.SymbolicValue;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;

import java.util.Map;

public class MethodInvocationConstraint {

  private final SymbolicValue constrainedValue;
  private final Constraint yieldConstraint;
  private final AtomicConstraint atomicConstraint;
  private final RelationalSymbolicValue relationValue;

  public MethodInvocationConstraint(SymbolicValue constrainedValue, Constraint yieldConstraint) {
    this.constrainedValue = constrainedValue;
    this.yieldConstraint = yieldConstraint;
    if (constrainedValue instanceof RelationalSymbolicValue) {
      relationValue = (RelationalSymbolicValue) constrainedValue;
      atomicConstraint = relationValue.convertToAtomic(yieldConstraint);
    } else {
      relationValue = null;
      atomicConstraint = new AtomicConstraint(constrainedValue, yieldConstraint);
    }
  }

  public SymbolicValue constrainedValue() {
    return constrainedValue;
  }

  ProgramState getState(ProgramState state, MethodInvocationTree mit) {
    if (atomicConstraint == null) {
      if (relationValue.checkRelation((BooleanConstraint) yieldConstraint, state) == null) {
        return null;
      }
      return state.addConstraint(constrainedValue, yieldConstraint);
    } else {
      return atomicConstraint.exitMethodInto(state, mit);
    }
  }

  public PotentialNullPointer matchingNullPointer(Map<SymbolicValue, PotentialNullPointer> localMap) {
    if (atomicConstraint != null && !atomicConstraint.isNull()) {
      return localMap.get(atomicConstraint.getValue());
    }
    return null;
  }
}
