/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.ode.bpel.engine;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.ode.bpel.extensions.sync.Constants;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * 
 * WARNING --- EXPERIMENTAL
 * 
 * Mechanism for obtaining instance-level locks. Very simple implementation at
 * the moment, that is only valid for a single processing node. To move to
 * multi-processor setup we'll need to implement this lock in the database.
 * 
 * @author Maciej Szefler - m s z e f l e r @ g m a i l . c o m
 */
public class InstanceLockManager {
	private static final Log __log = LogFactory
			.getLog(InstanceLockManager.class);

	private final Lock _mutex = new java.util.concurrent.locks.ReentrantLock();
	private final Map<Long, InstanceInfo> _locks = new HashMap<Long, InstanceInfo>();

	public void lock(Long iid, int time, TimeUnit tu)
			throws InterruptedException, TimeoutException {
		if (Constants.DEBUG_LEVEL > 1) {
			System.out.println("InstanceLockManager - lock1 " + iid + " " + time + " " + tu);
		}
		if (iid == null) {
			if (Constants.DEBUG_LEVEL > 1) {
				System.out.println("InstanceLockManager - lock2 " + iid + " " + time + " " + tu);
			}
			return;
		}
		if (Constants.DEBUG_LEVEL > 1) {
			System.out.println("InstanceLockManager - lock3 " + iid + " " + time + " " + tu);
		}
		String thrd = Thread.currentThread().toString();
		if (Constants.DEBUG_LEVEL > 1) {
			System.out.println("InstanceLockManager - lock4 " + iid + " " + time + " " + tu);
		}
		if (__log.isDebugEnabled())
			__log.debug(thrd + ": lock(iid=" + iid + ", time=" + time + tu
					+ ")");

		InstanceInfo li;
		
		
		if (Constants.DEBUG_LEVEL > 1) {
			System.out.println("InstanceLockManager - lock5 " + iid + " " + time + " " + tu);
		}
		_mutex.lock();
		if (Constants.DEBUG_LEVEL > 1) {
			System.out.println("InstanceLockManager - lock6 " + iid + " " + time + " " + tu);
		}
		try {
			if (Constants.DEBUG_LEVEL > 1) {
				System.out.println("InstanceLockManager - lock7 " + iid + " " + time + " " + tu);
			}
			while (true) {
				if (Constants.DEBUG_LEVEL > 1) {
					System.out.println("InstanceLockManager - lock8 " + iid + " " + time + " " + tu);
				}
				li = _locks.get(iid);
				if (Constants.DEBUG_LEVEL > 1) {
					System.out.println("InstanceLockManager - lock9 " + iid + " " + time + " " + tu);
				}
				if (li == null) {
					if (Constants.DEBUG_LEVEL > 1) {
						System.out.println("InstanceLockManager - lock10 " + iid + " " + time + " " + tu);
					}
					li = new InstanceInfo(iid, Thread.currentThread());
					if (Constants.DEBUG_LEVEL > 1) {
						System.out.println("InstanceLockManager - lock11 " + iid + " " + time + " " + tu);
					}
					_locks.put(iid, li);
					if (Constants.DEBUG_LEVEL > 1) {
						System.out.println("InstanceLockManager - lock12 " + iid + " " + time + " " + tu);
					}
					if (__log.isDebugEnabled()) {
						__log.debug(thrd + ": lock(iid=" + iid + ", time="
								+ time + tu + ")-->GRANTED");
						if (Constants.DEBUG_LEVEL > 1) {
							System.out.println("InstanceLockManager - lock13 " + thrd + ": lock(iid=" + iid + ", time="
								+ time + tu + ")-->GRANTED");
						}
					}
					return;
				} else {
					if (__log.isDebugEnabled()) {
						__log.debug(thrd + ": lock(iid=" + iid + ", time="
								+ time + tu + ")-->WAITING(held by "
								+ li.acquierer + ")");
						if (Constants.DEBUG_LEVEL > 1) {
							System.out.println("InstanceLockManager - lock14 " + thrd + ": lock(iid=" + iid + ", time="
								+ time + tu + ")-->WAITING(held by "
								+ li.acquierer + ")");
						}
					}

					if (!li.available.await(time, tu)) {
						if (__log.isDebugEnabled())
							__log.debug(thrd + ": lock(iid=" + iid + ", time="
									+ time + tu + ")-->TIMEOUT (held by "
									+ li.acquierer + ")");
						if (Constants.DEBUG_LEVEL > 1) {
							System.out.println("InstanceLockManager - lock15 " + thrd + ": lock(iid=" + iid + ", time="
								+ time + tu + ")-->TIMEOUT (held by "
								+ li.acquierer + ")");
						}
						throw new TimeoutException();
					}
				}
			}

		} finally {
			if (Constants.DEBUG_LEVEL > 1) {
				System.out.println("InstanceLockManager - lock16 " + iid + " " + time + " " + tu);
			}
			_mutex.unlock();
			if (Constants.DEBUG_LEVEL > 1) {
				System.out.println("InstanceLockManager - lock17 " + iid + " " + time + " " + tu);
			}
		}

	}

