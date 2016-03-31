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
package org.sonar.java.se.symbolicvalues;

import com.google.common.collect.Lists;
import org.junit.Test;
import org.sonar.java.se.ProgramState;
import org.sonar.java.se.constraint.BooleanConstraint;
import org.sonar.java.se.constraint.Constraint;
import org.sonar.java.se.symbolicvalues.RelationalSymbolicValue.Kind;

import java.util.HashMap;
import java.util.Map;

import static org.fest.assertions.Assertions.assertThat;

public class RelationalSymbolicValueTest {

  private static int counter = 0;

  private static RelationalSymbolicValue relation(Kind kind, SymbolicValue a, SymbolicValue b) {
    RelationalSymbolicValue value = new RelationalSymbolicValue(counter, kind);
    counter += 1;
    value.computedFrom(Lists.newArrayList(b, a));
    return value;
  }

  private static SymbolicValue symbolicValue() {
    return new SymbolicValue(counter++);
  }

  @Test
  public void testAtomic() {
    counter = 0;
    ProgramState state = ProgramState.EMPTY_STATE;
    SymbolicValue svA = symbolicValue();
    RelationalSymbolicValue rA = relation(RelationalSymbolicValue.Kind.EQUAL, svA, SymbolicValue.NULL_LITERAL);
    state = state.addConstraint(rA, BooleanConstraint.TRUE);
    SymbolicValue svB = symbolicValue();
    RelationalSymbolicValue rB = relation(RelationalSymbolicValue.Kind.NOT_EQUAL, svB, SymbolicValue.NULL_LITERAL);
    state = state.addConstraint(rB, BooleanConstraint.TRUE);
    SymbolicValue svC = symbolicValue();
    RelationalSymbolicValue rC = relation(RelationalSymbolicValue.Kind.EQUAL, svC, SymbolicValue.NULL_LITERAL);
    state = state.addConstraint(rC, BooleanConstraint.FALSE);
    SymbolicValue svD = symbolicValue();
    RelationalSymbolicValue rD = relation(RelationalSymbolicValue.Kind.NOT_EQUAL, svD, SymbolicValue.NULL_LITERAL);
    state = state.addConstraint(rD, BooleanConstraint.FALSE);
    SymbolicValue svE = symbolicValue();
    RelationalSymbolicValue rE = relation(RelationalSymbolicValue.Kind.GREATER_THAN, svE, SymbolicValue.NULL_LITERAL);
    state = state.addConstraint(rE, BooleanConstraint.FALSE);
    Map<SymbolicValue, Constraint> constraints = new HashMap<>();
    RelationalSymbolicValue.AtomicConstraint atomic = rA.convertToAtomic(state.getConstraint(rA));
    assertThat(atomic).isNotNull();
    assertThat(atomic.isNull()).isTrue();
    atomic = rB.convertToAtomic(state.getConstraint(rB));
    assertThat(atomic).isNotNull();
    assertThat(atomic.isNull()).isFalse();
    atomic = rC.convertToAtomic(state.getConstraint(rC));
    assertThat(atomic).isNotNull();
    assertThat(atomic.isNull()).isFalse();
    atomic = rD.convertToAtomic(state.getConstraint(rD));
    assertThat(atomic).isNotNull();
    assertThat(atomic.isNull()).isTrue();
    assertThat(rE.convertToAtomic(state.getConstraint(rE))).isNull();

    state = ProgramState.EMPTY_STATE;
    rA = relation(RelationalSymbolicValue.Kind.EQUAL, SymbolicValue.NULL_LITERAL, svA);
    state = state.addConstraint(rA, BooleanConstraint.TRUE);
    rB = relation(RelationalSymbolicValue.Kind.NOT_EQUAL, SymbolicValue.NULL_LITERAL, svB);
    state = state.addConstraint(rB, BooleanConstraint.TRUE);
    rC = relation(RelationalSymbolicValue.Kind.EQUAL, SymbolicValue.NULL_LITERAL, svC);
    state = state.addConstraint(rC, BooleanConstraint.FALSE);
    rD = relation(RelationalSymbolicValue.Kind.NOT_EQUAL, SymbolicValue.NULL_LITERAL, svD);
    state = state.addConstraint(rD, BooleanConstraint.FALSE);
    constraints.clear();

    atomic = rA.convertToAtomic(state.getConstraint(rA));
    assertThat(atomic).isNotNull();
    assertThat(atomic.isNull()).isTrue();
    atomic = rB.convertToAtomic(state.getConstraint(rB));
    assertThat(atomic).isNotNull();
    assertThat(atomic.isNull()).isFalse();
    atomic = rC.convertToAtomic(state.getConstraint(rC));
    assertThat(atomic).isNotNull();
    assertThat(atomic.isNull()).isFalse();
    atomic = rD.convertToAtomic(state.getConstraint(rD));
    assertThat(atomic).isNotNull();
    assertThat(atomic.isNull()).isTrue();
  }
}
