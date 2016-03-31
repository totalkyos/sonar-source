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

import org.sonar.java.se.constraint.ConstraintManager;
import org.sonar.java.se.symbolicvalues.SymbolicValue;
import org.sonar.java.se.symbolicvalues.SymbolicValueAdapter;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class ParameterValueAdapter implements SymbolicValueAdapter {
  
  private final Map<SymbolicValue, SymbolicValue> argumentMap = new HashMap<>();
  private final ConstraintManager constraintManager;

  public ParameterValueAdapter(List<SymbolicValue> parameterValues, List<SymbolicValue> argumentValues, ConstraintManager constraintManager) {
    this.constraintManager = constraintManager;
    Iterator<SymbolicValue> iterator = parameterValues.iterator();
    for (SymbolicValue argument : argumentValues) {
      if (!iterator.hasNext()) {
        // Variable argument list declared
        break;
      }
      argumentMap.put(iterator.next(), argument);
    }
  }

  @Override
  public SymbolicValue convert(SymbolicValue value) {
    if (SymbolicValue.PROTECTED_SYMBOLIC_VALUES.contains(value)) {
      return value;
    }
    return argumentMap.get(value);
  }

  public SymbolicValue convertValue(SymbolicValue value) {
    SymbolicValue converted = convert(value);
    return converted == null ? constraintManager.convert(value, this) : converted;
  }

  public void setResultValue(SymbolicValue result, SymbolicValue resultValue) {
    argumentMap.put(result, resultValue);
  }

  public void removeResultValue(SymbolicValue result) {
    argumentMap.remove(result);
  }
}
