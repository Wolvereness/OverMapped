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
import static com.google.common.collect.Maps.*;
import static com.google.common.collect.Sets.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.Thread.UncaughtExceptionHandler;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.objectweb.asm.commons.Remapper;
import org.yaml.snakeyaml.Yaml;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.wolvereness.overmapped.asm.ByteClass;
import com.wolvereness.overmapped.asm.Signature;
import com.wolvereness.overmapped.lib.MultiProcessor;
import com.wolvereness.overmapped.lib.WellOrdered;
import com.wolvereness.overmapped.lib.WellOrdered.CircularOrderException;
import com.wolvereness.overmapped.lib.WellOrdered.WellOrderedException;

@Mojo(name="map")
public class OverMapped extends AbstractMojo implements UncaughtExceptionHandler {

	@Parameter(required=true, property="mapping.maps")
	private File maps;

	@Parameter(required=true, property="mapping.input")
	private File input;

	@Parameter(required=false, property="mapping.output")
	private File output;

	@Parameter(required=false, property="mapping.original")
	private File original;

	@Parameter(defaultValue="2", property="mapping.cores")
	private int cores;

	@Parameter(defaultValue="WARN", required=true, property="mapping.missing")
	private String missing;
	Missing missingAction = Missing.WARN;

	@Parameter(defaultValue="false", property="mapping.findParents")
	private boolean findParents;

	@Parameter(defaultValue="true", property="mapping.correctEnums")
	private boolean correctEnums;

