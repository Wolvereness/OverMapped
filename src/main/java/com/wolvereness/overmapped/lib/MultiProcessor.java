/*
 * This file is part of wolvereness-commons.
 *
 * wolvereness-commons is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * wolvereness-commons is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with wolvereness-commons.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.wolvereness.overmapped.lib;

import java.util.Collection;
import java.util.Iterator;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.apache.commons.lang3.Validate;

import com.google.common.collect.ImmutableList;

public abstract class MultiProcessor {
	volatile boolean shutdown;

	public static MultiProcessor newMultiProcessor(final int threads, final ThreadFactory factory) {
		Validate.isTrue(threads >= 0, "Cannot have negative threads");
		return threads == 0 ? new SingletonProcessor() : new ProperProcessor(threads, factory);

	}

	public void shutdown() {
		shutdown = true;
	}

	final void checkShutdown() {
		if (MultiProcessor.this.shutdown)
			throw new IllegalStateException("Cannot submit tasks to shutdown processor");
	}

	public abstract <T> Future<T> submit(final Callable<T> task);
}

final class SingletonProcessor extends MultiProcessor {

	@Override
	public <T> Future<T> submit(final Callable<T> task) {
		super.checkShutdown();
		try {
			final T object = task.call();
			return new Future<T>()
				{
					@Override
					public boolean cancel(final boolean mayInterruptIfRunning) {
						return false;
					}

					@Override
					public boolean isCancelled() {
						return false;
					}

					@Override
					public boolean isDone() {
						return true;
					}

					@Override
					public T get() throws InterruptedException, ExecutionException {
						return object;
					}

					@Override
					public T get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
						return object;
					}
				};
		} catch (final Exception exception) {
			final Exception ex = exception;
			return new Future<T>()
				{
					@Override
					public boolean cancel(final boolean mayInterruptIfRunning) {
						return false;
					}

					@Override
					public boolean isCancelled() {
						return false;
					}

					@Override
					public boolean isDone() {
						return true;
					}

					@Override
					public T get() throws InterruptedException, ExecutionException {
						throw new ExecutionException(ex);
					}

					@Override
					public T get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
						throw new ExecutionException(ex);
					}
				};
		}
	}
}

final class ProperProcessor extends MultiProcessor {
	@SuppressWarnings("unchecked")
	static final AtomicIntegerFieldUpdater<Task<?>> STATE_UPDATER = (AtomicIntegerFieldUpdater<Task<?>>) (Object) AtomicIntegerFieldUpdater.newUpdater(Task.class, "state");
	class Task<T> implements Future<T> {
		private static final int READY = 0;
		private static final int PROCESSING = READY + 1;
		private static final int WAITING = PROCESSING + 1;
		private static final int DONE = WAITING + 1;
		private static final int EXCEPTION = DONE + 1;
		private static final int CANCELLED = EXCEPTION + 1;
		final Callable<T> callable;
		private Throwable exception;
		private T value;
		volatile int state;

		Task(final Callable<T> callable) {
			this.callable = callable;
		}

		@Override
		public boolean cancel(final boolean mayInterruptIfRunning) {
			return STATE_UPDATER.compareAndSet(this, READY, CANCELLED);
		}

		@Override
		public boolean isCancelled() {
			return state == CANCELLED;
		}

		@Override
		public boolean isDone() {
			final int state = this.state;
			return state == DONE || state == CANCELLED;
		}

		@Override
		public T get() throws InterruptedException, ExecutionException, CancellationException {
			while (true) {
				switch (this.state) {
					case DONE:
						return value;
					case EXCEPTION:
						throw new ExecutionException(exception);
					case CANCELLED:
						throw new CancellationException();
					case PROCESSING:
						if (!STATE_UPDATER.compareAndSet(this, PROCESSING, WAITING)) {
							continue;
						}
						// We transition ourselves (without loop) from PROCESSING -> WAITING
					case WAITING:
						impatientlyWait();
						continue;
					case READY:
						handle();
						continue;
					default:
						throw new AssertionError("Unexpected value of state: " + this.state);
				}
			}
		}

		void handle() {
			if (STATE_UPDATER.compareAndSet(this, READY, PROCESSING)) {
				calculate();
			}
		}

		private void calculate() {
			try {
				this.value = callable.call();
				if (!STATE_UPDATER.compareAndSet(this, PROCESSING, DONE)) {
					notifyWaitting(DONE);
				}
			} catch (final Throwable ex) {
				this.exception = ex;
				if (!STATE_UPDATER.compareAndSet(this, PROCESSING, EXCEPTION)) {
					notifyWaitting(EXCEPTION);
				}
			}
		}

		private void impatientlyWait() throws InterruptedException {
			final Queue<Task<?>> queue = ProperProcessor.this.queue;
			while (true) {
				if (state != WAITING)
					return;

				final Task<?> t = queue.poll();
				if (t != null) {
					t.handle();
				} else {
					break;
				}
			}

			patientlyWait();
		}

		private synchronized void patientlyWait() throws InterruptedException {
			while (state == WAITING) {
				this.wait();
			}
		}

		private synchronized void notifyWaitting(final int newState) {
			this.state = newState;
			this.notifyAll();
		}

		@Override
		public T get(final long timeout, final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
			throw new UnsupportedOperationException("Cannot time waiting for a MultiProcessor");
		}
	}

	private final BlockingQueue<Task<?>> queue = new LinkedBlockingQueue<Task<?>>();
	private final Collection<Thread> threads;

	public ProperProcessor(int threadCount, final ThreadFactory factory) {
		final ImmutableList.Builder<Thread> threads = ImmutableList.builder();
		while (threadCount-- >= 1) {
			final Thread thread = factory.newThread(
				new Runnable()
					{
						@Override
						public void run() {
							final BlockingQueue<Task<?>> queue = ProperProcessor.this.queue;
							try {
								while (!shutdown) {
									queue.take().handle();
								}
							} catch (final InterruptedException ex) {
								if (!shutdown)
									throw new IllegalStateException("Interrupted innappropriately", ex);
							}
						}
					}
				);
			threads.add(thread);
		}

		final Iterator<Thread> it = (this.threads = threads.build()).iterator();
		final Thread initial = it.next();
		if (it.hasNext()) {
			submit(new Callable<Object>()
				{
					@Override
					public Object call() throws Exception {
						while (it.hasNext()) {
							it.next().start();
						}
						return null;
					}
				});
		}
		initial.start();
	}

	@Override
	public void shutdown() {
		if (shutdown)
			return;
		super.shutdown();

		final Queue<Task<?>> queue = this.queue;
		while (true) {
			final Task<?> task = queue.poll();
			if (task != null) {
				task.cancel(false);
			} else {
				break;
			}
		}
		for (final Thread thread : threads) {
			thread.interrupt();
		}
	}

	@Override
	public <T> Future<T> submit(final Callable<T> callable) {
		super.checkShutdown();
		final Task<T> task = new Task<T>(callable);
		queue.add(task);
		return task;
	}
}
