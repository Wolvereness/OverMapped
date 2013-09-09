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

import static com.google.common.collect.Lists.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.maven.plugin.MojoFailureException;
import org.objectweb.asm.commons.Remapper;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.wolvereness.overmapped.asm.ByteClass;
import com.wolvereness.overmapped.asm.Signature;
import com.wolvereness.overmapped.asm.Signature.MutableSignature;

class MembersSubRoutine extends SubRoutine {
	static class Store {
		String newName;
		String oldName;
		String description;
		String originalDescription;
		final Set<String> searchCache;
		final Set<String> parents;
		Map<String, Signature> classFieldsCache;
		final OverMapped instance;

		Store(
		      final Set<String> searchCache,
		      final Set<String> parents,
		      final OverMapped instance
		      ) {
			this.searchCache = searchCache;
			this.parents = parents;
			this.instance = instance;
		}
	}

	MembersSubRoutine() {
		super("members");
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
	                   final MutableSignature mutableSignature,
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

		final Store store = new Store(
			searchCache,
			instance.isFindParents()
				? new HashSet<String>()
				: null,
			instance
			);

		for (final Map.Entry<?, ?> memberMap : ((Map<?,?>) memberMaps).entrySet()) {
			final Map<?,?> maps = asType(
				memberMap.getValue(),
				"`%4$s' points to a %2$s `%1$s', expected a %5$s, in `%3$s'",
				false,
				memberMaps,
				memberMap,
				Map.class
				);

			if (memberMap.getKey() instanceof Collection<?> && ((Collection<?>) memberMap.getKey()).size() > 1) {
				final Iterable<String> classNames; {
					final ImmutableCollection.Builder<String> containingClassNames = ImmutableList.builder();
					for (final Object clazz : (Collection<?>) memberMap.getKey()) {
						final String unresolvedClassName = asType(
							clazz,
							"`%4$s' contains a %2$s `%1$s', expected a %5$s, from `%3$s'",
							false,
							memberMaps,
							memberMap.getKey(),
							String.class
							);
						final String className = inverseNameMaps.get(unresolvedClassName);
						if (className == null) {
							instance.missingAction.actMemberClass(instance.getLog(), unresolvedClassName, memberMap.getKey(), inverseNameMaps);
							continue;
						}
						containingClassNames.add(className);
					}
					classNames = containingClassNames.build();
				}

				for (final Map.Entry<?, ?> entry : maps.entrySet()) {
					parseMapping(
						store,
						inverseMapper,
						mutableSignature,
						maps,
						entry,
						false
						);
					final String newName = store.newName, oldName = store.oldName, description = store.description, originalDescription = store.originalDescription;

					if (!mutableSignature.update("", "", description).isMethod())
						throw new MojoFailureException(String.format(
							"Malformed mapping %s for %s; can only map methods.",
							entry,
							memberMap
							));

					for (final String className : classNames) {
						updateMember(store, signatureMaps, inverseSignatureMaps, mutableSignature, oldName, newName, description, className, nameMaps, originalDescription, nameMaps.get(className));

						if (mutableSignature.isMethod()) {
							final Set<String> parents = store.parents;
							if (parents != null) {
								parents.addAll(depends.get(className));
							}
							for (final String inherited : rdepends.get(className)) {
								if (!updateMember(store, signatureMaps, inverseSignatureMaps, mutableSignature, oldName, newName, description, inherited, nameMaps, originalDescription, nameMaps.get(inherited)))
									continue;

								if (parents != null) {
									parents.addAll(depends.get(inherited));
								}
							}
						}
					}
					performParentChecks(store, nameMaps, inverseSignatureMaps, mutableSignature, classNames, newName, oldName, description, originalDescription);
					store.searchCache.clear();
				}

				continue;
			}

			if (memberMap.getKey() instanceof Collection<?> && ((Collection<?>) memberMap.getKey()).size() < 1)
				throw new MojoFailureException(String.format(
					"Malformed mapping %s -> %s",
					memberMap.getKey(),
					maps
					));

			final String unresolvedClassName = asType(
				memberMap.getKey() instanceof Collection<?>
					? ((Collection<?>) memberMap.getKey()).iterator().next()
					: memberMap.getKey(),
				"`%4$s' points from a %2$s `%1$s', expected a %5$s, in `%3$s'",
				false,
				memberMaps,
				memberMap,
				String.class
				);
			final String className = inverseNameMaps.get(unresolvedClassName);
			if (className == null) {
				instance.missingAction.actMemberClass(instance.getLog(), unresolvedClassName, memberMap.getKey(), inverseNameMaps);
				continue;
			}

			for (final Map.Entry<?, ?> entry : maps.entrySet()) {
				processSingleClassMappings(
					store,
					classes,
					depends,
					rdepends,
					nameMaps,
					signatureMaps,
					inverseSignatureMaps,
					inverseMapper,
					mutableSignature,
					maps,
					className,
					unresolvedClassName,
					entry
					);
			}
		}
	}

