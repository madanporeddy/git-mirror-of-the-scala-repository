package scala.actors
package scheduler

import java.lang.Thread.State
import java.util.{Collection, ArrayList}
import scala.concurrent.forkjoin._

/** The <code>ForkJoinScheduler</code> is backed by a lightweight
 *  fork-join task execution framework.
 *
 * @author Philipp Haller
 */
class ForkJoinScheduler(val initCoreSize: Int, val maxSize: Int, daemon: Boolean) extends Runnable with IScheduler with TerminationMonitor {

  private var pool = makeNewPool()
  private var terminating = false
  private var snapshoting = false

  // this has to be a java.util.Collection, since this is what
  // the ForkJoinPool returns.
  private var drainedTasks: Collection[ForkJoinTask[_]] = null

  protected val CHECK_FREQ = 10

  def this(d: Boolean) {
    this(ThreadPoolConfig.corePoolSize, ThreadPoolConfig.maxPoolSize, d)
  }

  def this() {
    this(false)
  }

  private def makeNewPool(): DrainableForkJoinPool = {
    val p = new DrainableForkJoinPool()
    // enable locally FIFO scheduling mode
    p.setAsyncMode(true)
    p.setParallelism(initCoreSize)
    p.setMaximumPoolSize(maxSize)
    Debug.info(this+": parallelism "+p.getParallelism())
    Debug.info(this+": max pool size "+p.getMaximumPoolSize())
    p
  }

  /** Starts this scheduler.
   */
  def start() {
    try {
      val t = new Thread(this)
      t.setDaemon(daemon)
      t.setName("ForkJoinScheduler")
      t.start()
    } catch {
      case e: Exception =>
        Debug.info(this+": could not create scheduler thread: "+e)
    }
  }

  private def allWorkersBlocked: Boolean =
    (pool.workers != null) &&
    pool.workers.forall(t => {
      (t == null) || {
        val s = t.getState()
        s == State.BLOCKED || s == State.WAITING || s == State.TIMED_WAITING
      }
    })

  override def run() {
    try {
      while (true) {
        this.synchronized {
          try {
            wait(CHECK_FREQ)
          } catch {
            case _: InterruptedException =>
          }

          if (terminating)
            throw new QuitException

          if (allTerminated) {
            //Debug.info(this+": all actors terminated")
            throw new QuitException
          }

          if (!snapshoting) {
            gc()

            // check if we need more threads to avoid deadlock
            val poolSize = pool.getPoolSize()
            if (allWorkersBlocked && (poolSize < ThreadPoolConfig.maxPoolSize)) {
              pool.setParallelism(poolSize + 1)
            }
          } else if (pool.isQuiescent()) {
            val list = new ArrayList[ForkJoinTask[_]]
            val num = pool.drainTasksTo(list)
            Debug.info(this+": drained "+num+" tasks")
            drainedTasks = list
            throw new QuitException
          }
        }
      }
    } catch {
      case _: QuitException =>
        Debug.info(this+": initiating shutdown...")
        while (!pool.isQuiescent()) {
          try {
            Thread.sleep(10)
          } catch {
            case ignore: InterruptedException =>
          }
        }
        pool.shutdown()
        // allow thread to exit
    }
  }

  // TODO: when do we pass a task that is not a RecursiveAction?
  def execute(task: Runnable) {
    pool.execute(task)
  }

  override def executeFromActor(task: Runnable) {
    // TODO: only pass RecursiveAction (with Runnable), and cast to it
    val recAction = new RecursiveAction {
      def compute() = task.run()
    }
    recAction.fork()
  }

  /** Submits a closure for execution.
   *
   *  @param  fun  the closure to be executed
   */
  def execute(fun: => Unit): Unit =
    execute(new Runnable {
      def run() { fun }
    })

  /** Shuts down the scheduler.
   */
  def shutdown(): Unit = synchronized {
    terminating = true
  }

  def isActive =
    (pool ne null) && !pool.isShutdown()

  override def managedBlock(blocker: scala.concurrent.ManagedBlocker) {
    ForkJoinPool.managedBlock(new ForkJoinPool.ManagedBlocker {
      def block = blocker.block()
      def isReleasable() = blocker.isReleasable
    }, true)
  }

  /** Suspends the scheduler. All threads that were in use by the
   *  scheduler and its internal thread pool are terminated.
   */
  def snapshot() = synchronized {
    snapshoting = true
  }

  /** Resumes the execution of the scheduler if it was previously
   *  suspended using <code>ForkJoinScheduler.snapshot</code>.
   */
  def restart() {
    synchronized {
      if (!snapshoting)
        error("snapshot has not been invoked")
      else if (isActive)
        error("scheduler is still active")
      else
        snapshoting = false
    }
    pool = makeNewPool()
    val iter = drainedTasks.iterator()
    while (iter.hasNext()) {
      pool.execute(iter.next())
    }
    start()
  }

}
