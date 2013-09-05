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
import com.google.common.collect.Multimap;
import com.wolvereness.overmapped.asm.ByteClass;
import com.wolvereness.overmapped.asm.Signature;
import com.wolvereness.overmapped.asm.Signature.MutableSignature;

class ClassesSubRoutine extends SubRoutine {

	ClassesSubRoutine() {
		super("classes");
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
		final Object classMaps = map.get(tag);
		if (!(classMaps instanceof Map))
			return;

		for (final Map.Entry<?, ?> classMap : ((Map<?,?>) classMaps).entrySet()) {
			final String originalName = ((String) classMap.getKey()).toString();
			final String newName = ((String) classMap.getValue()).toString();
			if (nameMaps.containsValue(newName))
				throw new MojoFailureException(String.format(
					"Cannot map `%s' to a duplicate entry `%s' mapped from `%s'",
					originalName,
					newName,
					inverseNameMaps.get(newName)
					));
			final String trueOriginal = inverseNameMaps.get(originalName);
			if (trueOriginal == null) {
				instance.missingAction.actClass(instance.getLog(), originalName, newName, inverseNameMaps);
			} else {
				nameMaps.put(trueOriginal, newName);
			}
		}
	}
}
