package com.wolvereness.overmapped.asm;

import java.util.Map;

import org.objectweb.asm.commons.Remapper;

import com.wolvereness.overmapped.asm.Signature.MutableSignature;

final class SignatureRemapper extends Remapper {
	private final MutableSignature signature = new Signature.MutableSignature("", "", "");
	private final Map<Signature, Signature> signatures;
	private final Map<String, ByteClass> classes;
	private final Map<String, String> classMaps;

	SignatureRemapper(
	                  final Map<String, String> classMaps,
	                  final Map<Signature, Signature> signatures,
	                  final Map<String, ByteClass> classes
	                  ) {
		this.classMaps = classMaps;
		this.signatures = signatures;
		this.classes = classes;
	}

	@Override
	public String mapMethodName(final String owner, final String name, final String desc) {
		return signature.update(owner, name, desc, signatures).getElementName();
	}

	@Override
	public String mapFieldName(final String owner, final String name, final String desc) {
		return signature.update(owner, name, desc, signatures, classes).getElementName();
	}

	@Override
	public String map(final String typeName) {
		final String name = classMaps.get(typeName);
		if (name != null)
			return name;
		return typeName;
	}
}
