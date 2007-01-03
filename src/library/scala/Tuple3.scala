
/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2007, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id$

// generated by genprod on Wed Jan 03 13:31:53 CET 2007

package scala

/** Tuple3 is the canonical representation of a @see Product3 */
case class Tuple3[+T1, +T2, +T3](_1:T1, _2:T2, _3:T3) 
  /*extends Product3[T1, T2, T3] */ {

   override def toString() = {
     val sb = new compat.StringBuilder
     sb.append('{').append(_1).append(',').append(_2).append(',').append(_3).append('}')
     sb.toString
   }
}
