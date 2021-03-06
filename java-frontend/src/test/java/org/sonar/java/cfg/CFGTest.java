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
package org.sonar.java.cfg;

import com.google.common.base.Charsets;
import com.sonar.sslr.api.typed.ActionParser;
import org.junit.Test;
import org.sonar.java.ast.parser.JavaParser;
import org.sonar.java.cfg.CFG.Block;
import org.sonar.java.model.CFGDebug;
import org.sonar.plugins.java.api.tree.ClassTree;
import org.sonar.plugins.java.api.tree.CompilationUnitTree;
import org.sonar.plugins.java.api.tree.IdentifierTree;
import org.sonar.plugins.java.api.tree.LiteralTree;
import org.sonar.plugins.java.api.tree.MemberSelectExpressionTree;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.Tree.Kind;
import org.sonar.plugins.java.api.tree.VariableTree;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.fest.assertions.Assertions.assertThat;

public class CFGTest {

  static CFGChecker checker(BlockChecker... checkers) {
    return new CFGChecker(checkers);
  }

  static BlockChecker block(final ElementChecker... checkers) {
    return new BlockChecker(checkers);
  }

  static BlockChecker terminator(final Tree.Kind kind, final int... successorIDs) {
    return new BlockChecker(kind, successorIDs);
  }

  static ElementChecker element(final Tree.Kind kind) {
    return new ElementChecker(kind);
  }

  static ElementChecker element(final Tree.Kind kind, final String name) {
    return new ElementChecker(kind, name);
  }

  static ElementChecker element(final Tree.Kind kind, final int value) {
    return new ElementChecker(kind, value);
  }

  private static class CFGChecker {

    private final List<BlockChecker> checkers = new ArrayList<>();

    CFGChecker(BlockChecker... checkers) {
      Collections.addAll(this.checkers, checkers);
    }

    public void check(final CFG cfg) {
      try {
        assertThat(cfg.blocks()).as("Expected number of blocks").hasSize(checkers.size() + 1);
        final Iterator<BlockChecker> checkerIterator = checkers.iterator();
        final List<Block> blocks = new ArrayList<>(cfg.blocks());
        final Block exitBlock = blocks.remove(blocks.size() - 1);
        for (final Block block : blocks) {
          checkerIterator.next().check(block);
          checkLinkedBlocks(block.id(), "Successor", cfg.blocks(), block.successors());
          checkLinkedBlocks(block.id(), "Predecessors", cfg.blocks(), block.predecessors());
        }
        assertThat(exitBlock.elements()).isEmpty();
        assertThat(exitBlock.successors()).isEmpty();
        assertThat(cfg.blocks()).as("CFG entry block is no longer in the list of blocks!").contains(cfg.entry());
      } catch (final Throwable e) {
        System.out.println(CFGDebug.toString(cfg));
        throw e;
      }
    }

    private void checkLinkedBlocks(int id, String type, List<Block> blocks, Set<Block> linkedBlocks) {
      for (Block block : linkedBlocks) {
        assertThat(block).as(type + " block " + id + " is missing from he list of blocks").isIn(blocks);
      }
    }
  }

  private static class BlockChecker {

    private int[] successorIDs = new int[] {};
    private final List<ElementChecker> checkers = new ArrayList<>();
    private TerminatorChecker terminatorChecker;
    private int ifTrue = -1;
    private int ifFalse = -1;
    private int exitId = -1;

    BlockChecker(final int... ids) {
      if( ids.length <= 1) {
        throw new IllegalArgumentException("creating a block with only one successors should not be possible!");
      }
      successors(ids);
    }

    BlockChecker(final Tree.Kind kind, final int... ids) {
      successors(ids);
      terminator(kind);
    }

    BlockChecker(final ElementChecker... checkers) {
      Collections.addAll(this.checkers, checkers);
      if (this.checkers.isEmpty()) {
        throw new IllegalArgumentException("Only terminator may have no elements!");
      }
    }

    BlockChecker successors(final int... ids) {
      if (ifTrue != -1 || ifFalse != -1) {
        throw new IllegalArgumentException("Cannot mix true/false with generic successors!");
      }
      successorIDs = new int[ids.length];
      int n = 0;
      for (int i : ids) {
        successorIDs[n++] = i;
      }
      Arrays.sort(successorIDs);
      return this;
    }

    BlockChecker ifTrue(final int id) {
      if (successorIDs.length > 0) {
        throw new IllegalArgumentException("Cannot mix true/false with generic successors!");
      }
      ifTrue = id;
      return this;
    }

    BlockChecker ifFalse(final int id) {
      if (successorIDs.length > 0) {
        throw new IllegalArgumentException("Cannot mix true/false with generic successors!");
      }
      ifFalse = id;
      return this;
    }

    BlockChecker terminator(final Kind kind) {
      this.terminatorChecker = new TerminatorChecker(kind);
      return this;
    }

    public void check(final Block block) {
      assertThat(block.elements()).as("Expected number of elements in block " + block.id()).hasSize(checkers.size());
      final Iterator<ElementChecker> checkerIterator = checkers.iterator();
      for (final Tree element : block.elements()) {
        checkerIterator.next().check(element);
      }
      if (successorIDs.length == 0) {
        if (ifTrue != -1) {
          assertThat(block.trueBlock().id()).as("Expected true successor block " + block.id()).isEqualTo(ifTrue);
        }
        if (ifFalse != -1) {
          assertThat(block.falseBlock().id()).as("Expected true successor block " + block.id()).isEqualTo(ifFalse);
        }
        if(exitId != -1) {
          assertThat(block.exitBlock().id()).as("Expected exit successor block " + block.id()).isEqualTo(exitId);
        }
      } else {
        assertThat(block.successors()).as("Expected number of successors in block " + block.id()).hasSize(successorIDs.length);
        final int[] actualSuccessorIDs = new int[successorIDs.length];
        int n = 0;
        for (final Block successor : block.successors()) {
          actualSuccessorIDs[n++] = successor.id();
        }
        Arrays.sort(actualSuccessorIDs);
        assertThat(actualSuccessorIDs).as("Expected successors in block " + block.id()).isEqualTo(successorIDs);
      }
      if (terminatorChecker != null) {
        terminatorChecker.check(block.terminator());
      }
    }

    BlockChecker exit(final int id) {
      exitId = id;
      return this;
    }
  }

  private static class ElementChecker {

    private final Tree.Kind kind;
    private final String name;

    ElementChecker(final Tree.Kind kind, final String name) {
      super();
      this.kind = kind;
      this.name = name;
      switch (kind) {
        case VARIABLE:
        case IDENTIFIER:
        case CHAR_LITERAL:
        case STRING_LITERAL:
        case INT_LITERAL:
        case METHOD_INVOCATION:
          break;
        default:
          throw new IllegalArgumentException("Unsupported element kind! "+kind);
      }
    }

    ElementChecker(final Tree.Kind kind, final int value) {
      super();
      this.kind = kind;
      this.name = Integer.toString(value);
      switch (kind) {
        case INT_LITERAL:
          break;
        default:
          throw new IllegalArgumentException("Unsupported element kind!");
      }
    }

