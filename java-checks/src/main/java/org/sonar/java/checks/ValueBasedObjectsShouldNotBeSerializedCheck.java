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
package org.sonar.java.checks;

import java.util.Arrays;
import java.util.List;
import org.sonar.check.Rule;
import org.sonar.java.model.ModifiersUtils;
import org.sonar.plugins.java.api.JavaFileScanner;
import org.sonar.plugins.java.api.JavaFileScannerContext;
import org.sonar.plugins.java.api.semantic.Type;
import org.sonar.plugins.java.api.tree.BaseTreeVisitor;
import org.sonar.plugins.java.api.tree.ClassTree;
import org.sonar.plugins.java.api.tree.Modifier;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.VariableTree;

@Rule(key = "S3437")
public class ValueBasedObjectsShouldNotBeSerializedCheck extends BaseTreeVisitor implements JavaFileScanner {

  private JavaFileScannerContext context;

  @Override
  public void scanFile(JavaFileScannerContext context) {
    this.context = context;
    scan(context.getTree());
  }

  @Override
  public void visitClass(ClassTree clas) {
    if (isSerializable(clas)) {
      for (Tree member : clas.members()) {
        if (member.is(Tree.Kind.VARIABLE) && !isTransient((VariableTree) member) && isValueBased((VariableTree) member)) {
          context.reportIssue(this, ((VariableTree) member).simpleName(), "Make this value-based field transient so it is not included in the serialization of this class.");
        }
      }
    }

    super.visitClass(clas);
  }

  private static boolean isSerializable(ClassTree clas) {
    return clas.symbol().type().isSubtypeOf("java.io.Serializable");
  }

  private static boolean isTransient(VariableTree variable) {
    return ModifiersUtils.hasModifier(variable.modifiers(), Modifier.TRANSIENT);
  }

  // A FOUTRE DANS UNE CLASSE SEPAREE TYPEuTILS 
  private static boolean isValueBased(VariableTree variable) {
    List<String> KNOWN_VALUE_BASED_CLASSES = Arrays.asList(
      "java.time.chrono.HijrahDate", 
      "java.time.chrono.JapaneseDate", 
      "java.time.chrono.MinguoDate", 
      "java.time.chrono.ThaiBuddhistDate",
      "java.util.Optional",
      "java.util.DoubleOptional",
      "java.util.IntOptional",
      "java.util.LongOptional");
    
    Type st = variable.type().symbolType();
    String className = st.fullyQualifiedName();
    return (KNOWN_VALUE_BASED_CLASSES.contains(className) || isInJavaTimePackage(className)) && !"java.time.Clock".equals(className);
  }
  
  private static boolean isInJavaTimePackage(String className) {
    return className.substring(0, className.lastIndexOf(".")).equals("java.time");
  }

}
