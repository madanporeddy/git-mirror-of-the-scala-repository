/* NSC -- new scala compiler
 * Copyright 2005 LAMP/EPFL
 * @author  Martin Odersky
 */

// $Id$

package scala.tools.nsc
package backend
package icode;

import scala.tools.nsc.ast._;
import scala.collection.mutable.{Stack, HashSet, BitSet};

trait Linearizers { self: ICodes =>
  import opcodes._;

  abstract class Linearizer {
    def linearize(c: IMethod): List[BasicBlock];
    def linearizeAt(c: IMethod, start: BasicBlock): List[BasicBlock]
  }

  /** 
   * A simple linearizer which predicts all branches to 
   * take the 'success' branch and tries to schedule those
   * blocks immediately after the test. This is in sync with
   * how 'while' statements are translated (if the test is 
   * 'true', the loop continues).
   */
  class NormalLinearizer extends Linearizer with WorklistAlgorithm {
    type Elem = BasicBlock;

    val worklist: WList = new Stack();

    var blocks: List[BasicBlock] = Nil;
    
    def linearize(m: IMethod): List[BasicBlock] = {
      val b = m.code.startBlock;
      blocks = Nil;

      run {
        worklist pushAll (m.exh map (_.startBlock));
        worklist.push(b); 
      }

      blocks.reverse;
    }
    
    def linearizeAt(m: IMethod, start: BasicBlock): List[BasicBlock] = {
      blocks = Nil
      worklist.clear()
      linearize(start)
    }

    /** Linearize another subtree and append it to the existing blocks. */
    def linearize(startBlock: BasicBlock): List[BasicBlock] = {
      //blocks = startBlock :: Nil;
      run( { worklist.push(startBlock); } );
      blocks.reverse;
    }

    def processElement(b: BasicBlock) = 
      if (b.size > 0) {
        add(b);
        b.lastInstruction match {
          case JUMP(whereto) => 
            add(whereto);
          case CJUMP(success, failure, _, _) =>
            add(success);
            add(failure);
          case CZJUMP(success, failure, _, _) =>
            add(success);
            add(failure);
          case SWITCH(_, labels) =>
            add(labels);
          case RETURN(_) => ();
          case THROW() =>   ();
        }
      }

    def dequeue: Elem = worklist.pop;

    /** 
     * Prepend b to the list, if not already scheduled. 
     * TODO: use better test than linear search
     */
    def add(b: BasicBlock) {
      if (blocks.contains(b))
        ()
      else {
        blocks = b :: blocks;
        worklist push b;
      }
    }

    def add(bs: List[BasicBlock]): Unit = bs foreach add;
  }

  /**
   * Linearize code using a depth first traversal.
   */
  class DepthFirstLinerizer extends Linearizer {
    var blocks: List[BasicBlock] = Nil;
    
    def linearize(m: IMethod): List[BasicBlock] = {
      blocks = Nil;

      dfs(m.code.startBlock);
      m.exh foreach (b => dfs(b.startBlock));

      blocks.reverse
    }
    
    def linearizeAt(m: IMethod, start: BasicBlock): List[BasicBlock] = {
      blocks = Nil
      dfs(start)
      blocks.reverse
    }

    def dfs(b: BasicBlock): Unit = 
      if (b.size > 0 && add(b))
        b.successors foreach dfs;

    /** 
     * Prepend b to the list, if not already scheduled. 
     * TODO: use better test than linear search
     * @return Returns true if the block was added.
     */
    def add(b: BasicBlock): Boolean = 
      if (blocks.contains(b))
        false
      else {
        blocks = b :: blocks;
        true
      }
  }

  /**
   * Linearize code in reverse post order. In fact, it does
   * a post order traversal, prepending visited nodes to the list.
   * This way, it is constructed already in reverse post order.
   */
  class ReversePostOrderLinearizer extends Linearizer {
    var blocks: List[BasicBlock] = Nil;
    var visited: HashSet[BasicBlock] = new HashSet;
    val added: BitSet = new BitSet
    
    def linearize(m: IMethod): List[BasicBlock] = {
      blocks = Nil;
      visited.clear;
      added.clear;

      m.exh foreach (b => rpo(b.startBlock));
      rpo(m.code.startBlock);
      
      // if the start block has predecessors, it won't be the first one 
      // in the linearization, so we need to enforce it here
      if (m.code.startBlock.predecessors eq Nil)
        blocks
      else
        m.code.startBlock :: (blocks.filterNot(_ == m.code.startBlock))
    }

    def linearizeAt(m: IMethod, start: BasicBlock): List[BasicBlock] = {
      blocks = Nil
      visited.clear
      added.clear
      
      rpo(start)
      blocks
    }
    
    def rpo(b: BasicBlock): Unit = 
      if (b.size > 0 && !(visited contains b)) {
        visited += b;
        b.successors foreach rpo;
        add(b);
      }

    /** 
     * Prepend b to the list, if not already scheduled. 
     * @return Returns true if the block was added.
     */
    def add(b: BasicBlock) = 
      if (!added(b.label)) {
        added += b.label
        blocks = b :: blocks;
      }
  }

  /** A 'dump' of the blocks in this method, which does not
   *  require any well-formedness of the basic blocks (like
   *  the last instruction being a jump).
   */
  class DumpLinearizer extends Linearizer {
    def linearize(m: IMethod): List[BasicBlock] =
      m.code.blocks.toList;
    
    def linearizeAt(m: IMethod, start: BasicBlock): List[BasicBlock] = {
      error("not implemented")
    }
  }
  
}