    public ElementChecker(final Tree.Kind kind) {
      super();
      this.kind = kind;
      name = null;
      switch (kind) {
        case METHOD_INVOCATION:
        case METHOD_REFERENCE:
        case MEMBER_SELECT:
        case NULL_LITERAL:
        case EQUAL_TO:
        case NOT_EQUAL_TO:
        case LESS_THAN:
        case LESS_THAN_OR_EQUAL_TO:
        case GREATER_THAN:
        case GREATER_THAN_OR_EQUAL_TO:
        case POSTFIX_INCREMENT:
        case PREFIX_INCREMENT:
        case POSTFIX_DECREMENT:
        case PREFIX_DECREMENT:
        case TRY_STATEMENT:
        case NEW_CLASS:
        case NEW_ARRAY:
        case INSTANCE_OF:
        case LAMBDA_EXPRESSION:
        case TYPE_CAST:
        case PLUS_ASSIGNMENT:
        case ASSIGNMENT:
        case ARRAY_ACCESS_EXPRESSION:
        case LOGICAL_COMPLEMENT:
        case PLUS:
          break;
        default:
          throw new IllegalArgumentException("Unsupported element kind: " + kind);
      }
    }

    public void check(final Tree element) {
      assertThat(element.kind()).as("Element kind").isEqualTo(kind);
      switch (element.kind()) {
        case VARIABLE:
          assertThat(((VariableTree) element).simpleName().name()).as("Variable name").isEqualTo(name);
          break;
        case IDENTIFIER:
          assertThat(((IdentifierTree) element).identifierToken().text()).as("Identifier").isEqualTo(name);
          break;
        case INT_LITERAL:
          assertThat(((LiteralTree) element).token().text()).as("Integer").isEqualTo(name);
          break;
        case CHAR_LITERAL:
          assertThat(((LiteralTree) element).token().text()).as("String").isEqualTo(name);
          break;
        case METHOD_INVOCATION:
          if (name != null) {
            MethodInvocationTree method = (MethodInvocationTree) element;
            MemberSelectExpressionTree select = (MemberSelectExpressionTree) method.methodSelect();
            assertThat(select.identifier().toString()).as("Method").isEqualTo(name);
          }
          break;
        default:
          // No need to test any associated symbol for the other cases
          break;
      }
    }
  }

  private static class TerminatorChecker {

    private final Kind kind;

    private TerminatorChecker(final Tree.Kind kind) {
      this.kind = kind;
      switch (kind) {
        case IF_STATEMENT:
        case CONDITIONAL_OR:
        case CONDITIONAL_AND:
        case CONDITIONAL_EXPRESSION:
        case BREAK_STATEMENT:
        case CONTINUE_STATEMENT:
        case SWITCH_STATEMENT:
        case RETURN_STATEMENT:
        case FOR_STATEMENT:
        case FOR_EACH_STATEMENT:
        case WHILE_STATEMENT:
        case DO_STATEMENT:
        case THROW_STATEMENT:
        case SYNCHRONIZED_STATEMENT:
          break;
        default:
          throw new IllegalArgumentException("Unexpected terminator kind!");
      }
    }

    public void check(final Tree element) {
      assertThat(element).as("Element kind").isNotNull();
      assertThat(element.kind()).as("Element kind").isEqualTo(kind);
    }

  }

  public static final ActionParser<Tree> parser = JavaParser.createParser(Charsets.UTF_8);

  private static CFG buildCFG(final String methodCode) {
    final CompilationUnitTree cut = (CompilationUnitTree) parser.parse("class A { " + methodCode + " }");
    final MethodTree tree = ((MethodTree) ((ClassTree) cut.types().get(0)).members().get(0));
    return CFG.build(tree);
  }

  @Test
  public void empty_cfg() {
    final CFG cfg = buildCFG("void fun() {}");
    final CFGChecker cfgChecker = checker();
    cfgChecker.check(cfg);
    assertThat(cfg.entry().isMethodExitBlock()).as("entry is an exit").isTrue();
  }

  @Test
  public void simplest_cfg() {
    final CFG cfg = buildCFG("void fun() { bar();}");
    final CFGChecker cfgChecker = checker(
      block(
        element(Tree.Kind.IDENTIFIER, "bar"),
        element(Tree.Kind.METHOD_INVOCATION)).successors(0));
    cfgChecker.check(cfg);
    CFG.Block entry = cfg.entry();
    assertThat(entry.isMethodExitBlock()).as("1st block is not an exit").isFalse();
    assertThat(entry.successors()).as("number of successors").hasSize(1);
    CFG.Block exit = entry.successors().iterator().next();
    assertThat(exit.isMethodExitBlock()).as("2nd block is an exit").isTrue();
  }

  @Test
  public void straight_method_calls() {
    final CFG cfg = buildCFG("void fun() { bar();qix();baz();}");
    final CFGChecker cfgChecker = checker(
      block(
        element(Tree.Kind.IDENTIFIER, "bar"),
        element(Tree.Kind.METHOD_INVOCATION),
        element(Tree.Kind.IDENTIFIER, "qix"),
        element(Tree.Kind.METHOD_INVOCATION),
        element(Tree.Kind.IDENTIFIER, "baz"),
        element(Tree.Kind.METHOD_INVOCATION)).successors(0));
    cfgChecker.check(cfg);
  }

  @Test
  public void single_declaration() {
    final CFG cfg = buildCFG("void fun() {Object o;}");
    final CFGChecker cfgChecker = checker(
      block(
        element(Tree.Kind.VARIABLE, "o")).successors(0));
    cfgChecker.check(cfg);
  }

  @Test
  public void if_then() {
    final CFG cfg = buildCFG("void fun() {if(a) { foo(); } }");
    final CFGChecker cfgChecker = checker(
      block(
        element(Tree.Kind.IDENTIFIER, "a")
        ).terminator(Tree.Kind.IF_STATEMENT).successors(0, 1),
      block(
        element(Tree.Kind.IDENTIFIER, "foo"),
        element(Tree.Kind.METHOD_INVOCATION)).successors(0));
    cfgChecker.check(cfg);
  }

  @Test
  public void if_then_else() {
    final CFG cfg = buildCFG("void fun() {if(a) { foo(); } else { bar(); } }");
    final CFGChecker cfgChecker = checker(
      block(
        element(Tree.Kind.IDENTIFIER, "a"))
      .terminator(Tree.Kind.IF_STATEMENT).successors(1, 2),
      block(
        element(Tree.Kind.IDENTIFIER, "foo"),
        element(Tree.Kind.METHOD_INVOCATION)).successors(0),
      block(
        element(Tree.Kind.IDENTIFIER, "bar"),
        element(Tree.Kind.METHOD_INVOCATION)
        ).successors(0));
    cfgChecker.check(cfg);
  }

