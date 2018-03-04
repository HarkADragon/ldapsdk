/*
 * Copyright 2018 Ping Identity Corporation
 * All Rights Reserved.
 */
/*
 * Copyright (C) 2018 Ping Identity Corporation
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License (GPLv2 only)
 * or the terms of the GNU Lesser General Public License (LGPLv2.1 only)
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 */
package com.unboundid.util;



import java.io.Closeable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import static com.unboundid.util.UtilityMessages.*;



/**
 * This class provides an implementation of a reentrant lock that can be used
 * with the Java try-with-resources facility.  It does not implement the
 * {@code java.util.concurrent.locks.Lock} interface in order to ensure that it
 * can only be used through lock-with-resources mechanism, but it uses a
 * {@code java.util.concurrent.locks.ReentrantLock} behind the scenes to provide
 * its functionality.
 * <BR><BR>
 * <H2>Example</H2>
 * The following example demonstrates how to use this lock using the Java
 * try-with-resources facility:
 * <PRE>
 * // Wait for up to 5 seconds to acquire the lock.
 * try (final CloseableLock.Lock lock =
 *           closeableLock.tryLock(5L, TimeUnit.SECONDS))
 * {
 *   // Do something while the lock is held.  The lock will automatically be
 *   // released once code execution leaves this block.
 * }
 * catch (final InterruptedException e)
 * {
 *   // The thread was interrupted before the lock could be acquired.
 * }
 * catch (final TimeoutException)
 * {
 *   // The lock could not be acquired within the specified 5-second timeout.
 * }
 * </PRE>
 */
@Mutable()
@ThreadSafety(level=ThreadSafetyLevel.COMPLETELY_THREADSAFE)
public final class CloseableLock
{
  // The {@code Closeable} object that will be returned by all of the methods
  // used to acquire the lock.
  private final Lock lock;

  // The reentrant lock that will be used to actually perform the locking.
  private final ReentrantLock reentrantLock;



  /**
   * Creates a new instance of this lock with a non-fair ordering policy.
   */
  public CloseableLock()
  {
    this(false);
  }



  /**
   * Creates a new instance of this lock with the specified ordering policy.
   *
   * @param  fair  Indicates whether the lock should use fair ordering.  If
   *               {@code true}, then if multiple threads are waiting on the
   *               lock, then the one that has been waiting the longest is the
   *               one that will get it.  If {@code false}, then no guarantee
   *               will be made about the order.  Fair ordering can incur a
   *               performance penalty.
   */
  public CloseableLock(final boolean fair)
  {
    reentrantLock = new ReentrantLock(fair);
    lock = new Lock(reentrantLock);
  }



  /**
   * Acquires this lock, blocking until the lock is available.
   *
   * @return  The {@link Lock} instance that may be used to perform the
   *          unlock via the try-with-resources facility.
   */
  public Lock lock()
  {
    reentrantLock.lock();
    return lock;
  }



  /**
   * Acquires this lock, blocking until the lock is available.
   *
   * @return  The {@link Lock} instance that may be used to perform the unlock
   *          via the try-with-resources facility.
   *
   * @throws  InterruptedException  If the thread is interrupted while waiting
   *                                to acquire the lock.
   */
  public Lock lockInterruptibly()
         throws InterruptedException
  {
    reentrantLock.lockInterruptibly();
    return lock;
  }



  /**
   * Tries to acquire the lock, waiting up to the specified length of time for
   * it to become available.
   *
   * @param  waitTime  The maximum length of time to wait for the lock.  It must
   *                   be greater than zero.
   * @param  timeUnit  The time unit that should be used when evaluating the
   *                   {@code waitTime} value.
   *
   * @return  The {@link Lock} instance that may be used to perform the unlock
   *          via the try-with-resources facility.
   *
   * @throws  InterruptedException  If the thread is interrupted while waiting
   *                                to acquire the lock.
   *
   * @throws  TimeoutException  If the lock could not be acquired within the
   *                            specified length of time.
   */
  public Lock tryLock(final long waitTime, final TimeUnit timeUnit)
         throws InterruptedException, TimeoutException
  {
    if (waitTime <= 0)
    {
      Validator.violation(
           "CloseableLock.tryLock.waitTime must be greater than zero.  The " +
                "provided value was " + waitTime);
    }

    if (reentrantLock.tryLock(waitTime, timeUnit))
    {
      return lock;
    }
    else
    {
      throw new TimeoutException(ERR_CLOSEABLE_LOCK_TRY_LOCK_TIMEOUT.get(
           StaticUtils.millisToHumanReadableDuration(
                timeUnit.toMillis(waitTime))));
    }
  }



  /**
   * Indicates whether this lock uses fair ordering.
   *
   * @return  {@code true} if this lock uses fair ordering, or {@code false} if
   *          not.
   */
  public boolean isFair()
  {
    return reentrantLock.isFair();
  }



  /**
   * Indicates whether this lock is currently held by any thread.
   *
   * @return  {@code true} if this lock is currently held by any thread, or
   *          {@code false} if not.
   */
  public boolean isLocked()
  {
    return reentrantLock.isLocked();
  }



  /**
   * Indicates whether this lock is currently held by the current thread.
   *
   * @return  {@code true} if this lock is currently held by the current thread,
   *          or {@code false} if not.
   */
  public boolean isHeldByCurrentThread()
  {
    return reentrantLock.isHeldByCurrentThread();
  }



  /**
   * Retrieves the number of holds that the current thread has on the lock.
   *
   * @return  The number of holds that the current thread has on the lock.
   */
  public int getHoldCount()
  {
    return reentrantLock.getHoldCount();
  }



  /**
   * Indicates whether any threads are currently waiting to acquire this lock.
   *
   * @return  {@code true} if any threads are currently waiting to acquire this
   *          lock, or {@code false} if not.
   */
  public boolean hasQueuedThreads()
  {
    return reentrantLock.hasQueuedThreads();
  }



  /**
   * Indicates whether the specified thread is currently waiting to acquire this
   * lock, or {@code false} if not.
   *
   * @param  thread  The thread for which to make the determination.  It must
   *                 not be {@code null}.
   *
   * @return  {@code true} if the specified thread is currently waiting to
   *          acquire this lock, or {@code false} if not.
   */
  public boolean hasQueuedThread(final Thread thread)
  {
    Validator.ensureNotNull(thread);

    return reentrantLock.hasQueuedThread(thread);
  }



  /**
   * Retrieves the number of threads currently waiting to acquire this lock.
   *
   * @return  The number of threads currently waiting to acquire this lock.
   */
  public int getQueueLength()
  {
    return reentrantLock.getQueueLength();
  }



  /**
   * Retrieves a string representation of this lock.
   *
   * @return  A string representation of this lock.
   */
  @Override()
  public String toString()
  {
    return "CloseableLock(lock=" + reentrantLock.toString() + ')';
  }



  /**
   * This class provides a {@code Closeable} implementation that may be used to
   * unlock a {@link CloseableLock} via Java's try-with-resources
   * facility.
   */
  public final class Lock
         implements Closeable
  {
    // The associated reentrant lock.
    private final ReentrantLock lock;



    /**
     * Creates a new instance with the provided lock.
     *
     * @param  lock  The lock that will be unlocked when the [@link #close()}
     *               method is called.  This must not be {@code null}.
     */
    private Lock(final ReentrantLock lock)
    {
      this.lock = lock;
    }



    /**
     * Unlocks the associated lock.
     */
    @Override()
    public void close()
    {
      lock.unlock();
    }
  }
}