	private volatile Pair<Thread, Throwable> uncaught;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			process();
		} catch (final MojoExecutionException ex) {
			throw ex;
		} catch (final MojoFailureException ex) {
			throw ex;
		} catch (final Throwable t) {
			throw new MojoExecutionException(null, t);
		}
	}

	private void process() throws Throwable {
		validateInput();

		final MultiProcessor executor = MultiProcessor.newMultiProcessor(cores - 1, new ThreadFactoryBuilder().setDaemon(true).setNameFormat(OverMapped.class.getName() + "-processor-%d").setUncaughtExceptionHandler(this).build());
		final Future<?> fileCopy = executor.submit(
			new Callable<Object>()
				{
					@Override
					public Object call() throws Exception {
						if (original != null) {
							if (original.exists()) {
								original.delete();
							}
							Files.copy(input, original);
						}
						return null;
					}
				}
			);
		final Future<Iterable<?>> mappings = executor.submit(
			new Callable<Iterable<?>>()
				{
					@Override
					public Iterable<?> call() throws Exception {
						final Object yaml = new Yaml().load(Files.toString(maps, Charset.forName("UTF8")));
						if (yaml instanceof Iterable)
							return (Iterable<?>) yaml;
						if (yaml instanceof Map)
							return ImmutableList.of(yaml);
						throw new ClassCastException(String.format(
							"%s (%s) implements neither %s nor %s",
							yaml,
							yaml == null ? Object.class : yaml.getClass(),
							Iterable.class,
							Map.class
							));
					}
				}
			);

		final Map<String, ByteClass> byteClasses = newLinkedHashMap();
		final List<Pair<ZipEntry, byte[]>> fileEntries= newArrayList();

		readClasses(executor, byteClasses, fileEntries);

		try {
			reorderEntries(byteClasses);
		} catch (final CircularOrderException ex) {
			final Throwable throwable = new MojoFailureException("Circular class hiearchy detected");
			throwable.initCause(ex);
			throw throwable;
		}

		final Multimap<String, String> depends = processDepends(byteClasses);
		final Multimap<String, String> rdepends = processReverseDepends(depends);

		final BiMap<String, String> nameMaps = HashBiMap.create(byteClasses.size());
		final BiMap<String, String> inverseNameMaps = nameMaps.inverse();

		final BiMap<Signature, Signature> signatureMaps = HashBiMap.create();
		final BiMap<Signature, Signature> inverseSignatureMaps = signatureMaps.inverse();

		final Map<Signature, Integer> flags = newHashMap();

		final Remapper inverseMapper = new Remapper()
			{
				@Override
				public String map(final String typeName) {
					final String name = inverseNameMaps.get(typeName);
					if (name != null)
						return name;
					return typeName;
				}
			};

		prepareSignatures(byteClasses, rdepends, nameMaps, signatureMaps);

		final Signature.MutableSignature signature = Signature.newMutableSignature("", "", "");
		final Set<String> searchCache = newHashSet();

		for (final Object mapping : mappings.get()) {
			final Map<?,?> map = (Map<?, ?>) mapping;

			for (final SubRoutine subRoutine : SubRoutine.SUB_ROUTINES) {
				try {
					subRoutine.invoke(
						this,
						byteClasses,
						depends,
						rdepends,
						nameMaps,
						inverseNameMaps,
						signatureMaps,
						inverseSignatureMaps,
						inverseMapper,
						signature,
						searchCache,
						flags,
						map
						);
				} catch (final Exception ex) {
					final Throwable throwable = new MojoFailureException(
						"Failed to parse mappings in " + mapping);
					throwable.initCause(ex);
					throw throwable;
				}
			}
		}

		try {
			fileCopy.get();
		} catch (final ExecutionException ex) {
			throw new MojoFailureException(String.format(
				"Could not copy `%s' to `%s'",
				input,
				original
				));
		}

		writeToFile(executor, byteClasses, fileEntries, nameMaps, signatureMaps, flags);

		executor.shutdown();

		final Pair<Thread, Throwable> uncaught = this.uncaught;
		if (uncaught != null)
			throw new MojoExecutionException(
				String.format(
					"Uncaught exception in %s",
					uncaught.getLeft()
					),
				uncaught.getRight()
				);
	}

	private void writeToFile(
	                         final MultiProcessor executor,
	                         final Map<String, ByteClass> byteClasses,
	                         final List<Pair<ZipEntry, byte[]>> fileEntries,
	                         final BiMap<String, String> nameMaps,
	                         final BiMap<Signature, Signature> signatureMaps,
	                         final Map<Signature, Integer> flags
	                         ) throws
	                         IOException,
	                         FileNotFoundException,
	                         InterruptedException,
	                         ExecutionException
	                         {
		final Collection<Future<Pair<ZipEntry, byte[]>>> classWrites = newArrayList();
		for (final ByteClass clazz : byteClasses.values()) {
			classWrites.add(executor.submit(clazz.callable(signatureMaps, nameMaps, byteClasses, flags, correctEnums)));
		}

		FileOutputStream fileOut = null;
		JarOutputStream jar = null;
		try {
			jar = new JarOutputStream(fileOut = new FileOutputStream(output));
			for (final Pair<ZipEntry, byte[]> fileEntry : fileEntries) {
				jar.putNextEntry(fileEntry.getLeft());
				jar.write(fileEntry.getRight());
			}
			for (final Future<Pair<ZipEntry, byte[]>> fileEntryFuture : classWrites) {
				final Pair<ZipEntry, byte[]> fileEntry = fileEntryFuture.get();
				jar.putNextEntry(fileEntry.getLeft());
				jar.write(fileEntry.getRight());
			}
		} finally {
			if (jar != null) {
				try {
					jar.close();
				} catch (final IOException ex) {
				}
			}
			if (fileOut != null) {
				try {
					fileOut.close();
				} catch (final IOException ex) {
				}
			}
		}
	}

	private void validateInput() throws MojoExecutionException, MojoFailureException {
		if (cores <=0)
			throw new MojoExecutionException(String.format(
				"Cannot process with no cores: `%d'",
				cores
				));
		if (!maps.exists() || maps.isDirectory())
			throw new MojoFailureException(String.format(
				"Cannot process non-existant maps file `%s'",
				maps
				));
		if (!input.exists() || input.isDirectory())
			throw new MojoFailureException(String.format(
				"Cannot process non-existent input file `%s'",
				input
				));

		verifyOut(output);
		verifyOut(original);
		if (output == null) {
			output = input;
		}

		try {
			missingAction = Missing.valueOf(missing);
		} catch (final IllegalArgumentException ex) {
			getLog().warn(
				String.format(
					"Unknown value for mapping.missing: %s, using default %s",
					missing,
					missingAction.name()
					),
				ex
				);
		}
	}

	private void prepareSignatures(
	                               final Map<String, ByteClass> byteClasses,
	                               final Multimap<String, String> rdepends,
	                               final BiMap<String, String> nameMaps,
	                               final BiMap<Signature, Signature> signatureMaps
	                               ) {
		for (final ByteClass clazz : byteClasses.values()) {
			if (missingAction == Missing.VERBOSE) {
				getLog().info("Loading class: " + clazz);
			}
			final String name = clazz.getToken();
			nameMaps.put(name, name);
			final Iterable<String> reverseDependencies = rdepends.containsKey(name) ? rdepends.get(name) : ImmutableSet.<String>of();
			for (final Signature signature : clazz.getLocalSignatures()) {
				signatureMaps.put(signature, signature);
				if (signature.isMethod() && !signature.isConstructor()) {
					for (final String rdepend : reverseDependencies) {
						final Signature newSignature = signature.forClassName(rdepend);
						signatureMaps.put(newSignature, newSignature);
					}
				}
			}
		}
	}

	private Multimap<String, String> processDepends(
	                                                final Map<String, ByteClass> byteClasses
	                                                ) {
		final HashMultimap<String, String> depends = HashMultimap.create();
		final Set<String> knownClasses = byteClasses.keySet();
		for (final Map.Entry<String, ByteClass> entry : byteClasses.entrySet()) {
			final String name = entry.getKey();
			final ByteClass clazz = entry.getValue();
			for (final String interfaceName : clazz.getInterfaces()) {
				addTransitiveDependencies(depends, knownClasses, name, interfaceName);
			}
			addTransitiveDependencies(depends, knownClasses, name, clazz.getParent());
		}
		return depends;
	}

	private void addTransitiveDependencies(
	                                       final HashMultimap<String, String> depends,
	                                       final Set<String> knownClasses,
	                                       final String name,
	                                       final String dependency
	                                       ) {
		if (!knownClasses.contains(dependency))
			return;

		final Set<String> transitiveDependencies = depends.get(dependency);
		if (transitiveDependencies != null) {
			depends.putAll(name, transitiveDependencies);
		}
		depends.put(name, dependency);
	}

	private Multimap<String, String> processReverseDepends(
	                                                       final Multimap<String, String> dependencies
	                                                       ) {
		final HashMultimap<String, String> rdepends = HashMultimap.create();
		for (final Map.Entry<String, String> dependency : dependencies.entries()) {
			rdepends.put(dependency.getValue(), dependency.getKey());
		}
		return rdepends;
	}

	private void readClasses(
	                         final MultiProcessor executor,
	                         final Map<String, ByteClass> byteClasses,
	                         final List<Pair<ZipEntry, byte[]>> fileEntries
	                         ) throws
	                         ZipException,
	                         IOException,
	                         InterruptedException,
	                         ExecutionException,
	                         MojoFailureException
	                         {
		final List<Future<ByteClass>> classBuffer = newArrayList();
		final List<Future<Pair<ZipEntry, byte[]>>> fileBuffer = newArrayList();

		final ZipFile zipInput = new ZipFile(input);
		final Enumeration<? extends ZipEntry> zipEntries = zipInput.entries();
		while (zipEntries.hasMoreElements()) {
			final ZipEntry zipEntry = zipEntries.nextElement();
			if (ByteClass.isClass(zipEntry.getName())) {
				classBuffer.add(executor.submit(
					new Callable<ByteClass>()
						{
							@Override
							public ByteClass call() throws Exception {
								return new ByteClass(zipEntry.getName(), zipInput.getInputStream(zipEntry));
							}
						}
					));
			} else {
				fileBuffer.add(executor.submit(
					new Callable<Pair<ZipEntry, byte[]>>()
						{
							@Override
							public Pair<ZipEntry, byte[]> call() throws Exception {
								return new ImmutablePair<ZipEntry, byte[]>(
									new ZipEntry(zipEntry),
									ByteStreams.toByteArray(zipInput.getInputStream(zipEntry))
									);
							}
						}
					));
			}
		}

		for (final Future<Pair<ZipEntry, byte[]>> file : fileBuffer) {
			fileEntries.add(file.get());
		}
		for (final Future<ByteClass> clazzFuture : classBuffer) {
			ByteClass clazz = clazzFuture.get();
			clazz = byteClasses.put(clazz.getToken(), clazz);
			if (clazz != null)
				throw new MojoFailureException(String.format(
					"Duplicate class definition %s - %s",
					clazz,
					clazzFuture.get()
					));
		}

		zipInput.close();
	}

	private void reorderEntries(
	                            final Map<String, ByteClass> byteClasses
	                            ) throws
	                            WellOrderedException
	                            {
		final List<ByteClass> classBuffer = WellOrdered.process(
			new ArrayList<ByteClass>(),
			byteClasses.values(),
			new WellOrdered.AbstractInformer<ByteClass>()
				{
					@Override
					public void addPrecedingTo(final ByteClass token, final Collection<? super ByteClass> of) {
						addIfFound(token.getParent(), of);
						for (final String interfaceName : token.getInterfaces()) {
							addIfFound(interfaceName, of);
						}
					}

					private void addIfFound(final String name, final Collection<? super ByteClass> of) {
						final ByteClass clazz = byteClasses.get(name);
						if (clazz != null) {
							of.add(clazz);
						}
					}
				}
			);
		byteClasses.clear();

		for (final ByteClass clazz : classBuffer) {
			byteClasses.put(clazz.getToken(), clazz);
		}
	}

	private void verifyOut(
	                       final File out
	                       ) throws
	                       MojoFailureException
	                       {
		if (out != null){
			if (out.isDirectory())
				throw new MojoFailureException(String.format(
					"Cannot write to directory `%s'",
					out
					));
			final File parent = out.getParentFile();
			if (!parent.isDirectory() && !parent.mkdirs())
				throw new MojoFailureException(String.format(
					"Cannot make directory for `%s'",
					out
					));
		}
	}

	@Override
	public void uncaughtException(final Thread t, final Throwable e) {
		uncaught = new ImmutablePair<Thread, Throwable>(t, e);
	}

	boolean isFindParents() {
		return findParents;
	}
}
