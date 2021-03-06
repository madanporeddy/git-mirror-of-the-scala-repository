/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2003-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id$


package scala.collection
package mutable

import generic._

import TraversableView.NoBuilder

/** A non-strict projection of an iterable. 
 * @author Sean McDirmid
 * @author Martin Odersky
 * @version 2.8
 * @since   2.8
 */
trait VectorView[A, +Coll] extends VectorViewLike[A, Coll, VectorView[A, Coll]]

object VectorView {
  type Coll = TraversableView[_, C] forSome { type C <: scala.collection.Traversable[_] }
  implicit def builderFactory[A]: BuilderFactory[A, VectorView[A, Vector[_]], Coll] = new BuilderFactory[A, VectorView[A, mutable.Vector[_]], Coll] { def apply(from: Coll) = new NoBuilder }
}
