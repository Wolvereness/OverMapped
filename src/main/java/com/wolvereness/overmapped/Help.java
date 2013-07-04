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

import java.io.IOException;
import java.io.InputStream;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

import com.google.common.io.ByteStreams;

@Mojo(name="help")
public class Help extends AbstractMojo {

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		final InputStream help = OverMapped.class.getResourceAsStream("/README.TXT");
		if (help != null) {
			try {
				getLog().info(new String(ByteStreams.toByteArray(help), "UTF8"));
			} catch (final IOException ex) {
				getLog().warn("Missing HELP data", ex);
			} finally {
				try {
					help.close();
				} catch (final IOException e) {
				}
			}
		} else {
			getLog().warn("Missing HELP data");
		}
	}
}
