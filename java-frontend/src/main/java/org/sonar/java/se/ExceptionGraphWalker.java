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
package org.sonar.java.se;

import org.sonar.java.cfg.CFG;
import org.sonar.java.cfg.CFG.Block;
import org.sonar.java.se.symbolicvalues.SymbolicExceptionValue;
import org.sonar.plugins.java.api.tree.BlockTree;
import org.sonar.plugins.java.api.tree.CatchTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.TryStatementTree;
import org.sonar.plugins.java.api.tree.VariableTree;

import java.util.List;

class ExceptionGraphWalker {

  private final List<Block> blocks;

  ExceptionGraphWalker(CFG cfg) {
    blocks = cfg.blocks();
  }

  Block lastBlock() {
    return blocks.get(blocks.size() - 1);
  }

  Block catchBlock(TryStatementTree tryStatement, SymbolicExceptionValue exceptionValue) {
    for (CatchTree catchTree : tryStatement.catches()) {
      VariableTree parameter = catchTree.parameter();
      if (exceptionValue.canRaise(parameter.symbol())) {
        return blockCorrespondingTo(catchTree.block());
      }
    }
    return null;
  }

  Block finallyBlock(TryStatementTree tryStatement) {
    BlockTree finallyBlock = tryStatement.finallyBlock();
    return finallyBlock == null ? null : blockCorrespondingTo(finallyBlock);
  }

  private Block blockCorrespondingTo(BlockTree aBlock) {
    for (Block block : blocks) {
      if (aBlock.equals(findBlockTree(block))) {
        return block;
      }
    }
    return null;
  }

  private BlockTree findBlockTree(Block block) {
    Tree statement;
    List<Tree> elements = block.elements();
    if (elements.isEmpty()) {
      statement = block.terminator();
      if (statement == null) {
        return null;
      }
    } else {
      statement = elements.get(0);
    }
    Tree parent = statement.parent();
    while (parent != null) {
      if (parent.is(Tree.Kind.BLOCK)) {
        return (BlockTree) parent;
      }
      parent = parent.parent();
    }
    return null;
  }
}