	public void unlock(Long iid) {
		if (Constants.DEBUG_LEVEL > 1) {
			System.out.println("InstanceLockManager - unlock1 " + iid);
		}
		if (iid == null) {
			if (Constants.DEBUG_LEVEL > 1) {
				System.out.println("InstanceLockManager - unlock2 " + iid);
			}
			return;
		}

		String thrd = Thread.currentThread().toString();
		if (__log.isDebugEnabled())
			__log.debug(thrd + ": unlock(iid=" + iid + ")");
		if (Constants.DEBUG_LEVEL > 1) {
			System.out.println("InstanceLockManager - unlock3 " + iid);
		}
		_mutex.lock();
		if (Constants.DEBUG_LEVEL > 1) {
			System.out.println("InstanceLockManager - unlock4 " + iid);
		}
		try {
			if (Constants.DEBUG_LEVEL > 1) {
				System.out.println("InstanceLockManager - unlock5 " + iid);
			}
			InstanceInfo li = _locks.get(iid);
			if (Constants.DEBUG_LEVEL > 1) {
				System.out.println("InstanceLockManager - unlock6 " + iid);
			}
			if (li == null) {
				if (Constants.DEBUG_LEVEL > 1) {
					System.out.println("InstanceLockManager - unlock7 " + iid);
				}
				throw new IllegalStateException(
						"Instance not locked, cannot unlock!");
			}
			if (Constants.DEBUG_LEVEL > 1) {
				System.out.println("InstanceLockManager - unlock8 " + iid);
			}
			_locks.remove(iid);
			if (Constants.DEBUG_LEVEL > 1) {
				System.out.println("InstanceLockManager - unlock9 " + iid);
			}
			// Note, that we have to signall all threads, because new holder
			// will create a new
			// instance of "available" condition variable, so all the waiters
			// need to try again
			li.available.signalAll();
			if (Constants.DEBUG_LEVEL > 1) {
				System.out.println("InstanceLockManager - unlock10 " + iid);
			}
		} finally {
			if (Constants.DEBUG_LEVEL > 1) {
				System.out.println("InstanceLockManager - unlock11 " + iid);
			}
			_mutex.unlock();
			if (Constants.DEBUG_LEVEL > 1) {
				System.out.println("InstanceLockManager - unlock12 " + iid);
			}
		}

	}

	@Override
	public String toString() {
		return "{InstanceLockManager: " + _locks + "}";
	}

	/**
	 * Information about the lock state for a particular instance.
	 * 
	 * @author Maciej Szefler - m s z e f l e r @ g m a i l . c o m
	 */
	private class InstanceInfo {
		final long iid;

		/** Thread that acquired the lock. */
		final Thread acquierer;

		/** Condition-Variable indicating that the lock has become available. */
		Condition available = _mutex.newCondition();

		InstanceInfo(long iid, Thread t) {
			this.iid = iid;
			this.acquierer = t;
		}

		@Override
		public String toString() {
			return "{Lock for Instance #" + iid + ", acquired by " + acquierer
					+ "}";
		}
	}

	/** Exception class indicating a time-out occured while obtaining a lock. */
	public static final class TimeoutException extends Exception {
		private static final long serialVersionUID = 7247629086692580285L;
	}
}
