/*
 * This file is part of OverMapped.
 *
 * OverMapped is free software: you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OverMapped is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with OverMapped.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.wolvereness.overmapped;

import java.util.Map;
import java.util.Set;

import org.apache.maven.plugin.MojoFailureException;
import org.objectweb.asm.commons.Remapper;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.wolvereness.overmapped.asm.ByteClass;
import com.wolvereness.overmapped.asm.Signature;

abstract class SubRoutine {
	static final Iterable<SubRoutine> SUB_ROUTINES = ImmutableList.of(
		new ClassesSubRoutine(),
		new MembersSubRoutine(),
		new FlagsSubRoutine()
		);

	final String tag;

	SubRoutine(final String tag) {
		this.tag = tag;
	}

	abstract void invoke(
	                     final OverMapped instance,
	                     final Map<String, ByteClass> classes,
	                     final Multimap<String, String> depends,
	                     final Multimap<String, String> rdepends,
	                     final BiMap<String, String> nameMaps,
	                     final BiMap<String, String> inverseNameMaps,
	                     final BiMap<Signature, Signature> signatureMaps,
	                     final BiMap<Signature, Signature> inverseSignatureMaps,
	                     final Remapper inverseMapper,
	                     final Signature.MutableSignature signature,
	                     final Set<String> searchCache,
	                     final Map<Signature, Integer> flags,
	                     final Map<?,?> flagMaps
	                     ) throws
	                     ClassCastException,
	                     NullPointerException,
	                     MojoFailureException
	                     ;

	String exceptionMessage(final Map<?, ?> bigMap) {
		return String.format(
			"Failed to parse %s: `%s' in `%s'.",
			tag,
			bigMap.get(tag),
			bigMap
			);
	}

	static <T> T asType(
	                    final Object value,
	                    final String message,
	                    final boolean acceptNull,
	                    final Object source,
	                    final Object identifier,
	                    final Class<T> clazz
	                    ) throws
	                    ClassCastException,
	                    NullPointerException
	                    {
		if (clazz.isInstance(value))
			return clazz.cast(value);
		if (acceptNull && value == null)
			return null;
		final String exceptionMessage = String.format(
			message,
			value,
			value == null ? Object.class : value.getClass(),
			source,
			identifier,
			clazz
			);
		if (value == null)
			throw new NullPointerException(exceptionMessage);
		throw new ClassCastException(exceptionMessage);
	}

	static <T> T getTypeFrom(
	                         final Map<?,?> map,
	                         final String token,
	                         final boolean acceptNull,
	                         final Class<T> clazz
	                         ) throws
	                         ClassCastException,
	                         NullPointerException
	                         {
		return asType(
			map.get(token),
			"Expected a token \"%4$s\"->%5$s in `%3s', got %2$s `%1$s'",
			acceptNull,
			map,
			token,
			clazz
			);
	}

	static String getStringFrom(
	                            final Map<?,?> map,
	                            final String token,
	                            final boolean acceptNull
	                            ) throws
	                            ClassCastException,
	                            NullPointerException
	                            {
		return getTypeFrom(map, token, acceptNull, String.class);
	}
}
