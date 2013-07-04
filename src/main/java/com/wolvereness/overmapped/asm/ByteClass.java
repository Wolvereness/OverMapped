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
package com.wolvereness.overmapped.asm;

import static org.objectweb.asm.Opcodes.ASM4;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.zip.ZipEntry;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.mutable.MutableObject;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.RemappingClassAdapter;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import com.wolvereness.overmapped.asm.Signature.MutableSignature;

public final class ByteClass {
	static final String FILE_POSTFIX = ".class";
	private final ClassReader reader;
	private final String token;
	private final String parent;
	private final List<String> interfaces;
	private final List<Signature> localSignatures;

	public ByteClass(final String name, final InputStream data) throws IOException {
		Validate.notNull(name, "File name cannot be null");
		Validate.notNull(data, "InputStream cannot be null");
		Validate.isTrue(name.toLowerCase().endsWith(FILE_POSTFIX), "File name must be a class file");

		this.token = name.substring(0, name.length() - FILE_POSTFIX.length());

		final MutableObject<String> parent = new MutableObject<String>();
		final ImmutableList.Builder<String> interfaces = ImmutableList.builder();
		final ImmutableList.Builder<Signature> localSignatures = ImmutableList.builder();

		try {
			this.reader = new ClassReader(ByteStreams.toByteArray(data));
		} finally {
			try {
				data.close();
			} catch (final IOException ex) {
			}
		}
		reader.accept(
			new ClassVisitor(ASM4)
				{

					@Override
					public FieldVisitor visitField(
					                               final int access,
					                               final String name,
					                               final String desc,
					                               final String signature,
					                               final Object value
					                               ) {
						localSignatures.add(new Signature(ByteClass.this.getToken(), name, desc));
						return super.visitField(access, name, desc, signature, value);
					}

					@Override
					public MethodVisitor visitMethod(
					                                 final int access,
					                                 final String name,
					                                 final String desc,
					                                 final String signature,
					                                 final String[] exceptions
					                                 ) {
						localSignatures.add(new Signature(ByteClass.this.getToken(), name, desc));
						return super.visitMethod(access, name, desc, signature, exceptions);
					}

					@Override
					public void visit(
					                  final int version,
					                  final int access,
					                  final String name,
					                  final String signature,
					                  final String superName,
					                  final String[] interfacesArray
					                  ) {
						super.visit(version, access, name, signature, superName, interfacesArray);

						parent.setValue(superName);

						if (!name.equals(ByteClass.this.getToken()))
							throw new IllegalArgumentException(name + " is not " + ByteClass.this.getToken());

						for (final String interfaceName : interfacesArray) {
							if (!(interfaceName.startsWith("java.") || interfaceName.startsWith("javax."))) {
								interfaces.add(interfaceName);
							}
						}
					}
				},
			ClassReader.SKIP_CODE
			);

		this.parent = parent.getValue();
		this.interfaces = interfaces.build();
		this.localSignatures = localSignatures.build();
	}

	/**
	 * @return The token for this ByteClass
	 */
	public String getToken() {
		return token;
	}

	/**
	 * @return the parent
	 */
	public String getParent() {
		return parent;
	}

	/**
	 * @return the interfaces implemented
	 */
	public List<String> getInterfaces() {
		return interfaces;
	}

	/**
	 * @return the localSignatures
	 */
	public List<Signature> getLocalSignatures() {
		return localSignatures;
	}

	public Callable<Pair<ZipEntry, byte[]>> callable(
	                                                 final Map<Signature, Signature> signatures,
	                                                 final Map<String, String> classMaps,
	                                                 final Map<String, ByteClass> classes,
	                                                 final Map<Signature, Integer> flags
	                                                 ) {
		return new Callable<Pair<ZipEntry, byte[]>>()
			{
				@Override
				public Pair<ZipEntry, byte[]> call() throws Exception {
					return ByteClass.this.call(signatures, classMaps, classes, flags);
				}
			};
	}

	public Pair<ZipEntry, byte[]> call(
	                                   final Map<Signature, Signature> signatures,
	                                   final Map<String, String> classMaps,
	                                   final Map<String, ByteClass> classes,
	                                   final Map<Signature, Integer> flags
	                                   ) throws
	                                   Exception
	                                   {
		final MutableSignature signature = new Signature.MutableSignature("", "", "");
		final ClassWriter writer = new ClassWriter(0);
		reader.accept(
			new RemappingClassAdapter(
				writer,
				new Remapper()
					{
						@Override
						public String mapMethodName(final String owner, final String name, final String desc) {
							return signature.update(owner, name, desc, signatures).getElementName();
						}

						@Override
						public String mapFieldName(final String owner, final String name, final String desc) {
							return signature.update(owner, name, desc, signatures).getElementName();
						}

						@Override
						public String map(final String typeName) {
							final String name = classMaps.get(typeName);
							if (name != null)
								return name;
							return typeName;
						}
					}
				)
				{
					@Override
					public FieldVisitor visitField(
					                               final int access,
					                               final String name,
					                               final String desc,
					                               final String generics,
					                               final Object value
					                               ) {
						return super.visitField(
							signature.updateAndGet(
								ByteClass.this.getToken(),
								name,
								desc,
								flags,
								access
								),
							name,
							desc,
							generics,
							value
							);
					}

					@Override
					public MethodVisitor visitMethod(
					                                 final int access,
					                                 final String name,
					                                 final String desc,
					                                 final String generics,
					                                 final String[] exceptions
					                                 ) {
						return super.visitMethod(
							signature.updateAndGet(
								ByteClass.this.getToken(),
								name,
								desc,
								flags,
								access
								),
							name,
							desc,
							generics,
							exceptions
							);
					}
				},
			ClassReader.EXPAND_FRAMES
			);

		return new ImmutablePair<ZipEntry, byte[]>(
			new ZipEntry(classMaps.get(token) + FILE_POSTFIX),
			writer.toByteArray()
			);
	}

	@Override
	public String toString() {
		return this.getClass().getName() + "(" + token + " _ " + parent + interfaces + "):" + localSignatures;
	}

	public static boolean isClass(final String name) {
		return name.endsWith(FILE_POSTFIX);
	}
}
