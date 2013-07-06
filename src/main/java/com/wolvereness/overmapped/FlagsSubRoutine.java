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
import com.wolvereness.overmapped.asm.Signature;
import com.wolvereness.overmapped.asm.Signature.MutableSignature;

class FlagsSubRoutine extends SubRoutine {

	FlagsSubRoutine() {
		super("flags");
	}

	@Override
	public void invoke(
	                   final OverMapped instance,
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
		final Object flagMaps = map.get(tag);
		if (!(flagMaps instanceof Map))
			return;

		for (final Map.Entry<?, ?> flagMap : ((Map<?,?>) flagMaps).entrySet()) {
			final String qualifiedName = SubRoutine.asType(
				flagMap.getKey(),
				"`%4$s' points from a %2$s `%1$s', expected a %5$s, in `%3$s'",
				false,
				flagMaps,
				flagMap,
				String.class
				);
			final Integer flag = SubRoutine.asType(
				flagMap.getValue(),
				"Expected a value `%4$s'->%5$s in %3%s, got %2$s `%1$s'",
				false,
				flagMaps,
				qualifiedName,
				Integer.class
				);


			final int firstSpace = qualifiedName.indexOf(' ');
			final int finalSpace = qualifiedName.lastIndexOf(' ');

			if (firstSpace == finalSpace || qualifiedName.indexOf(' ', firstSpace + 1) != finalSpace)
				throw new MojoFailureException(String.format(
					"Malformed mapping %s",
					qualifiedName
					));

			final String clazz = qualifiedName.substring(0, firstSpace);
			final String name = qualifiedName.substring(firstSpace + 1, finalSpace);
			final String description = qualifiedName.substring(finalSpace + 1);

			final String unmappedClass = inverseNameMaps.get(clazz);
			if (unmappedClass == null) {
				instance.missingAction.actFlag(instance.getLog(), flagMap, signatureMaps);
				continue;
			}
			final String unmappedDescription = inverseMapper.mapDesc(description);

			final Signature unmappedSignature = inverseSignatureMaps.get(signature.update(unmappedClass, name, unmappedDescription));
			if (unmappedSignature == null) {
				instance.missingAction.actFlag(instance.getLog(), flagMap, signatureMaps);
				continue;
			}

			flags.put(unmappedSignature, flag);
		}
	}
}
