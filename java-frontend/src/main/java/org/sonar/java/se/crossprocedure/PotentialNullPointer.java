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

import org.sonar.java.model.JavaTree;
import org.sonar.java.se.symbolicvalues.SymbolicValue;
import org.sonar.plugins.java.api.tree.MemberSelectExpressionTree;

public class PotentialNullPointer {

  private final int index;
  private final SymbolicValue value;
  private final MemberSelectExpressionTree syntaxNode;

  public PotentialNullPointer(SymbolicValue value, MemberSelectExpressionTree syntaxNode, int index) {
    this.index = index;
    this.value = value;
    this.syntaxNode = syntaxNode;
  }

  public Integer getIndex() {
    return Integer.valueOf(index);
  }

  public SymbolicValue getValue() {
    return value;
  }

  public Integer getErrorLine() {
    return Integer.valueOf(((JavaTree) syntaxNode).getLine());
  }

  public PotentialNullPointer converted(ParameterValueAdapter adapter) {
    return new PotentialNullPointer(adapter.convert(value), syntaxNode, index);
  }

  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder();
    buffer.append("NPE(");
    buffer.append(index);
    buffer.append("): ");
    buffer.append(value);
    buffer.append(" -> line ");
    buffer.append(getErrorLine());
    return buffer.toString();
  }
}
