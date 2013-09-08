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

import static com.google.common.collect.Lists.*;
import static org.objectweb.asm.Opcodes.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
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
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.RemappingClassAdapter;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.io.ByteStreams;

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
			new ClassParser(token, interfaces, parent, localSignatures),
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
		final ClassWriter writer = new ClassWriter(0);
		reader.accept(
			new RemappingClassAdapter(
				new EnumCorrection(writer),
				new SignatureRemapper(classMaps, signatures, classes)
				),
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

final class ClassParser extends ClassVisitor {
	private final String className;
	private final Builder<String> interfaces;
	private final MutableObject<String> parent;
	private final Builder<Signature> localSignatures;

	ClassParser(
	            final String className,
	            final Builder<String> interfaces,
	            final MutableObject<String> parent,
	            final Builder<Signature> localSignatures
	            ) {
		super(ASM4);
		this.className = className;
		this.interfaces = interfaces;
		this.parent = parent;
		this.localSignatures = localSignatures;
	}

	@Override
	public FieldVisitor visitField(
	                               final int access,
	                               final String name,
	                               final String desc,
	                               final String signature,
	                               final Object value
	                               ) {
		localSignatures.add(new Signature(className, name, desc));
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
		localSignatures.add(new Signature(className, name, desc));
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

		if (!name.equals(className))
			throw new IllegalArgumentException(name + " is not " + className);

		for (final String interfaceName : interfacesArray) {
			if (!(interfaceName.startsWith("java.") || interfaceName.startsWith("javax."))) {
				interfaces.add(interfaceName);
			}
		}
	}
}

final class EnumCorrection extends ClassVisitor {
	private List<String> enums;
	private String className;

	EnumCorrection(final ClassVisitor cv) {
		super(ASM4, cv);
	}

	@Override
	public void visit(
	                  final int version,
	                  final int access,
	                  final String name,
	                  final String signature,
	                  final String superName,
	                  final String[] interfaces
	                  ) {
		if (superName.equals("java/lang/Enum")) {
			enums = newArrayList();
			className = name;
		}
		super.visit(version, access, name, signature, superName, interfaces);
	}

	@Override
	public FieldVisitor visitField(final int access, final String name, final String desc, final String signature, final Object value) {
		if ((access & 0x4000) != 0 && enums != null) {
			enums.add(name);
		}
		return super.visitField(access, name, desc, signature, value);
	}

	@Override
	public MethodVisitor visitMethod(final int access, final String name, final String desc, final String signature, final String[] exceptions) {
		final MethodVisitor methodVisitor = super.visitMethod(access, name, desc, signature, exceptions);
		if (!(name.equals("<clinit>") && desc.equals("()V")) || enums == null) {
			return methodVisitor;
		}
		return new EnumMethodCorrection(methodVisitor, enums, className);
	}
}

final class EnumMethodCorrection extends MethodVisitor {
	private final Iterator<String> it;
	private boolean active;
	private String lastName;
	private final String className;
	private final String classDescriptor;

	EnumMethodCorrection(final MethodVisitor mv, final List<String> enums, final String className) {
		super(ASM4, mv);
		this.it = enums.iterator();
		this.className = className;
		this.classDescriptor = Type.getObjectType(className).getDescriptor();
	}

	@Override
	public void visitTypeInsn(final int opcode, final String type) {
		if (!active && it.hasNext()) {
			// Initiate state machine
			if (opcode != NEW)
				throw new AssertionError("Unprepared for TypeInsn: " + opcode + " - " + type + " in " + className);
			active = true;
		}
		super.visitTypeInsn(opcode, type);
	}

	@Override
	public void visitLdcInsn(final Object cst) {
		if (active && lastName == null) {
			if (!(cst instanceof String))
				throw new AssertionError("Unprepared for LdcInsn: " + cst + " in " + className);
			// Switch the first constant in the Enum constructor
			super.visitLdcInsn(lastName = it.next());
		} else {
			super.visitLdcInsn(cst);
		}
	}

	@Override
	public void visitFieldInsn(final int opcode, final String owner, final String name, final String desc) {
		if (opcode == PUTSTATIC && active && lastName != null && owner.equals(className) && desc.equals(classDescriptor) && name.equals(lastName)) {
			// Finish the current state machine
			active = false;
			lastName = null;
		}
		super.visitFieldInsn(opcode, owner, name, desc);
	}
}