	private static void processSingleClassMappings(
	                                               final Store store,
	                                               final Map<String, ByteClass> classes,
	                                               final Multimap<String, String> depends,
	                                               final Multimap<String, String> rdepends,
	                                               final BiMap<String, String> nameMaps,
	                                               final BiMap<Signature, Signature> signatureMaps,
	                                               final BiMap<Signature, Signature> inverseSignatureMaps,
	                                               final Remapper inverseMapper,
	                                               final MutableSignature mutableSignature,
	                                               final Map<?, ?> maps,
	                                               final String className,
	                                               final String originalClassName,
	                                               final Map.Entry<?, ?> entry
	                                               ) throws
	                                               MojoFailureException
	                                               {
		if (entry.getValue() instanceof String) {
			parseMapping(store, inverseMapper, mutableSignature, maps, entry, true);
			final String newName = store.newName, oldName = store.oldName, description = store.description, originalDescription = store.originalDescription;
			if (description == null) {
				final Map<String, Signature> classFieldsCache = buildFieldsCache(store, classes.get(className).getLocalSignatures(), signatureMaps);
				final Signature signature = getClassField(store, classFieldsCache, oldName, originalClassName);
				if (signature == null)
					return;
				attemptFieldMap(signatureMaps, signature, mutableSignature, oldName, newName, className);
				return;
			}

			updateMember(store, signatureMaps, inverseSignatureMaps, mutableSignature, oldName, newName, description, className, nameMaps, originalDescription, originalClassName);

			if (mutableSignature.isMethod()) {
				final Set<String> parents = store.parents;
				if (parents != null) {
					parents.addAll(depends.get(className));
				}
				for (final String inherited : rdepends.get(className)) {
					if (!updateMember(store, signatureMaps, inverseSignatureMaps, mutableSignature, oldName, newName, description, inherited, nameMaps, originalDescription, nameMaps.get(inherited)))
						continue;

					if (parents != null) {
						parents.addAll(depends.get(inherited));
					}
				}
				performParentChecks(store, nameMaps, inverseSignatureMaps, mutableSignature, className, newName, oldName, description, originalDescription);
			}
			store.searchCache.clear();
		} else if (entry.getValue() instanceof Iterable) {
			final Map<String, Signature> classFieldsCache = buildFieldsCache(store, classes.get(className).getLocalSignatures(), signatureMaps);
			final Iterable<?> names = (Iterable<?>) entry.getValue();
			final List<?> oldNames;
			final int start; {
				final Map<?, ?> entryMap = asType(
					entry.getKey(),
					"`%4$s' points from a %2$s `%1$s', expected a %5$s, in `%3$s'",
					false,
					maps,
					entry,
					Map.class
					);
				if (entryMap.size() != 1)
					throw new MojoFailureException(String.format(
						"Malformed mapping `%s' to `%s' in `%s'",
						entryMap,
						entry.getValue(),
						maps
						));
				final Map.Entry<?, ?> pair = entryMap.entrySet().iterator().next();
				oldNames = asType(
					pair.getKey(),
					"`%4$s' points from a %2$s `%1$s', expected a %5$s, in `%3$s'",
					false,
					entry,
					pair,
					List.class
					);
				final String startToken = asType(
					pair.getValue(),
					"`%4$s' points to a %2$s `%1$s', expected a %5$s, in `%3$s'",
					false,
					entry,
					pair,
					String.class
					);
				start = oldNames.indexOf(startToken);
				if (start == -1)
					throw new MojoFailureException(String.format(
						"Cannot find value `%s' in `%s'",
						startToken,
						oldNames
						));
			}
			int i = start;
			for (final Object name : names) {
				if (i >= oldNames.size())
					throw new MojoFailureException(String.format(
						"Insuffient sequence length %n in `%s' for `%s' at `%s'",
						i,
						oldNames,
						name,
						names,
						name
						));
				final String newName = asType(
					name,
					"`%4$s' contains a %2$2 `%1$s', expected a %5$s, in `%3$s'",
					false,
					entry,
					names,
					String.class
					);
				final String oldName = asType(
					oldNames.get(i++),
					"`%4$s' contains a %2$2 `%1$s', expected a %5$s, in `%3$s'",
					false,
					entry,
					oldNames,
					String.class
					);
				final Signature signature = getClassField(store, classFieldsCache, oldName, originalClassName);
				if (signature == null) {
					continue;
				}

				attemptFieldMap(signatureMaps, signature, mutableSignature, oldName, newName, className);
			}
		} else
			throw new MojoFailureException(String.format(
				"Malformed mapping `%s' from `%s' in `%s'",
				entry.getValue(),
				entry.getKey(),
				maps
				));
	}

