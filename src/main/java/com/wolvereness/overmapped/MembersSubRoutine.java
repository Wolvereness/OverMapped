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

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.maven.plugin.MojoFailureException;
import org.objectweb.asm.commons.Remapper;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.wolvereness.overmapped.asm.Signature;
import com.wolvereness.overmapped.asm.Signature.MutableSignature;

class MembersSubRoutine extends SubRoutine {

	MembersSubRoutine() {
		super("members");
	}

	@Override
	public void invoke(
	                   final OverMapped instance,
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
		final Object memberMaps = map.get(tag);
		if (!(memberMaps instanceof Map))
			return;

		for (final Map.Entry<?, ?> memberMap : ((Map<?,?>) memberMaps).entrySet()) {
			final Object key = memberMap.getKey();
			final Collection<String> classes;
			final String name;
			final String newName;
			final String description;

			if (key instanceof Iterable) {
				final ImmutableCollection.Builder<String> classesBuilder = ImmutableList.builder();
				for (final Object clazz : (Iterable<?>) key) {
					classesBuilder.add(asType(
						clazz,
						"`%4$s' contains a %2$s `%1$s', expected a %5$s, from `%3$s'",
						false,
						memberMaps,
						key,
						String.class
						));
				}
				classes = classesBuilder.build();

				final Map<?,?> information = asType(
					memberMap.getValue(),
					"Expected a value `%4$s'->%5$s in %3%s, got %2$s `%1$s'",
					false,
					memberMaps,
					key,
					Map.class
					);
				name = getStringFrom(information, "name", false);
				newName = getStringFrom(information, "map", false);
				description = getStringFrom(information, "description", true);
			} else {
				final String qualifiedName = asType(
					key,
					"`%4$s' points from a %2$s `%1$s', expected a %5$s, in `%3$s'",
					false,
					memberMaps,
					memberMap,
					String.class
					);
				final int firstSpace = qualifiedName.indexOf(' ');
				final int finalSpace = qualifiedName.lastIndexOf(' ');

				if (firstSpace == finalSpace) {
					if (firstSpace == -1)
						throw new MojoFailureException(String.format(
							"Malformed mapping %s",
							qualifiedName
							));
					classes = ImmutableList.of(qualifiedName.substring(0, firstSpace));
					name = qualifiedName.substring(finalSpace + 1);
					description = null;
				} else if (qualifiedName.indexOf(' ', firstSpace + 1) != finalSpace)
					throw new MojoFailureException(String.format(
						"Malformed mapping %s",
						qualifiedName
						));
				else {
					classes = ImmutableList.of(qualifiedName.substring(0, firstSpace));
					name = qualifiedName.substring(firstSpace + 1, finalSpace);
					description = qualifiedName.substring(finalSpace + 1);
				}

				newName = asType(
					memberMap.getValue(),
					"`%4$s' points from a %2$s `%1$s', expected a %5$s, in `%3$s'",
					false,
					memberMaps,
					memberMap,
					String.class
					);
			}

			if (description == null) {
				instance.getLog().warn(String.format( // TODO
					"Wildcard description matching not yet implemented - %s %s->%s",
					classes,
					name,
					newName
					));
				continue;
			}

			final String unmappedDescription = inverseMapper.mapDesc(description);

			for (final String clazz : classes) {
				final String original = inverseNameMaps.get(clazz);
				if (original == null) {
					instance.missingAction.actMember(instance.getLog(), clazz, name, newName, description, inverseSignatureMaps);
					continue;
				}

				if (!updateMember(instance, searchCache, signatureMaps, inverseSignatureMaps, signature, name, newName, description, unmappedDescription, clazz, original)) {
					continue;
				}

				if (signature.isMethod()) {
					for (final String inherited : rdepends.get(original)) {
						updateMember(instance, searchCache, signatureMaps, inverseSignatureMaps, signature, name, newName, description, unmappedDescription, nameMaps.get(inherited), inherited);
					}
				}
			}

			searchCache.clear();
		}
	}


	private boolean updateMember(
	                             final OverMapped instance,
	                             final Set<String> cache,
	                             final BiMap<Signature, Signature> signatureMaps,
	                             final BiMap<Signature, Signature> inverseSignatureMaps,
	                             final Signature.MutableSignature signature,
	                             final String name,
	                             final String newName,
	                             final String description,
	                             final String unmappedDescription,
	                             final String clazz,
	                             final String original
	                             ) throws
	                             MojoFailureException
	                             {
		if (!cache.add(original))
			return false;

		signature.update(original, name, unmappedDescription);

		final Signature originalSignature = inverseSignatureMaps.get(signature);
		if (originalSignature != null) {
			try {
				signatureMaps.put(originalSignature, signature.forElementName(newName));
			} catch (final IllegalArgumentException ex) {
				final MojoFailureException mojoEx = new MojoFailureException(String.format(
					"Cannot map %s (currently %s) to pre-existing member %s (in class %s)",
					originalSignature,
					signature,
					signature.forElementName(newName),
					clazz
					));
				mojoEx.initCause(ex);
				throw mojoEx;
			}
		} else {
			instance.missingAction.actMember(instance.getLog(), clazz, name, newName, description, inverseSignatureMaps);
		}
		return true;
	}
}
