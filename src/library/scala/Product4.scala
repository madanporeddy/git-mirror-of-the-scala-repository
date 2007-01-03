
/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id: genprod.scala 9543 2007-01-02 16:51:29Z emir $

// generated by genprod on Wed Jan 03 13:31:53 CET 2007

package scala

import Predef._

object Product4 {
  def unapply[T1, T2, T3, T4](x: Product4[T1, T2, T3, T4]): Some[Product4[T1, T2, T3, T4]] = 
    Some(x)
}

/** Product4 is a cartesian product of 4 components 
 */
trait Product4[+T1, +T2, +T3, +T4] extends Product {

  /**
   *  The arity of this product.
   *  @return 4
   */
  override def arity = 4

  /**
   *  Returns the n-th projection of this product if 0&lt;=n&lt;arity,
   *  otherwise null.
   *
   *  @param n number of the projection to be returned 
   *  @return  same as _(n+1)
   *  @throws  IndexOutOfBoundsException
   */
  override def element(n: Int) = n match {
    case 0 => _1
    case 1 => _2
    case 2 => _3
    case 3 => _4
    case _ => throw new IndexOutOfBoundsException(n.toString())
  }

  /** projection of this product */
  def _1: T1

/** projection of this product */
  def _2: T2

/** projection of this product */
  def _3: T3

/** projection of this product */
  def _4: T4


}