	private static Signature getClassField(
	                                       final Store store,
	                                       final Map<String, Signature> classFieldsCache,
	                                       final String oldName,
	                                       final String originalClassName
	                                       ) throws
	                                       MojoFailureException
	                                       {
		final Signature signature = classFieldsCache.get(oldName);
		if (signature != null)
			return signature;
		if (classFieldsCache.containsKey(oldName))
			throw new MojoFailureException(String.format(
				"Ambiguous field name %s",
				oldName
				));
		store.instance.missingAction.actField(store.instance.getLog(), classFieldsCache, oldName, originalClassName);
		return null;
	}

	private static void parseMapping(
	                                 final Store store,
	                                 final Remapper inverseMapper,
	                                 final MutableSignature mutableSignature,
	                                 final Map<?, ?> maps,
	                                 final Map.Entry<?, ?> entry,
	                                 boolean nullDescription
	                                 ) throws
	                                 MojoFailureException
	                                 {
		store.newName = asType(
			entry.getValue(),
			"`%4$s' points to a %2$s `%1$s', expected a %5$s, in `%3$s'",
			false,
			maps,
			entry,
			String.class
			);
		if (entry.getKey() instanceof Map && ((Map<?, ?>) entry.getKey()).size() == 1) {
			final Map.Entry<?, ?> mapKey = ((Map<?, ?>) entry.getKey()).entrySet().iterator().next();
			store.oldName = asType(
				mapKey.getKey(),
				"`%4$s' points from a %2$s `%1$s', expected a %5$s, in `%3$s'",
				false,
				entry.getKey(),
				mapKey,
				String.class
				);
			final String unresolvedDescription = store.originalDescription = asType(
				mapKey.getValue(),
				"`%4$s' points to a %2$s `%1$s', expected a %5$s, in `%3$s'",
				nullDescription,
				entry.getKey(),
				mapKey,
				String.class
				);
			store.description = parseDescription(inverseMapper, mutableSignature, unresolvedDescription);
		} else if (entry.getKey() instanceof String) {
			final String fullToken = (String) entry.getKey();
			final int split = fullToken.indexOf(' ');
			final String unresolvedDescription;
			if (nullDescription & (nullDescription = (split == -1))) {
				unresolvedDescription = null;
				store.oldName = fullToken;
			} else if (nullDescription || split != fullToken.lastIndexOf(' '))
				throw new MojoFailureException(String.format(
					"Malformed mapping %s",
					fullToken
					));
			else {
				unresolvedDescription = store.originalDescription = fullToken.substring(split + 1, fullToken.length());
				store.oldName = fullToken.substring(0, split);
			}
			store.description = parseDescription(inverseMapper, mutableSignature, unresolvedDescription);
		} else
			throw new MojoFailureException(String.format(
				"Malformed mapping `%s' to `%s' in `%s'",
				entry.getKey(),
				store.newName,
				maps
				));
	}

