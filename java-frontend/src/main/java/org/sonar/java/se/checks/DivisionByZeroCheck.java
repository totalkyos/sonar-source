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
package org.sonar.java.se.checks;

import org.sonar.check.Rule;
import org.sonar.java.se.CheckerContext;
import org.sonar.java.se.ProgramState;
import org.sonar.java.se.constraint.Constraint;
import org.sonar.java.se.constraint.ConstraintManager;
import org.sonar.java.se.constraint.ObjectConstraint;
import org.sonar.java.se.symbolicvalues.SymbolicValue;
import org.sonar.plugins.java.api.semantic.Type;
import org.sonar.plugins.java.api.tree.AssignmentExpressionTree;
import org.sonar.plugins.java.api.tree.BinaryExpressionTree;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.LiteralTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.TypeCastTree;
import org.sonar.plugins.java.api.tree.UnaryExpressionTree;

import javax.annotation.Nullable;

import java.util.List;

@Rule(key = "S3518")
public class DivisionByZeroCheck extends SECheck {

  private enum Status {
    ZERO, NON_ZERO, UNDETERMINED;
  }

  private static class ZeroSymbolicValue extends SymbolicValue {

    private final Status initialStatus;

    public ZeroSymbolicValue(int id, Status initialStatus) {
      super(id);
      this.initialStatus = initialStatus;
    }

  }

  private static class ZeroConstraint extends ObjectConstraint {
    private ZeroConstraint(Tree syntaxNode, Status status) {
      super(false, false, syntaxNode, status);
    }

    @Override
    public ZeroConstraint inverse() {
      return new ZeroConstraint(syntaxNode(), Status.NON_ZERO);
    }

    @Override
    public boolean isInversible(@Nullable Constraint otherConstraint) {
      return hasStatus(Status.ZERO) && (otherConstraint == null || !otherConstraint.isNull());
    }
  }

  @Override
  public ProgramState checkPreStatement(CheckerContext context, Tree syntaxNode) {
    PreStatementVisitor visitor = new PreStatementVisitor(context);
    syntaxNode.accept(visitor);
    return visitor.programState;
  }

  private class PreStatementVisitor extends CheckerTreeNodeVisitor {
    private final ConstraintManager constraintManager;
    private final CheckerContext context;

    PreStatementVisitor(CheckerContext context) {
      super(context.getState());
      this.context = context;
      this.constraintManager = context.getConstraintManager();
    }

    @Override
    public void visitAssignmentExpression(AssignmentExpressionTree tree) {
      List<SymbolicValue> symbolicValues = programState.peekValues(2);
      SymbolicValue var = symbolicValues.get(0);
      SymbolicValue expr = symbolicValues.get(1);
      checkExpression(tree, var, expr);
    }

    @Override
    public void visitBinaryExpression(BinaryExpressionTree tree) {
      List<SymbolicValue> symbolicValues;
      switch (tree.kind()) {
        case MULTIPLY:
        case PLUS:
        case MINUS:
        case DIVIDE:
        case REMAINDER:
          symbolicValues = programState.peekValues(2);
          checkExpression(tree, symbolicValues.get(1), symbolicValues.get(0));
          break;
        case GREATER_THAN:
        case GREATER_THAN_OR_EQUAL_TO:
        case LESS_THAN:
        case LESS_THAN_OR_EQUAL_TO:
          symbolicValues = programState.peekValues(2);
          setAsUndetermined(symbolicValues.get(1), tree.leftOperand());
          setAsUndetermined(symbolicValues.get(0), tree.rightOperand());
          break;
        default:
          // do nothing
      }
    }

    private void setAsUndetermined(SymbolicValue sv, Tree tree) {
      programState = programState.addConstraint(sv, new ZeroConstraint(tree, Status.UNDETERMINED));
    }

    private void checkExpression(Tree tree, SymbolicValue leftOp, SymbolicValue rightOp) {
      switch (tree.kind()) {
        case MULTIPLY:
        case MULTIPLY_ASSIGNMENT:
          handleMultiply(leftOp, rightOp);
          break;
        case PLUS:
        case PLUS_ASSIGNMENT:
        case MINUS:
        case MINUS_ASSIGNMENT:
          handlePlusMinus(leftOp, rightOp);
          break;
        case DIVIDE:
        case DIVIDE_ASSIGNMENT:
        case REMAINDER:
        case REMAINDER_ASSIGNMENT:
          handleDivide(tree, leftOp, rightOp);
          break;
        default:
          // can not be reached
      }
    }

    private boolean isZero(SymbolicValue symbolicValue) {
      return hasStatus(symbolicValue, Status.ZERO);
    }

    private boolean isNonZero(SymbolicValue symbolicValue) {
      return hasStatus(symbolicValue, Status.NON_ZERO);
    }

    private boolean hasStatus(SymbolicValue symbolicValue, Status status) {
      return programState.getConstraintWithStatus(symbolicValue, status) != null;
    }

    private void handleMultiply(SymbolicValue left, SymbolicValue right) {
      boolean leftIsZero = isZero(left);
      if (leftIsZero || isZero(right)) {
        reuseSymbolicValue(leftIsZero ? left : right);
      } else if (isNonZero(left) && isNonZero(right)) {
        deferConstraint(Status.NON_ZERO);
      }
    }

