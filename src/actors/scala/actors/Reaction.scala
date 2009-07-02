/*                     __                                               *\
**     ________ ___   / /  ___     Scala API                            **
**    / __/ __// _ | / /  / _ |    (c) 2005-2009, LAMP/EPFL             **
**  __\ \/ /__/ __ |/ /__/ __ |                                         **
** /____/\___/_/ |_/____/_/ | |                                         **
**                          |/                                          **
\*                                                                      */

// $Id$


package scala.actors

import java.lang.{InterruptedException, Runnable}

private[actors] class KillActorException extends Throwable {
  /*
   * For efficiency reasons we do not fill in
   * the execution stack trace.
   */
  override def fillInStackTrace(): Throwable = this
}

/** <p>
 *    The abstract class <code>Reaction</code> associates
 *    an instance of an <code>Actor</code> with a
 *    <a class="java/lang/Runnable" href="" target="contentFrame">
 *    <code>java.lang.Runnable</code></a>.
 *  </p>
 *
 *  @version 0.9.10
 *  @author Philipp Haller
 */
class Reaction(a: Actor, f: PartialFunction[Any, Unit], msg: Any) extends ActorTask(a, () => {
  if (f == null)
    a.act()
  else
    f(msg)
}) {

  def this(a: Actor) = this(a, null, null)

}