  @Test
  public void if_then_elseif() {
    final CFG cfg = buildCFG("void fun() {\nif(a) {\n foo(); \n } else if(b) {\n bar();\n } }");
    final CFGChecker cfgChecker = checker(
      block(
        element(Tree.Kind.IDENTIFIER, "a")
        ).terminator(Tree.Kind.IF_STATEMENT).successors(2, 3),
      block(
        element(Tree.Kind.IDENTIFIER, "foo"),
        element(Tree.Kind.METHOD_INVOCATION)
        ).successors(0),
      block(
        element(Tree.Kind.IDENTIFIER, "b")
        ).terminator(Tree.Kind.IF_STATEMENT).successors(0, 1),
      block(
        element(Tree.Kind.IDENTIFIER, "bar"),
        element(Tree.Kind.METHOD_INVOCATION)
        ).successors(0));
    cfgChecker.check(cfg);
  }

  @Test
  public void conditionalOR() {
    final CFG cfg = buildCFG("void fun() {if(a || b) { foo(); } }");
    final CFGChecker cfgChecker = checker(
      block(
        element(Tree.Kind.IDENTIFIER, "a")
        ).terminator(Tree.Kind.CONDITIONAL_OR).successors(1, 2),
      block(
        element(Tree.Kind.IDENTIFIER, "b")
        ).terminator(Tree.Kind.IF_STATEMENT).successors(0, 1),
      block(
        element(Tree.Kind.IDENTIFIER, "foo"),
        element(Tree.Kind.METHOD_INVOCATION)
        ).successors(0));
    cfgChecker.check(cfg);
  }

  @Test
  public void conditionalAND() {
    final CFG cfg = buildCFG("void fun() {if((a && b)) { foo(); } }");
    final CFGChecker cfgChecker = checker(
      block(
        element(Tree.Kind.IDENTIFIER, "a")
        ).terminator(Tree.Kind.CONDITIONAL_AND).successors(0, 2),
      block(
        element(Tree.Kind.IDENTIFIER, "b")
        ).terminator(Tree.Kind.IF_STATEMENT).successors(0, 1),
      block(
        element(Tree.Kind.IDENTIFIER, "foo"),
        element(Tree.Kind.METHOD_INVOCATION)
        ).successors(0));
    cfgChecker.check(cfg);
  }

  @Test
  public void assignmentAND() {
    final CFG cfg = buildCFG("void fun() {boolean bool = a && b;}");
    final CFGChecker cfgChecker = checker(
      block(
        element(Tree.Kind.IDENTIFIER, "a")).terminator(Tree.Kind.CONDITIONAL_AND).successors(1, 2),
      block(
        element(Tree.Kind.IDENTIFIER, "b")).successors(1),
      block(
        element(Tree.Kind.VARIABLE, "bool")).successors(0));
    cfgChecker.check(cfg);
  }

  @Test
  public void three_branch_if() {
    final CFG cfg = buildCFG("void fun() { foo ? a : b; a.toString();}");
    final CFGChecker cfgChecker = checker(
      block(
        element(Tree.Kind.IDENTIFIER, "foo")).terminator(Tree.Kind.CONDITIONAL_EXPRESSION).successors(2, 3),
      block(
        element(Tree.Kind.IDENTIFIER, "a")).successors(1),
      block(
        element(Tree.Kind.IDENTIFIER, "b")).successors(1),
      block(
        element(Tree.Kind.IDENTIFIER, "a"),
        element(Tree.Kind.METHOD_INVOCATION)).successors(0));
    cfgChecker.check(cfg);
  }

  @Test
  public void switch_statement() {
    CFG cfg = buildCFG("void foo(int i, int j, int k) {\n" +
        "    switch (i==-1 ? j:k) {\n" +
        "      default:;\n" +
        "    }\n" +
        "  }");

    assertThat(cfg.blocks().get(0).id()).isEqualTo(5);
    cfg = buildCFG(
      "void fun(int foo) { int a; switch(foo) { case 1: System.out.println(bar);case 2: System.out.println(qix);break; default: System.out.println(baz);} }");
    CFGChecker cfgChecker = checker(
      block(
        element(Kind.INT_LITERAL, "1"),
        element(Tree.Kind.IDENTIFIER, "bar"),
        element(Tree.Kind.IDENTIFIER, "System"),
        element(Tree.Kind.MEMBER_SELECT),
        element(Tree.Kind.METHOD_INVOCATION)
        ).successors(3),
      block(
        element(Kind.INT_LITERAL, "2"),
        element(Tree.Kind.IDENTIFIER, "qix"),
        element(Tree.Kind.IDENTIFIER, "System"),
        element(Tree.Kind.MEMBER_SELECT),
        element(Tree.Kind.METHOD_INVOCATION)
        ).terminator(Tree.Kind.BREAK_STATEMENT).successors(0),
      block(
        element(Tree.Kind.IDENTIFIER, "baz"),
        element(Tree.Kind.IDENTIFIER, "System"),
        element(Tree.Kind.MEMBER_SELECT),
        element(Tree.Kind.METHOD_INVOCATION)
        ).successors(0),
      block(
        element(Tree.Kind.VARIABLE, "a"),
        element(Tree.Kind.IDENTIFIER, "foo")
        ).terminator(Tree.Kind.SWITCH_STATEMENT).successors(2, 3, 4));
    cfgChecker.check(cfg);
  }

  @Test
  public void switch_statement_with_piledUpCases_againstDefault() {
    final CFG cfg = buildCFG(
      "void fun(int foo) { int a; switch(foo) { case 1: System.out.println(bar);case 2: System.out.println(qix);break; case 3: case 4: default: System.out.println(baz);} }");
    final CFGChecker cfgChecker = checker(
      block(
        element(Kind.INT_LITERAL, "1"),
        element(Tree.Kind.IDENTIFIER, "bar"),
        element(Tree.Kind.IDENTIFIER, "System"),
        element(Tree.Kind.MEMBER_SELECT),
        element(Tree.Kind.METHOD_INVOCATION)).successors(3),
      block(
        element(Kind.INT_LITERAL, "2"),
        element(Tree.Kind.IDENTIFIER, "qix"),
        element(Tree.Kind.IDENTIFIER, "System"),
        element(Tree.Kind.MEMBER_SELECT),
        element(Tree.Kind.METHOD_INVOCATION)).terminator(Tree.Kind.BREAK_STATEMENT).successors(0),
      block(
        element(Kind.INT_LITERAL, "4"),
        element(Kind.INT_LITERAL, "3"),
        element(Tree.Kind.IDENTIFIER, "baz"),
        element(Tree.Kind.IDENTIFIER, "System"),
        element(Tree.Kind.MEMBER_SELECT),
        element(Tree.Kind.METHOD_INVOCATION)).successors(0),
      block(
        element(Tree.Kind.VARIABLE, "a"),
        element(Tree.Kind.IDENTIFIER, "foo")).terminator(Tree.Kind.SWITCH_STATEMENT).successors(2, 3, 4));
    cfgChecker.check(cfg);
  }