    private void handlePlusMinus(SymbolicValue left, SymbolicValue right) {
      boolean leftIsZero = isZero(left);
      if (leftIsZero || isZero(right)) {
        reuseSymbolicValue(leftIsZero ? right : left);
      }
    }

    private void handleDivide(Tree tree, SymbolicValue leftOp, SymbolicValue rightOp) {
      if (isZero(rightOp)) {
        reportIssue(tree);
      } else if (isZero(leftOp)) {
        reuseSymbolicValue(leftOp);
      } else if (isNonZero(leftOp) && isNonZero(rightOp)) {
        deferConstraint(tree.is(Tree.Kind.DIVIDE, Tree.Kind.DIVIDE_ASSIGNMENT) ? Status.NON_ZERO : Status.UNDETERMINED);
      }
    }

    private void deferConstraint(Status status) {
      constraintManager.setValueFactory((id, node) -> new ZeroSymbolicValue(id, status));
    }

    private void reuseSymbolicValue(SymbolicValue sv) {
      constraintManager.setValueFactory((id, node) -> sv);
    }

    private void reportIssue(Tree tree) {
      ExpressionTree expression = getDenominator(tree);
      String operation = tree.is(Tree.Kind.REMAINDER, Tree.Kind.REMAINDER_ASSIGNMENT) ? "modulation" : "division";
      String expressionName = expression.is(Tree.Kind.IDENTIFIER) ? ("'" + ((IdentifierTree) expression).name() + "'") : "this expression";
      context.reportIssue(expression, DivisionByZeroCheck.this, "Make sure " + expressionName + " can't be zero before doing this " + operation + ".");

      // interrupt exploration
      programState = null;
    }

    private ExpressionTree getDenominator(Tree tree) {
      return tree.is(Tree.Kind.DIVIDE, Tree.Kind.REMAINDER) ? ((BinaryExpressionTree) tree).rightOperand() : ((AssignmentExpressionTree) tree).expression();
    }

    @Override
    public void visitTypeCast(TypeCastTree tree) {
      Type type = tree.type().symbolType();
      if (type.isPrimitive()) {
        SymbolicValue sv = programState.peekValue();
        if (isZero(sv)) {
          reuseSymbolicValue(sv);
        } else if (isNonZero(sv)) {
          deferConstraint(Status.NON_ZERO);
        }
      }
    }

    @Override
    public void visitUnaryExpression(UnaryExpressionTree tree) {
      if (!tree.is(Tree.Kind.LOGICAL_COMPLEMENT)) {
        SymbolicValue sv = programState.peekValue();
        if (isZero(sv)) {
          if (tree.is(Tree.Kind.UNARY_MINUS, Tree.Kind.UNARY_PLUS)) {
            reuseSymbolicValue(sv);
          } else {
            deferConstraint(Status.NON_ZERO);
          }
        } else {
          deferConstraint(Status.UNDETERMINED);
        }
      }
    }
  }

  @Override
  public ProgramState checkPostStatement(CheckerContext context, Tree syntaxNode) {
    PostStatementVisitor visitor = new PostStatementVisitor(context);
    syntaxNode.accept(visitor);
    return visitor.programState;
  }

  private static class PostStatementVisitor extends CheckerTreeNodeVisitor {

    PostStatementVisitor(CheckerContext context) {
      super(context.getState());
    }

    @Override
    public void visitLiteral(LiteralTree tree) {
      if (tree.is(Tree.Kind.INT_LITERAL, Tree.Kind.LONG_LITERAL, Tree.Kind.DOUBLE_LITERAL, Tree.Kind.FLOAT_LITERAL, Tree.Kind.CHAR_LITERAL)) {
        SymbolicValue sv = programState.peekValue();
        ZeroConstraint constraint = isNumberZero(tree.value()) ? new ZeroConstraint(tree, Status.ZERO) : new ZeroConstraint(tree, Status.NON_ZERO);
        programState = programState.addConstraint(sv, constraint);
      }
    }


    private static boolean isNumberZero(String literalValue) {
      return isNullCharacter(literalValue)
        || !(literalValue.matches("(.)*[1-9]+(.)*") || literalValue.matches("(0x|0X){1}(.)*[1-9a-fA-F]+(.)*") || literalValue.matches("(0b|0B){1}(.)*[1]+(.)*"));
    }

    private static boolean isNullCharacter(String literalValue) {
      return "'\0'".equals(literalValue) || "'\u0000'".equals(literalValue);
    }


    @Override
    public void visitBinaryExpression(BinaryExpressionTree tree) {
      checkDeferredConstraint(tree);
    }

    @Override
    public void visitAssignmentExpression(AssignmentExpressionTree tree) {
      checkDeferredConstraint(tree);
    }

    @Override
    public void visitUnaryExpression(UnaryExpressionTree tree) {
      checkDeferredConstraint(tree);
    }

    @Override
    public void visitTypeCast(TypeCastTree tree) {
      checkDeferredConstraint(tree);
    }

    private void checkDeferredConstraint(Tree tree) {
      SymbolicValue sv = programState.peekValue();
      if (sv instanceof ZeroSymbolicValue) {
        programState = programState.addConstraint(sv, new ZeroConstraint(tree, ((ZeroSymbolicValue) sv).initialStatus));
      }
    }
  }
}
