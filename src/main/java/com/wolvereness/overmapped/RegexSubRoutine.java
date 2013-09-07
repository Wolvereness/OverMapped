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

import static com.google.common.collect.Maps.*;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.apache.maven.plugin.MojoFailureException;
import org.objectweb.asm.commons.Remapper;

import com.google.common.collect.BiMap;
import com.google.common.collect.Multimap;
import com.wolvereness.overmapped.asm.ByteClass;
import com.wolvereness.overmapped.asm.Signature;
import com.wolvereness.overmapped.asm.Signature.MutableSignature;

class RegexSubRoutine extends SubRoutine {

	RegexSubRoutine() {
		super("regex");
	}

	@Override
	public void invoke(
	                   final OverMapped instance,
	                   final Map<String, ByteClass> classes,
	                   final Multimap<String, String> depends,
	                   final Multimap<String, String> rdepends,
	                   final BiMap<String, String> nameMaps,
	                   final BiMap<String, String> inverseNameMaps,
	                   final BiMap<Signature, Signature> signatureMaps,
	                   final BiMap<Signature, Signature> inverseSignatureMaps,
	                   final Remapper inverseMapper,
	                   final MutableSignature signature,
	                   final Set<String> searchCache,
	                   final Map<Signature, Integer> flags,
	                   final Map<?,?> map
	                   ) throws
	                   ClassCastException,
	                   NullPointerException,
	                   MojoFailureException
	                   {
		final Object regexMaps = map.get(tag);
		if (!(regexMaps instanceof Map))
			return;

		final Map<String, String> regexNameMaps = newHashMap();
		for (final Map.Entry<?, ?> regexMap : ((Map<?,?>) regexMaps).entrySet()) {
			{
				final Pattern regex; {
					final String regexString = asType(
						regexMap.getKey(),
						"`%4$s' points from a %2$s `%1$s', expected a %5$s, in `%3$s'",
						false,
						regexMaps,
						regexMap,
						String.class
						);
					try {
						regex = Pattern.compile(regexString, Pattern.DOTALL);
					} catch (final PatternSyntaxException ex) {
						final MojoFailureException exception = new MojoFailureException(String.format(
							"Failed to parse regex `%s' key in `%s' of `%s'",
							regexString,
							regexMap,
							regexMaps
							));
						exception.initCause(ex);
						throw exception;
					}
				}
				final String replacement = asType(
					regexMap.getValue(),
					"Expected a value `%4$s'->%5$s in %3%s, got %2$s `%1$s'",
					false,
					regexMaps,
					regexMap,
					String.class
					);

				for (final Iterator<Map.Entry<String, String>> it = nameMaps.entrySet().iterator(); it.hasNext(); ) {
					final Map.Entry<String, String> classMap = it.next();
					final String oldName = classMap.getValue();
					final String newName = regex.matcher(oldName).replaceAll(replacement);
					if (!oldName.equals(newName)) {
						regexNameMaps.put(oldName, newName);
						it.remove(); // Insert them back later; this is to prevent class name-switching state issues
					}
				}
			}

			for (final Map.Entry<String, String> regexNameMap : regexNameMaps.entrySet()) {
				try {
					nameMaps.put(regexNameMap.getKey(), regexNameMap.getValue());
				} catch (final IllegalArgumentException ex) {
					final MojoFailureException exception = new MojoFailureException(String.format(
						"Failed to parse regex entry `%s' in `%s';  failed to insert `%s' into name maps for `%s'",
						regexMap,
						regexMaps,
						regexNameMap,
						regexNameMaps
						));
					exception.initCause(ex);
					throw exception;
				}
			}

			regexNameMaps.clear();
		}
	}
}