  @Test
  public void switch_statement_without_default() {
    final CFG cfg = buildCFG(
      "void fun(int foo) { int a; switch(foo) { case 1: System.out.println(bar);case 2: System.out.println(qix);break;} Integer.toString(foo); }");
    final CFGChecker cfgChecker = checker(
      block(
        element(Kind.INT_LITERAL, "1"),
        element(Tree.Kind.IDENTIFIER, "bar"),
        element(Tree.Kind.IDENTIFIER, "System"),
        element(Tree.Kind.MEMBER_SELECT),
        element(Tree.Kind.METHOD_INVOCATION)).successors(3),
      block(
        element(Kind.INT_LITERAL, "2"),
        element(Tree.Kind.IDENTIFIER, "qix"),
        element(Tree.Kind.IDENTIFIER, "System"),
        element(Tree.Kind.MEMBER_SELECT),
        element(Tree.Kind.METHOD_INVOCATION)).terminator(Tree.Kind.BREAK_STATEMENT).successors(1),
      block(
        element(Tree.Kind.VARIABLE, "a"),
        element(Tree.Kind.IDENTIFIER, "foo")).terminator(Tree.Kind.SWITCH_STATEMENT).successors(1, 3, 4),
      block(
        element(Tree.Kind.IDENTIFIER, "foo"),
        element(Tree.Kind.IDENTIFIER, "Integer"),
        element(Tree.Kind.METHOD_INVOCATION)).successors(0));
    cfgChecker.check(cfg);
  }

  @Test
  public void switch_statement_with_expression_in_case() {
    final CFG cfg = buildCFG(
      "void fun() { int a; switch(b) { case c : System.out.println(1);break; case d || e: System.out.println(2);break;} }");
    final CFGChecker cfgChecker = checker(
      block(
        element(Kind.IDENTIFIER, "c"),
        element(Kind.INT_LITERAL, "1"),
        element(Kind.IDENTIFIER, "System"),
        element(Kind.MEMBER_SELECT),
        element(Tree.Kind.METHOD_INVOCATION)).successors(0),
      block(
        element(Kind.IDENTIFIER, "d")).terminator(Kind.CONDITIONAL_OR).successors(2, 3),
      block(
        element(Kind.IDENTIFIER, "e")).successors(2),
      block(
        element(Kind.INT_LITERAL, "2"),
        element(Tree.Kind.IDENTIFIER, "System"),
        element(Tree.Kind.MEMBER_SELECT),
        element(Tree.Kind.METHOD_INVOCATION)).terminator(Tree.Kind.BREAK_STATEMENT).successors(0),
      block(
        element(Tree.Kind.VARIABLE, "a"),
        element(Tree.Kind.IDENTIFIER, "b")).terminator(Tree.Kind.SWITCH_STATEMENT).successors(0, 4, 5));
    cfgChecker.check(cfg);
  }

  @Test
  public void return_statement() {
    final CFG cfg = buildCFG("void fun(Object foo) { if(foo == null) return; }");
    final CFGChecker cfgChecker = checker(
      block(
        element(Tree.Kind.IDENTIFIER, "foo"),
        element(Tree.Kind.NULL_LITERAL),
        element(Tree.Kind.EQUAL_TO)
        ).terminator(Tree.Kind.IF_STATEMENT).successors(0, 1),
      terminator(Tree.Kind.RETURN_STATEMENT, 0));
    cfgChecker.check(cfg);
  }

