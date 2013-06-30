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

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;

import com.wolvereness.overmapped.asm.Signature;

public enum Missing {
	WARN
		{
			@Override
			public void act(
			                final Log log,
			                final String message,
			                final String string1,
			                final String string2,
			                final String string3,
			                final String string4,
			                final Object verbose
			                ) {
				log.warn(makeMessage(message, string1, string2, string3, string4));
			}
		},
	FAIL
		{
			@Override
			public void act(
			                final Log log,
			                final String message,
			                final String string1,
			                final String string2,
			                final String string3,
			                final String string4,
			                final Object verbose
			                ) throws
			                MojoFailureException
			                {
				throw new MojoFailureException(makeMessage(message, string1, string2, string3, string4));
			}
		},
	IGNORE
		{
			@Override
			public void act(
			                final Log log,
			                final String message,
			                final String string1,
			                final String string2,
			                final String string3,
			                final String string4,
			                final Object verbose
			                ) {
				// Ignore
			}
		},
	VERBOSE
		{
			@Override
			public void act(
			                final Log log,
			                final String message,
			                final String string1,
			                final String string2,
			                final String string3,
			                final String string4,
			                final Object verbose

			         ) throws
			         MojoFailureException
			         {
				WARN.act(log, message, string1, string2, string3, string4, verbose);
				log.info("Verbose:\n" + verbose);
			}
		}
	;

	abstract void act(
	                  final Log log,
	                  final String message,
	                  final String string1,
	                  final String string2,
	                  final String string3,
	                  final String string4,
	                  final Object verbose
	                  ) throws
	                  MojoFailureException
	                  ;

	String makeMessage(
	                   final String message,
	                   final String string1,
	                   final String string2,
	                   final String string3,
	                   final String string4) {
		return String.format(
			message,
			string1,
			string2,
			string3,
			string4
			);
	}

	public void actClass(
	                     final Log log,
	                     final String originalName,
	                     final String newName,
	                     final Map<String, String> classes
	                     ) throws
	                     MojoFailureException
	                     {
		act(log, "Could not find `%s' to map to `%s'", originalName, newName, null, null, classes);
	}

	public void actMember(
	                      final Log log,
	                      final String context,
	                      final String name,
	                      final String newName,
	                      final String description,
	                      final Map<Signature, Signature> signatures
	                      ) throws
	                      MojoFailureException
	                      {
		act(log, "Could not find member `%2$s' - `%4$s' in `%1$s' (mapping to `%3$s')", context, name, newName, description, signatures);
	}
}
