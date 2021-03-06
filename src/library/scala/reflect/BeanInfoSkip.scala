/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2002-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |    http://scala-lang.org/               **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id$

package scala.reflect

/** This annotation indicates that bean information should
 *  <strong>not</strong> be generated for the val, var, or def that it is
 *  attached to.  
 *
 *  @author Ross Judson (rjudson@managedobjects.com)
 */
class BeanInfoSkip extends Annotation