	private static void performParentChecks(
	                                        final Store store,
	                                        final BiMap<String, String> nameMaps,
	                                        final BiMap<Signature, Signature> inverseSignatureMaps,
	                                        final MutableSignature mutableSignature,
	                                        Object className_s,
	                                        final String newName,
	                                        final String oldName,
	                                        final String description,
	                                        final String originalDescription
	                                        ) {
		final Set<String> parents = store.parents;
		if (parents != null) {
			parents.removeAll(store.searchCache);
			if (parents.isEmpty())
				return;

			if (className_s instanceof String) {
				className_s = nameMaps.get(className_s);
			} else {
				final Collection<String> originalClassNames = newArrayList();
				for (final Object className : (Iterable<?>) className_s) {
					originalClassNames.add(nameMaps.get(className));
				}
				className_s = originalClassNames;
			}
			for (final String parent : parents) {
				if (inverseSignatureMaps.containsKey(mutableSignature.update(parent, oldName, description))) {
					store.instance.getLog().info(String.format(
						"Expected parent method mapping for `%s'->`%s' from mappings in %s",
						mutableSignature.update(nameMaps.get(parent), oldName, originalDescription),
						mutableSignature.forElementName(newName),
						className_s
						));
				}
			}
			parents.clear();
		}
	}

	private static void attemptFieldMap(
	                                    final BiMap<Signature,
	                                    Signature> signatureMaps,
	                                    final Signature signature,
	                                    final MutableSignature mutableSignature,
	                                    final String oldName,
	                                    final String newName,
	                                    final String className
	                                    ) throws
	                                    MojoFailureException
	                                    {
		try {
			signatureMaps.put(signature, signature.forElementName(newName));
		} catch (final IllegalArgumentException ex) {
			final MojoFailureException mojoEx = new MojoFailureException(String.format(
				"Cannot map %s (currently %s) to pre-existing member %s (in class %s)",
				signature,
				mutableSignature.update(className, oldName, signature.getDescriptor()),
				signature.forElementName(newName),
				className
				));
			mojoEx.initCause(ex);
			throw mojoEx;
		}
	}

	private static String parseDescription(
	                                       final Remapper inverseMapper,
	                                       final MutableSignature mutableSignature,
	                                       final String unresolvedDescription
	                                       ) {
		return unresolvedDescription != null
			? mutableSignature.update("", "", unresolvedDescription).isMethod()
				? inverseMapper.mapMethodDesc(unresolvedDescription)
				: inverseMapper.mapDesc(unresolvedDescription)
			: null;
	}

	private static Map<String, Signature> buildFieldsCache(final Store store, final List<Signature> localSignatures, final Map<Signature, Signature> signatures) {
		Map<String, Signature> classFieldsCache = store.classFieldsCache;
		if (classFieldsCache == null) {
			classFieldsCache = store.classFieldsCache = new HashMap<String, Signature>(localSignatures.size());
		} else {
			classFieldsCache.clear();
		}

		int size = 0;
		for (final Signature signature : localSignatures) {
			if (signature.isMethod()) {
				continue;
			}
			final String mappedName = signatures.get(signature).getElementName();
			if (
					classFieldsCache.put(mappedName, signature) != null
					|| size == (size = classFieldsCache.size())
					) {
				// Remove the mapping we accidentally put in...
				classFieldsCache.put(mappedName, null);
			}
		}

		return classFieldsCache;
	}

	/**
	 *
	 * @return true if cache does not contain original (or similarly interpreted as attempted to update as it has not attempted to do so thus far)
	 */
	private static boolean updateMember(
	                                    final Store store,
	                                    final BiMap<Signature, Signature> signatureMaps,
	                                    final BiMap<Signature, Signature> inverseSignatureMaps,
	                                    final Signature.MutableSignature signature,
	                                    final String oldName,
	                                    final String newName,
	                                    final String description,
	                                    final String clazz,
	                                    final Map<String, String> classes,
	                                    final String originalDescription,
	                                    final String originalClass
	                                    ) throws
	                                    MojoFailureException
	                                    {
		if (!store.searchCache.add(clazz))
			return false;

		signature.update(clazz, oldName, description);

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
					classes.get(clazz)
					));
				mojoEx.initCause(ex);
				throw mojoEx;
			}
		} else {
			store.instance.missingAction.actMember(store.instance.getLog(), originalClass, oldName, newName, originalDescription, inverseSignatureMaps);
		}
		return true;
	}
}