  @Test
  public void array_loop() {
    final CFG cfg = buildCFG("void fun(Object foo) {System.out.println(''); for(int i =0;i<10;i++) { System.out.println(i); } }");
    final CFGChecker cfgChecker = checker(
      block(
        element(Tree.Kind.CHAR_LITERAL, "''"),
        element(Tree.Kind.IDENTIFIER, "System"),
        element(Tree.Kind.MEMBER_SELECT),
        element(Tree.Kind.METHOD_INVOCATION),
        element(Tree.Kind.INT_LITERAL, 0),
        element(Tree.Kind.VARIABLE, "i")
        ).successors(3),
      block(
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.INT_LITERAL, 10),
        element(Tree.Kind.LESS_THAN)
        ).terminator(Tree.Kind.FOR_STATEMENT).successors(0, 2),
      block(
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.IDENTIFIER, "System"),
        element(Tree.Kind.MEMBER_SELECT),
        element(Tree.Kind.METHOD_INVOCATION)
        ).successors(1),
      block(
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.POSTFIX_INCREMENT)
        ).successors(3));
    cfgChecker.check(cfg);
  }

  @Test
  public void array_loop_with_break() {
    final CFG cfg = buildCFG("void fun(Object foo) { for(int i =0;i<10;i++) { if(i == 5) break; } }");
    final CFGChecker cfgChecker = checker(
      block(
        element(Tree.Kind.INT_LITERAL, 0),
        element(Tree.Kind.VARIABLE, "i")
        ).successors(4),
      block(
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.INT_LITERAL, 10),
        element(Tree.Kind.LESS_THAN)
        ).terminator(Tree.Kind.FOR_STATEMENT).successors(0, 3),
      block(
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.INT_LITERAL, 5),
        element(Tree.Kind.EQUAL_TO)
        ).terminator(Tree.Kind.IF_STATEMENT).successors(1, 2),
      terminator(Tree.Kind.BREAK_STATEMENT, 0),
      block(
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.POSTFIX_INCREMENT)
        ).successors(4));
    cfgChecker.check(cfg);
  }

  @Test
  public void array_loop_with_continue() {
    final CFG cfg = buildCFG("void fun(Object foo) { for(int i =0;i<10;i++) { if(i == 5) continue; } }");
    final CFGChecker cfgChecker = checker(
      block(
        element(Tree.Kind.INT_LITERAL, 0),
        element(Tree.Kind.VARIABLE, "i")
        ).successors(4),
      block(
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.INT_LITERAL, 10),
        element(Tree.Kind.LESS_THAN)
        ).terminator(Tree.Kind.FOR_STATEMENT).successors(0, 3),
      block(
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.INT_LITERAL, 5),
        element(Tree.Kind.EQUAL_TO)
        ).terminator(Tree.Kind.IF_STATEMENT).successors(1, 2),
      terminator(Tree.Kind.CONTINUE_STATEMENT, 1),
      block(
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.POSTFIX_INCREMENT)
        ).successors(4));
    cfgChecker.check(cfg);
  }

  @Test
  public void foreach_loop_continue() {
    final CFG cfg = buildCFG("void fun(){ System.out.println('start'); for(String foo:list) {System.out.println(foo); if(foo.length()> 2) {continue;}  System.out.println('');} System.out.println('end'); }");
    final CFGChecker cfgChecker = checker(
        block(
            element(Tree.Kind.CHAR_LITERAL, "'start'"),
            element(Tree.Kind.IDENTIFIER, "System"),
            element(Tree.Kind.MEMBER_SELECT),
            element(Tree.Kind.METHOD_INVOCATION)).successors(6),
        block(
            element(Tree.Kind.IDENTIFIER, "list")).successors(2),
        block(
            element(Tree.Kind.IDENTIFIER, "foo"),
            element(Tree.Kind.IDENTIFIER, "System"),
            element(Tree.Kind.MEMBER_SELECT),
            element(Kind.METHOD_INVOCATION),
            element(Tree.Kind.IDENTIFIER, "foo"),
            element(Kind.METHOD_INVOCATION),
            element(Kind.INT_LITERAL, 2),
            element(Kind.GREATER_THAN)
        ).terminator(Kind.IF_STATEMENT).successors(3, 4),
        terminator(Kind.CONTINUE_STATEMENT).successors(2),
        block(
            element(Tree.Kind.CHAR_LITERAL, "''"),
            element(Tree.Kind.IDENTIFIER, "System"),
            element(Tree.Kind.MEMBER_SELECT),
            element(Tree.Kind.METHOD_INVOCATION)).successors(2),
        block(
            element(Tree.Kind.VARIABLE, "foo")).terminator(Tree.Kind.FOR_EACH_STATEMENT).successors(1, 5),
        block(
            element(Tree.Kind.CHAR_LITERAL, "'end'"),
            element(Tree.Kind.IDENTIFIER, "System"),
            element(Tree.Kind.MEMBER_SELECT),
            element(Tree.Kind.METHOD_INVOCATION)).successors(0));
    cfgChecker.check(cfg);
  }

  @Test
  public void foreach_loop() {
    CFG cfg = buildCFG("void fun(){ System.out.println(''); for(String foo:list) {System.out.println(foo);} System.out.println(''); }");
    CFGChecker cfgChecker = checker(
        block(
            element(Tree.Kind.CHAR_LITERAL, "''"),
            element(Tree.Kind.IDENTIFIER, "System"),
            element(Tree.Kind.MEMBER_SELECT),
            element(Tree.Kind.METHOD_INVOCATION)).successors(4),
        block(
            element(Tree.Kind.IDENTIFIER, "list")).successors(2),
        block(
            element(Tree.Kind.IDENTIFIER, "foo"),
            element(Tree.Kind.IDENTIFIER, "System"),
            element(Tree.Kind.MEMBER_SELECT),
            element(Tree.Kind.METHOD_INVOCATION)).successors(2),
        block(
            element(Tree.Kind.VARIABLE, "foo")).terminator(Tree.Kind.FOR_EACH_STATEMENT).successors(1, 3),
        block(
            element(Tree.Kind.CHAR_LITERAL, "''"),
            element(Tree.Kind.IDENTIFIER, "System"),
            element(Tree.Kind.MEMBER_SELECT),
            element(Tree.Kind.METHOD_INVOCATION)).successors(0));
    cfgChecker.check(cfg);
    cfg = buildCFG("void fun(){ for (String n : dir.list(foo() ? \"**\" : \"\")) {\n" +
        "      if (s.isEmpty()) {\n" +
        "        relativePath = n;\n" +
        "      }\n" +
        "    }}");
    cfgChecker = new CFGChecker(
        block(
            element(Kind.IDENTIFIER, "foo"),
            element(Kind.METHOD_INVOCATION)).terminator(Kind.CONDITIONAL_EXPRESSION).ifTrue(6).ifFalse(5),
        block(element(Kind.STRING_LITERAL, "**")).successors(4),
        block(element(Kind.STRING_LITERAL, "")).successors(4),
        block(
            element(Kind.IDENTIFIER, "dir"),
            element(Kind.METHOD_INVOCATION)).successors(1),
        block(
            element(Kind.IDENTIFIER, "s"),
            element(Kind.METHOD_INVOCATION)).terminator(Kind.IF_STATEMENT).ifTrue(2).ifFalse(1),
        block(
            element(Kind.IDENTIFIER, "n"),
            element(Kind.IDENTIFIER, "relativePath"),
            element(Kind.ASSIGNMENT)).successors(1),
        block(element(Kind.VARIABLE, "n")).terminator(Kind.FOR_EACH_STATEMENT).ifFalse(0).ifTrue(3)
        );
    cfgChecker.check(cfg);
  }

  @Test
  public void while_loop() {
    final CFG cfg = buildCFG("void fun() {int i = 0; while(i < 10) {i++; System.out.println(i); } }");
    final CFGChecker cfgChecker = checker(
      block(
        element(Tree.Kind.INT_LITERAL, 0),
        element(Tree.Kind.VARIABLE, "i")
        ).successors(2),
      block(
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.INT_LITERAL, 10),
        element(Tree.Kind.LESS_THAN)
        ).terminator(Tree.Kind.WHILE_STATEMENT).successors(0, 1),
      block(
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.POSTFIX_INCREMENT),
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.IDENTIFIER, "System"),
        element(Tree.Kind.MEMBER_SELECT),
        element(Tree.Kind.METHOD_INVOCATION)
        ).successors(2));
    cfgChecker.check(cfg);
  }

  @Test
  public void while_loop_with_break() {
    final CFG cfg = buildCFG("void fun() {int i = 0; while(i < 10) {i++; if(i == 5) break; } }");
    final CFGChecker cfgChecker = checker(
      block(
        element(Tree.Kind.INT_LITERAL, 0),
        element(Tree.Kind.VARIABLE, "i")
        ).successors(3),
      block(
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.INT_LITERAL, 10),
        element(Tree.Kind.LESS_THAN)
        ).terminator(Tree.Kind.WHILE_STATEMENT).successors(0, 2),
      block(
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.POSTFIX_INCREMENT),
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.INT_LITERAL, 5),
        element(Tree.Kind.EQUAL_TO)
        ).terminator(Tree.Kind.IF_STATEMENT).successors(1, 3),
      terminator(Tree.Kind.BREAK_STATEMENT, 0));
    cfgChecker.check(cfg);
  }

  @Test
  public void while_loop_with_continue() {
    final CFG cfg = buildCFG("void fun() {int i = 0; while(i < 10) {i++; if(i == 5) continue; } }");
    final CFGChecker cfgChecker = checker(
      block(
        element(Tree.Kind.INT_LITERAL, 0),
        element(Tree.Kind.VARIABLE, "i")
        ).successors(3),
      block(
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.INT_LITERAL, 10),
        element(Tree.Kind.LESS_THAN)
        ).terminator(Tree.Kind.WHILE_STATEMENT).successors(0, 2),
      block(
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.POSTFIX_INCREMENT),
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.INT_LITERAL, 5),
        element(Tree.Kind.EQUAL_TO)
        ).terminator(Tree.Kind.IF_STATEMENT).successors(1, 3),
      terminator(Tree.Kind.CONTINUE_STATEMENT, 3));
    cfgChecker.check(cfg);
  }

  @Test
  public void do_while_loop() {
    final CFG cfg = buildCFG("void fun() {int i = 0; do {i++; System.out.println(i); }while(i < 10); }");
    final CFGChecker cfgChecker = checker(
      block(
        element(Tree.Kind.INT_LITERAL, 0),
        element(Tree.Kind.VARIABLE, "i")
        ).successors(2),
      block(
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.POSTFIX_INCREMENT),
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.IDENTIFIER, "System"),
        element(Tree.Kind.MEMBER_SELECT),
        element(Tree.Kind.METHOD_INVOCATION)
        ).successors(1),
      block(
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.INT_LITERAL, 10),
        element(Tree.Kind.LESS_THAN)
        ).terminator(Tree.Kind.DO_STATEMENT).successors(0, 2));
    cfgChecker.check(cfg);
  }

  @Test
  public void do_while_loop_with_break() {
    final CFG cfg = buildCFG("void fun() {int i = 0; do { i++; if(i == 5) break; }while(i < 10); }");
    final CFGChecker cfgChecker = checker(
      block(
        element(Tree.Kind.INT_LITERAL, 0),
        element(Tree.Kind.VARIABLE, "i")
        ).successors(3),
      block(
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.POSTFIX_INCREMENT),
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.INT_LITERAL, 5),
        element(Tree.Kind.EQUAL_TO)
        ).terminator(Tree.Kind.IF_STATEMENT).successors(1, 2),
      terminator(Tree.Kind.BREAK_STATEMENT, 0),
      block(
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.INT_LITERAL, 10),
        element(Tree.Kind.LESS_THAN)
        ).terminator(Tree.Kind.DO_STATEMENT).successors(0, 3));
    cfgChecker.check(cfg);
  }

  @Test
  public void do_while_loop_with_continue() {
    final CFG cfg = buildCFG("void fun() {int i = 0; do{i++; if(i == 5) continue; }while(i < 10); }");
    final CFGChecker cfgChecker = checker(
      block(
        element(Tree.Kind.INT_LITERAL, 0),
        element(Tree.Kind.VARIABLE, "i")
        ).successors(3),
      block(
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.POSTFIX_INCREMENT),
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.INT_LITERAL, 5),
        element(Tree.Kind.EQUAL_TO)
        ).terminator(Tree.Kind.IF_STATEMENT).successors(1, 2),
      terminator(Tree.Kind.CONTINUE_STATEMENT, 3),
      block(
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.INT_LITERAL, 10),
        element(Tree.Kind.LESS_THAN)
        ).terminator(Tree.Kind.DO_STATEMENT).successors(0, 3));
    cfgChecker.check(cfg);
  }

  @Test
  public void break_on_label() {
    final CFG cfg = buildCFG("void fun() { foo: for(int i = 0; i<10;i++) { if(i==5) break foo; } }");
    final CFGChecker cfgChecker = checker(
      block(
        element(Tree.Kind.INT_LITERAL, 0),
        element(Tree.Kind.VARIABLE, "i")
        ).successors(4),
      block(
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.INT_LITERAL, 10),
        element(Tree.Kind.LESS_THAN)
        ).terminator(Tree.Kind.FOR_STATEMENT).successors(0, 3),
      block(
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.INT_LITERAL, 5),
        element(Tree.Kind.EQUAL_TO)
        ).terminator(Tree.Kind.IF_STATEMENT).successors(1, 2),
      terminator(Tree.Kind.BREAK_STATEMENT, 0),
      block(
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.POSTFIX_INCREMENT)
        ).successors(4));
    cfgChecker.check(cfg);
  }

  @Test
  public void continue_on_label() {
    final CFG cfg = buildCFG("void fun() { foo: for(int i = 0; i<10;i++) { plop(); if(i==5) continue foo; plop();} }");
    final CFGChecker cfgChecker = checker(
      block(
        element(Tree.Kind.INT_LITERAL, 0),
        element(Tree.Kind.VARIABLE, "i")
        ).successors(5),
      block(
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.INT_LITERAL, 10),
        element(Tree.Kind.LESS_THAN)
        ).terminator(Tree.Kind.FOR_STATEMENT).successors(0, 4),
      block(
        element(Tree.Kind.IDENTIFIER, "plop"),
        element(Kind.METHOD_INVOCATION),
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.INT_LITERAL, 5),
        element(Tree.Kind.EQUAL_TO)
        ).terminator(Tree.Kind.IF_STATEMENT).successors(2,3),
      terminator(Tree.Kind.CONTINUE_STATEMENT, 1),
        block(
            element(Tree.Kind.IDENTIFIER, "plop"),
            element(Kind.METHOD_INVOCATION)
        ).successors(1),
      block(
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.POSTFIX_INCREMENT)
        ).successors(5));
    cfgChecker.check(cfg);
  }

  @Test
  public void prefix_operators() {
    final CFG cfg = buildCFG("void fun() { ++i;i++; }");
    final CFGChecker cfgChecker = checker(
      block(
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.PREFIX_INCREMENT),
        element(Tree.Kind.IDENTIFIER, "i"),
        element(Tree.Kind.POSTFIX_INCREMENT)).successors(0));
    cfgChecker.check(cfg);
  }
  @Test
  public void exit_block_for_finally_with_if_statement() throws Exception {
    CFG cfg = buildCFG(" void test(boolean fooCalled) {\n" +
      "      Object bar;\n" +
      "      try {\n" +
      "        bar = new Bar();\n" +
      "      } finally {\n" +
      "        if (fooCalled) {foo();\n" +
      "        }\n" +
      "      }\n" +
      "      bar.toString();\n" +
      "    }");
    CFGChecker cfgChecker = checker(
      block(
        element(Kind.VARIABLE, "bar"),
        element(Kind.TRY_STATEMENT)
      ).successors(5, 4).exit(4),
      block(
        element(Kind.NEW_CLASS),
        element(Kind.IDENTIFIER, "bar"),
        element(Kind.ASSIGNMENT)
      ).successors(4),
      block(
        element(Kind.IDENTIFIER, "fooCalled")
      ).terminator(Kind.IF_STATEMENT).successors(2, 3),
      block(
        element(Kind.IDENTIFIER, "foo"),
        element(Kind.METHOD_INVOCATION)
      ).successors(2),
      new BlockChecker(1, 0).exit(0),
      block(
        element(Kind.IDENTIFIER, "bar"),
        element(Kind.METHOD_INVOCATION)
      ).successors(0)
    );
    cfgChecker.check(cfg);

  }
  @Test
  public void nested_try_finally() throws Exception {

    CFG cfg = buildCFG("  void  foo() {\n"+
      "    try {\n"+
      "      java.util.zip.ZipFile file = new java.util.zip.ZipFile(fileName);\n"+
      "      try {\n"+
      "        file.foo();// do something with the file...\n"+
      "      } finally {\n"+
      "        file.close();\n"+
      "      }\n"+
      "    } catch (Exception e) {\n"+
      "      // Handle exception\n"+
      "    }\n"+
      "  }");
    CFGChecker cfgChecker = checker(
      block(
        element(Tree.Kind.TRY_STATEMENT)
      ).successors(3, 0),
      block(
        element(Tree.Kind.IDENTIFIER, "fileName"),
        element(Kind.NEW_CLASS),
        element(Kind.VARIABLE, "file"),
        element(Kind.TRY_STATEMENT)
      ).successors(1, 2).exit(1),
      block(
        element(Tree.Kind.IDENTIFIER, "file"),
        element(Tree.Kind.METHOD_INVOCATION)
      ).successors(1),
      block(
        element(Tree.Kind.IDENTIFIER, "file"),
        element(Tree.Kind.METHOD_INVOCATION)
      ).successors(0)
      );
    cfgChecker.check(cfg);

  }

  @Test
  public void try_statement() {
    CFG cfg = buildCFG("void fun() {try {System.out.println('');} finally { System.out.println(''); }}");
    CFGChecker cfgChecker = checker(
        block(
            element(Tree.Kind.TRY_STATEMENT)
        ).successors(1, 2).exit(1),
        block(
            element(Tree.Kind.CHAR_LITERAL, "''"),
            element(Tree.Kind.IDENTIFIER, "System"),
            element(Tree.Kind.MEMBER_SELECT),
            element(Tree.Kind.METHOD_INVOCATION)
        ).successors(1),
        block(
            element(Tree.Kind.CHAR_LITERAL, "''"),
            element(Tree.Kind.IDENTIFIER, "System"),
            element(Tree.Kind.MEMBER_SELECT),
            element(Tree.Kind.METHOD_INVOCATION)
        ).successors(0));
    cfgChecker.check(cfg);
    cfg = buildCFG("void fun() {try {System.out.println('');} catch(IllegalArgumentException e) { foo('iae');} catch(Exception e){foo('e');}" +
        " finally { System.out.println('finally'); }}");
    cfgChecker = checker(
        block(
            element(Tree.Kind.TRY_STATEMENT)
        ).successors(2, 3, 4),
        block(
            element(Tree.Kind.CHAR_LITERAL, "'e'"),
            element(Tree.Kind.IDENTIFIER, "foo"),
            element(Tree.Kind.METHOD_INVOCATION)
        ).successors(1),
        block(
            element(Tree.Kind.CHAR_LITERAL, "'iae'"),
            element(Tree.Kind.IDENTIFIER, "foo"),
            element(Tree.Kind.METHOD_INVOCATION)
        ).successors(1),
        block(
            element(Tree.Kind.CHAR_LITERAL, "''"),
            element(Tree.Kind.IDENTIFIER, "System"),
            element(Tree.Kind.MEMBER_SELECT),
            element(Tree.Kind.METHOD_INVOCATION)
        ).successors(1, 3, 4),
        block(
            element(Tree.Kind.CHAR_LITERAL, "'finally'"),
            element(Tree.Kind.IDENTIFIER, "System"),
            element(Tree.Kind.MEMBER_SELECT),
            element(Tree.Kind.METHOD_INVOCATION)
        ).successors(0)
    );
    cfgChecker.check(cfg);
    cfg = buildCFG(
        "  private void f() {\n" +
            "    try {\n" +
            "    } catch (Exception e) {\n" +
            "      if (e instanceof IOException) { \n" +
            "      }\n}}");
    cfgChecker = checker(
        block(
            element(Tree.Kind.TRY_STATEMENT)
        ).successors(1, 2),
        block(
            element(Tree.Kind.IDENTIFIER, "e"),
            element(Tree.Kind.INSTANCE_OF)
        ).terminator(Tree.Kind.IF_STATEMENT).ifTrue(0).ifFalse(0),
        new BlockChecker(0, 2) // particular case of a block with multiple successors but no instructions.
    );
    cfgChecker.check(cfg);
    cfg = buildCFG(
        "  private void f() {\n" +
            "    try {\n" +
            "    return;" +
            "} finally { foo();} bar(); }");
    cfgChecker = checker(
        block(
            element(Tree.Kind.TRY_STATEMENT)
        ).successors(2, 3).exit(2),
        terminator(Kind.RETURN_STATEMENT).successors(2).exit(2),
        block(
            element(Tree.Kind.IDENTIFIER, "foo"),
            element(Kind.METHOD_INVOCATION)
        ).successors(0, 1).exit(0),
        block(
            element(Tree.Kind.IDENTIFIER, "bar"),
            element(Kind.METHOD_INVOCATION)
        ).successors(0)
    );
    cfgChecker.check(cfg);
  }

  @Test
  public void throw_statement() {
    final CFG cfg = buildCFG("void fun(Object a) {if(a==null) { throw new Exception();} System.out.println(''); }");
    final CFGChecker cfgChecker = checker(
      block(
        element(Tree.Kind.IDENTIFIER, "a"),
        element(Tree.Kind.NULL_LITERAL),
        element(Tree.Kind.EQUAL_TO)
        ).terminator(Tree.Kind.IF_STATEMENT).successors(1, 2),
      block(
        element(Tree.Kind.NEW_CLASS)).terminator(Tree.Kind.THROW_STATEMENT).successors(0),
      block(
        element(Tree.Kind.CHAR_LITERAL, "''"),
        element(Tree.Kind.IDENTIFIER, "System"),
        element(Tree.Kind.MEMBER_SELECT),
        element(Tree.Kind.METHOD_INVOCATION)).successors(0));
    cfgChecker.check(cfg);
  }

  @Test
  public void synchronized_statement() {
    final CFG cfg = buildCFG("void fun(Object a) {if(a==null) { synchronized(a) { foo();bar();} } System.out.println(''); }");
    final CFGChecker cfgChecker = checker(
      block(
        element(Tree.Kind.IDENTIFIER, "a"),
        element(Tree.Kind.NULL_LITERAL),
        element(Tree.Kind.EQUAL_TO)).terminator(Tree.Kind.IF_STATEMENT).successors(1, 3),
      block(
        element(Tree.Kind.IDENTIFIER, "a")).terminator(Tree.Kind.SYNCHRONIZED_STATEMENT).successors(2),
      block(
        element(Tree.Kind.IDENTIFIER, "foo"),
        element(Tree.Kind.METHOD_INVOCATION),
        element(Tree.Kind.IDENTIFIER, "bar"),
        element(Tree.Kind.METHOD_INVOCATION)).successors(1),
      block(
        element(Tree.Kind.CHAR_LITERAL, "''"),
        element(Tree.Kind.IDENTIFIER, "System"),
        element(Tree.Kind.MEMBER_SELECT),
        element(Tree.Kind.METHOD_INVOCATION)).successors(0));
    cfgChecker.check(cfg);
  }

  @Test
  public void multiple_constructions() {
    final CFG cfg = buildCFG("void fun(Object a) {if(a instanceof String) { a::toString;foo(y -> y+1); a += (String) a;  } }");
    final CFGChecker cfgChecker = checker(
      block(
        element(Tree.Kind.IDENTIFIER, "a"),
        element(Tree.Kind.INSTANCE_OF)
        ).terminator(Tree.Kind.IF_STATEMENT).successors(0, 1),
      block(
        element(Kind.METHOD_REFERENCE),
        element(Tree.Kind.LAMBDA_EXPRESSION),
        element(Tree.Kind.IDENTIFIER, "foo"),
        element(Tree.Kind.METHOD_INVOCATION),
        element(Tree.Kind.IDENTIFIER, "a"),
        element(Tree.Kind.TYPE_CAST),
        element(Tree.Kind.IDENTIFIER, "a"),
        element(Tree.Kind.PLUS_ASSIGNMENT)
        ).successors(0));
    cfgChecker.check(cfg);
  }

  @Test
  public void array_access_expression() {
    final CFG cfg = buildCFG("void fun(int[] array) { array[0] = 1; array[3+2] = 4; }");
    final CFGChecker cfgChecker = checker(
      block(
        element(Tree.Kind.INT_LITERAL, 1),
        element(Tree.Kind.INT_LITERAL, 0),
        element(Tree.Kind.IDENTIFIER, "array"),
        element(Tree.Kind.ARRAY_ACCESS_EXPRESSION),
        element(Tree.Kind.ASSIGNMENT),
        element(Tree.Kind.INT_LITERAL, 4),
        element(Tree.Kind.INT_LITERAL, 3),
        element(Tree.Kind.INT_LITERAL, 2),
        element(Tree.Kind.PLUS),
        element(Tree.Kind.IDENTIFIER, "array"),
        element(Tree.Kind.ARRAY_ACCESS_EXPRESSION),
        element(Tree.Kind.ASSIGNMENT)).successors(0));
    cfgChecker.check(cfg);
  }

  @Test
  public void try_with_resource() throws Exception {
    final CFG cfg = buildCFG("void fun() { String path = ''; try (BufferedReader br = new BufferedReader(new FileReader(path))) {} }");
    final CFGChecker cfgChecker = checker(
      block(
        element(Kind.CHAR_LITERAL, "''"),
        element(Kind.VARIABLE, "path"),
        element(Kind.TRY_STATEMENT)).successors(1),
      block(
        element(Kind.IDENTIFIER, "path"),
        element(Kind.NEW_CLASS),
        element(Kind.NEW_CLASS),
        element(Kind.VARIABLE, "br")).successors(0));
    cfgChecker.check(cfg);
  }

  @Test
  public void returnCascadedAnd() throws Exception {
    final CFG cfg = buildCFG(
      "void andAll(boolean a, boolean b, boolean c) { return a && b && c;}");
    final CFGChecker cfgChecker = checker(
      block(element(Kind.IDENTIFIER, "a")).terminator(Kind.CONDITIONAL_AND).ifTrue(4).ifFalse(3),
      block(element(Kind.IDENTIFIER, "b")).successors(3),
      terminator(Kind.CONDITIONAL_AND).ifTrue(2).ifFalse(1),
      block(element(Kind.IDENTIFIER, "c")).successors(1),
      terminator(Kind.RETURN_STATEMENT).successors(0));
    cfgChecker.check(cfg);
  }

  @Test
  public void returnCascadedOr() throws Exception {
    final CFG cfg = buildCFG(
      "void orAll(boolean a, boolean b, boolean c) { return a || b || c;}");
    final CFGChecker cfgChecker = checker(
      block(element(Kind.IDENTIFIER, "a")).terminator(Kind.CONDITIONAL_OR).ifTrue(3).ifFalse(4),
      block(element(Kind.IDENTIFIER, "b")).successors(3),
      terminator(Kind.CONDITIONAL_OR).ifTrue(1).ifFalse(2),
      block(element(Kind.IDENTIFIER, "c")).successors(1),
      terminator(Kind.RETURN_STATEMENT).successors(0));
    cfgChecker.check(cfg);
  }

  @Test
  public void complex_boolean_expression() throws Exception {
    final CFG cfg = buildCFG(" private boolean fun(boolean bool, boolean a, boolean b) {\n" +
        "    return (!bool && a) || (bool && b);\n" +
        "  }");
    final CFGChecker cfgChecker = checker(
        block(
            element(Kind.IDENTIFIER, "bool"),
            element(Kind.LOGICAL_COMPLEMENT)
        ).terminator(Kind.CONDITIONAL_AND).ifTrue(5).ifFalse(4),
        block(element(Kind.IDENTIFIER, "a")).successors(4),
        terminator(Kind.CONDITIONAL_OR).ifTrue(1).ifFalse(3),
        block(element(Kind.IDENTIFIER, "bool")).terminator(Kind.CONDITIONAL_AND).ifTrue(2).ifFalse(1),
        block(element(Kind.IDENTIFIER, "b")).successors(1),
        terminator(Kind.RETURN_STATEMENT).successors(0));
    cfgChecker.check(cfg);

  }

  @Test
  public void method_reference() throws Exception {
    final CFG cfg = buildCFG("void fun() { foo(Object::toString); }");
    final CFGChecker cfgChecker = checker(
        block(
            element(Kind.METHOD_REFERENCE),
            element(Kind.IDENTIFIER, "foo"),
            element(Kind.METHOD_INVOCATION)
        ).successors(0));
    cfgChecker.check(cfg);
  }

  @Test
  public void try_statement_with_CFG_blocks() {
    CFG cfg = buildCFG(
      "  private void f(boolean action) {\n" +
        "    try {\n" +
        "    if (action) {" +
        "       performAction();" +
        "    }" +
        "    doSomething();" +
        "} catch(Exception e) { foo();} bar(); }");
    CFGChecker cfgChecker = checker(
      block(
        element(Tree.Kind.TRY_STATEMENT)).successors(3, 5),
      block(
        element(Tree.Kind.IDENTIFIER, "action")).terminator(Kind.IF_STATEMENT).successors(2, 4),
      block(
        element(Tree.Kind.IDENTIFIER, "performAction"),
        element(Kind.METHOD_INVOCATION)).successors(2),
      block(
        element(Tree.Kind.IDENTIFIER, "foo"),
        element(Kind.METHOD_INVOCATION)).successors(1),
      block(
        element(Tree.Kind.IDENTIFIER, "doSomething"),
        element(Kind.METHOD_INVOCATION)).successors(1, 3),
      block(
        element(Tree.Kind.IDENTIFIER, "bar"),
        element(Kind.METHOD_INVOCATION)).successors(0));
    cfgChecker.check(cfg);
    cfg = buildCFG(
      "  private void f(boolean action) {\n" +
        "    try {\n" +
        "    doSomething();" +
        "    if (action) {" +
        "       performAction();" +
        "    }" +
        "} catch(Exception e) { foo();} bar(); }");
    cfgChecker = checker(
      block(
        element(Tree.Kind.TRY_STATEMENT)).successors(3, 5),
      block(
        element(Tree.Kind.IDENTIFIER, "doSomething"),
        element(Kind.METHOD_INVOCATION),
        element(Tree.Kind.IDENTIFIER, "action")).terminator(Kind.IF_STATEMENT).successors(2, 4),
      block(
        element(Tree.Kind.IDENTIFIER, "performAction"),
        element(Kind.METHOD_INVOCATION)).successors(2),
      block(
        element(Tree.Kind.IDENTIFIER, "foo"),
        element(Kind.METHOD_INVOCATION)).successors(1),
      new BlockChecker(1, 3),
      block(
        element(Tree.Kind.IDENTIFIER, "bar"),
        element(Kind.METHOD_INVOCATION)).successors(0));
    cfgChecker.check(cfg);
    cfg = buildCFG(
      "  private void f(boolean action) {\n" +
        "    try {\n" +
        "    if (action) {" +
        "       performAction();" +
        "    }" +
        "    doSomething();" +
        "} finally { foo();} bar(); }");
    cfgChecker = checker(
      block(
        element(Tree.Kind.TRY_STATEMENT)).successors(2, 5),
      block(
        element(Tree.Kind.IDENTIFIER, "action")).terminator(Kind.IF_STATEMENT).successors(3, 4),
      block(
        element(Tree.Kind.IDENTIFIER, "performAction"),
        element(Kind.METHOD_INVOCATION)).successors(3),
      block(
        element(Tree.Kind.IDENTIFIER, "doSomething"),
        element(Kind.METHOD_INVOCATION)).successors(2),
      block(
        element(Tree.Kind.IDENTIFIER, "foo"),
        element(Kind.METHOD_INVOCATION)).successors(0, 1),
      block(
        element(Tree.Kind.IDENTIFIER, "bar"),
        element(Kind.METHOD_INVOCATION)).successors(0));
    cfgChecker.check(cfg);
  }

}
