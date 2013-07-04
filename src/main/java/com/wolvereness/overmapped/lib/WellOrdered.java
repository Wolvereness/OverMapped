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

import static com.google.common.collect.Lists.*;
import static com.google.common.collect.Maps.*;
import static com.google.common.collect.Sets.*;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.Validate;

/**
 * Class to facilitate the ordering of elements, by defining their relations.
 */
public final class WellOrdered {

	public static class WellOrderedException extends Exception {
		private static final long serialVersionUID = 1L;

		WellOrderedException() {
			super();
		}

		WellOrderedException(final String message, final Throwable cause) {
			super(message, cause);
		}

		WellOrderedException(final String message) {
			super(message);
		}

		WellOrderedException(final Throwable cause) {
			super(cause);
		}
	}

	public static class CircularOrderException extends WellOrderedException {
		private static final long serialVersionUID = 1L;

		CircularOrderException() {
			super();
		}

		CircularOrderException(final String message, final Throwable cause) {
			super(message, cause);
		}

		CircularOrderException(final String message) {
			super(message);
		}

		CircularOrderException(final Throwable cause) {
			super(cause);
		}
	}

	public static class UnmetPrecedingTokenException extends WellOrderedException {
		private static final long serialVersionUID = 1L;

		UnmetPrecedingTokenException() {
			super();
		}

		UnmetPrecedingTokenException(final String message, final Throwable cause) {
			super(message, cause);
		}

		UnmetPrecedingTokenException(final String message) {
			super(message);
		}

		UnmetPrecedingTokenException(final Throwable cause) {
			super(cause);
		}
	}

	public interface Informer<T> {
		/**
		 * This method is to gather all tokens that must precede the specified
		 * token. A token added that cannot precede the token parameter will
		 * cause the {@link WellOrdered#process(List, Iterable, Informer)
		 * process} to fail quickly with an {@link
		 * UnmetPrecedingTokenException}.
		 *
		 * @param token the token to use as baseline
		 * @param of the collection to add ass preceding elements to
		 */
		void addPrecedingTo(T token, Collection<? super T> of);

		/**
		 * This method is to gather all tokens that should precede the
		 * specified token.
		 *
		 * @param token the token to use as baseline
		 * @param of the collection to add ass preceding elements to
		 */
		void addPrecedingPreferencesTo(T token, Collection<? super T> of);

		/**
		 * This method is to gather all tokens that should proceed the
		 * specified token.
		 *
		 * @param token the token to use as baseline
		 * @param of the collection to add ass preceding elements to
		 */
		void addProceedingPreferencesTo(T token, Collection<? super T> of);
	}

	/**
	 * This class provides a base implementation for the three methods located
	 * in {@link Informer}.
	 *
	 * @param <T> Type being ordered referenced
	 */
	public static abstract class AbstractInformer<T> implements Informer<T> {

		/**
		 * Performs nothing.
		 * <p>
		 * {@inheritDoc}
		 */
		@Override
		public void addPrecedingTo(final T token, final Collection<? super T> of) {}

		/**
		 * Performs nothing.
		 * <p>
		 * {@inheritDoc}
		 */
		@Override
		public void addPrecedingPreferencesTo(final T token, final Collection<? super T> of) {}

		/**
		 * Performs nothing.
		 * <p>
		 * {@inheritDoc}
		 */
		@Override
		public void addProceedingPreferencesTo(final T token, final Collection<? super T> of) {}
	}

	private WellOrdered() {}

	private static <T> void addToAsLinkedList(
	                                          final T token,
	                                          final Map<T, Collection<T>> map,
	                                          final Collection<T> tokens
	                                          ) {
		Collection<T> c = map.get(token);
		if (c == null) {
			map.put(token, c = newLinkedList());
		}
		c.addAll(tokens);
	}

	private static <T> void addToAllLinkedLists(
	                                            final Collection<T> tokens,
	                                            final Map<T, Collection<T>> map,
	                                            final T token
	                                            ) {
		for (final T target : tokens) {
			Collection<T> c = map.get(target);
			if (c == null) {
				map.put(target, c = newLinkedList());
			}
			c.add(token);
		}
	}

	private static <T> boolean handleTokens(
	                                        final T token,
	                                        final Map<T, Collection<T>> map,
	                                        final Set<T> in
	                                        ) {
		final Iterator<T> it = getIterator(token, map);
		if (it == null)
			return true;

		while (it.hasNext()) {
			if (in.contains(it.next()))
				return false;

			// Short the search for next time
			it.remove();
		}

		// Short the search further for next time as collection is empty
		map.remove(token);
		return true;
	}

	private static <T> Iterator<T> getIterator(final T token, final Map<T, Collection<T>> map) {
		final Collection<T> c = map.get(token);
		if (c == null)
			return null;
		return c.iterator();
	}

	public static <T, C extends List<? super T>> C process(
	                                                       final C out,
	                                                       final Iterable<? extends T> in,
	                                                       final Informer<T> informer
	                                                       ) throws
	                                                       WellOrderedException
	                                                       {
		Validate.notNull(     out, "Collection out cannot be null");
		Validate.notNull(      in, "Token in cannot be null");
		Validate.notNull(informer, "Informer cannot be null");

		final Map<T, Collection<T>> preceding = newHashMap();
		final Map<T, Collection<T>> required  = newHashMap();
		final Set<T>                pending   = newLinkedHashSet(in);

		{ // Preprocessing of information from specified informer
			final List<T> buffer = newArrayList();
			for (final T token : pending) {

				// Preferred preceding elements
				informer.addPrecedingPreferencesTo(token, buffer);
				addToAsLinkedList(token, preceding, buffer);
				buffer.clear();

				// Required preceding elements
				informer.addPrecedingTo(token, buffer);
				if (!pending.containsAll(buffer))
					throw new UnmetPrecedingTokenException(token + " cannot be proceded by one of " + buffer + " with only " + pending + " available");
				addToAsLinkedList(token, required, buffer);
				buffer.clear();

				// Preferred proceeding elements
				informer.addProceedingPreferencesTo(token, buffer);
				addToAllLinkedLists(buffer, preceding, token);
				buffer.clear();
			}
		}

		int size = pending.size();
		while (size != 0) {

			{ // Start normal processing
				final Iterator<T> tokenIterator = pending.iterator();
				while (tokenIterator.hasNext()) {
					final T token = tokenIterator.next();
					if (
							// Use preceding as primary/first check;
							// required is covered by the fall-back
							handleTokens(token, preceding, pending)
							&& handleTokens(token, required, pending)
							) {
						tokenIterator.remove();
						out.add(token);
					}
				}
			}

			if (size == (size = pending.size())) {
				// Fall-back situation when we can't find a token that's ready
				final Iterator<T> tokenIterator = pending.iterator();
				while (tokenIterator.hasNext()) {
					final T token = tokenIterator.next();
					// At this point, we ignore preferences
					if (handleTokens(token, required, pending)) {
						tokenIterator.remove();
						preceding.remove(token);
						out.add(token);
						break;
					}
				}

				if (size == (size = pending.size())) {
					// We made no progress; it's circular
					break;
				}
			}
		}

		if (size != 0)
			throw new CircularOrderException("Failed to resolve circular preceding requirements in " + required);

		return out;
	}
}
