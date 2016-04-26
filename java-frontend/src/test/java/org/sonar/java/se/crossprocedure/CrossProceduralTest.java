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

import org.junit.Test;
import org.sonar.java.se.JavaCheckVerifier;
import org.sonar.java.se.checks.ConditionAlwaysTrueOrFalseCheck;
import org.sonar.java.se.checks.NullDereferenceCheck;
import org.sonar.java.se.checks.UnclosedResourcesCheck;

public class CrossProceduralTest {

  @Test
  public void test() {
    JavaCheckVerifier.verify("src/test/files/se/CrossProcedural.java", new NullDereferenceCheck(), new ConditionAlwaysTrueOrFalseCheck());
  }

  @Test
  public void noYield() {
    JavaCheckVerifier.verify("src/test/files/se/CrossProceduralNoYield.java", new NullDereferenceCheck(), new ConditionAlwaysTrueOrFalseCheck());
  }

  @Test
  public void resources() {
    JavaCheckVerifier.verify("src/test/files/se/CrossProceduralResources.java", new NullDereferenceCheck(), new ConditionAlwaysTrueOrFalseCheck(), new UnclosedResourcesCheck());
  }

  @Test
  public void exception() {
    JavaCheckVerifier.verifyNoIssue("src/test/files/se/CrossProceduralException.java", new NullDereferenceCheck(), new ConditionAlwaysTrueOrFalseCheck(),
      new UnclosedResourcesCheck());
  }

  @Test
  public void coverage() {
    JavaCheckVerifier.verifyNoIssue("src/test/files/se/CrossProceduralCoverage.java", new NullDereferenceCheck(), new ConditionAlwaysTrueOrFalseCheck(),
      new UnclosedResourcesCheck());
  }

}
