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

import java.util.Map;

public class Signature {
	public static class MutableSignature extends Signature {
		MutableSignature(final String clazz, final String name, final String signature) {
			super(clazz, name, signature);
		}

		public MutableSignature update(
		                               final String clazz,
		                               final String name,
		                               final String signature,
		                               final Map<Signature, Signature> signatures
		                               ) {
			this.clazz = clazz;
			this.name = name;
			this.descriptor = signature;
			this.hash = hash(clazz, name, signature);
			final Signature value = signatures.get(this);
			if (value != null) {
				this.clazz = value.clazz;
				this.name = value.name;
				this.descriptor = value.descriptor;
				this.hash = value.hash;
			}
			return this;
		}

		public MutableSignature update(
		                               final String clazz,
		                               final String name,
		                               final String signature
		                               ) {
			this.clazz = clazz;
			this.name = name;
			this.descriptor = signature;
			this.hash = hash(clazz, name, signature);
			return this;
		}
	}

	@Deprecated String clazz;
	@Deprecated String name;
	@Deprecated String descriptor;
	@Deprecated int hash;

	Signature(final String clazz, final String name, final String signature) {
		this.clazz = clazz;
		this.name = name;
		this.descriptor = signature;
		this.hash = hash(clazz, name, signature);
	}

	public static Signature newSignature(final String clazz, final String name, final String signature) {
		return new Signature(clazz, name, signature);
	}

	public static MutableSignature newMutableSignature(final String clazz, final String name, final String signature) {
		return new MutableSignature(clazz, name, signature);
	}

	static int hash(final String clazz, final String name, final String signature) {
		int hash = 1;
		hash = 31 * hash + clazz.hashCode();
		hash = 31 * hash + name.hashCode();
		hash = 31 * hash + signature.hashCode();
		return hash;
	}

	@Override
	public int hashCode() {
		return hash;
	}

	@Override
	public boolean equals(final Object obj) {
		if (!(obj instanceof Signature))
			return false;
		return equals((Signature) obj);
	}

	private boolean equals(final Signature signature) {
		return
			this.hash == signature.hash
			&& this.clazz.equals(signature.clazz)
			&& this.name.equals(signature.name)
			&& this.descriptor.equals(signature.descriptor)
			;
	}

	public String getClassName() {
		return clazz;
	}

	public String getElementName() {
		return name;
	}

	public String getDescriptor() {
		return descriptor;
	}

	public Signature forClassName(final String clazz) {
		return new Signature(clazz, name, descriptor);
	}

	public Signature forElementName(final String name) {
		return new Signature(clazz, name, descriptor);
	}

	public boolean isMethod() {
		return descriptor.charAt(0) == '(';
	}

	@Override
	public String toString() {
		return toMappable();
	}

	/*
	 * Code retained if needed later
	 * (heh, we did end up needing this one :P)
	 */
	private String toMappable() {
		return new StringBuilder()
			.append(clazz)
			.append('.')
			.append(name)
			.append(' ')
			.append(descriptor)
			.toString();
	}

	/*
	 * Code retained if needed later.
	 *
	private void fromMappable(final String mappable) {
		int i1, i2;
		i1 = 0;
		i2 = mappable.indexOf('.');

		clazz = mappable.substring(i1, i1 = i2 + 1);
		i2 = mappable.indexOf('.', i1);

		name = mappable.substring(i1, i1 = i2 + 1);

		signature = mappable.substring(i1);

	}
	 *
	 */
}
