/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.skara.cli;

import org.openjdk.skara.host.*;
import org.openjdk.skara.forge.*;
import org.openjdk.skara.args.*;
import org.openjdk.skara.vcs.*;
import org.openjdk.skara.issuetracker.IssueTracker;
import org.openjdk.skara.jcheck.*;
import org.openjdk.skara.vcs.openjdk.*;
import org.openjdk.skara.proxy.HttpProxy;
import org.openjdk.skara.version.Version;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class GitLookup {
    public static void main(String[] args) throws IOException {
        var flags = List.of(
            Switch.shortcut("")
                  .fullname("verbose")
                  .helptext("Turn on verbose output")
                  .optional(),
            Switch.shortcut("")
                  .fullname("debug")
                  .helptext("Turn on debugging output")
                  .optional(),
            Switch.shortcut("")
                  .fullname("version")
                  .helptext("Print the version of this tool")
                  .optional());

        var inputs = List.of(
            Input.position(0)
                 .describe("HASH")
                 .singular()
                 .required()
        );

        var parser = new ArgumentParser("git-lookup", flags, inputs);
        var arguments = parser.parse(args);

        if (arguments.contains("version")) {
            System.out.println("git-info version: " + Version.fromManifest().orElse("unknown"));
            System.exit(0);
        }

        if (arguments.contains("verbose") || arguments.contains("debug")) {
            var level = arguments.contains("debug") ? Level.FINER : Level.FINE;
            Logging.setup(level);
        }

        HttpProxy.setup();

        var ref = new Hash(arguments.at(0).orString(""));
        var cwd = Path.of("").toAbsolutePath();
        var repo = ReadOnlyRepository.get(cwd).orElseThrow();
        var uri = Remote.toWebURI(Remote.toURI(repo.pullPath("origin"), true).toString());
        var credentials = GitCredentials.fill(uri.getHost(), uri.getPath(), null, null, uri.getScheme());

        if (credentials.password() == null) {
            System.err.println("error: no personal access token found, use git-credentials or the environment variable GIT_TOKEN");
            System.exit(1);
        }
        if (credentials.username() == null) {
            System.err.println("error: no username for " + uri.getHost() + " found, use git-credentials or the flag --username");
            System.exit(1);
        }

        var host = Forge.from(uri, new Credential(credentials.username(), credentials.password()));
        if (host.isEmpty()) {
            System.err.println("error: could not connect to host " + uri.getHost());
            System.exit(1);
        }
        var commit = host.get().search(ref);
        if (commit.isEmpty()) {
            System.err.println("error: could not connect to host " + uri.getHost());
            System.exit(1);
        }
        var c = commit.get();
        System.out.println(c.hash().hex());
        System.out.println(c.message());
        System.out.println("Author: " + c.author());
        System.out.println("Committer: " + c.committer());
    }
}
