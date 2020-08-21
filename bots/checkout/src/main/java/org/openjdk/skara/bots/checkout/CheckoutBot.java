/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package org.openjdk.skara.bots.checkout;

import org.openjdk.skara.bot.*;
import org.openjdk.skara.vcs.*;
import org.openjdk.skara.vcs.openjdk.convert.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.Collectors;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URLEncoder;
import java.util.logging.Logger;

public class CheckoutBot implements Bot, WorkItem {
    private static final Logger log = Logger.getLogger("org.openjdk.skara.bots");;
    private final URI from;
    private final Branch branch;
    private final Path to;
    private final Path storage;

    CheckoutBot(URI from, Branch branch, Path to, Path storage) {
        this.from = from;
        this.branch = branch;
        this.to = to;
        this.storage = storage;
    }

    private static String urlEncode(Path p) {
        return URLEncoder.encode(p.toString(), StandardCharsets.UTF_8);
    }

    private static String urlEncode(URI uri) {
        return URLEncoder.encode(uri.toString(), StandardCharsets.UTF_8);
    }

    @Override
    public boolean concurrentWith(WorkItem other) {
        if (!(other instanceof CheckoutBot)) {
            return true;
        }
        var o = (CheckoutBot) other;
        return !(o.to.equals(to) || o.from.equals(from));
    }

    @Override
    public String toString() {
        return "CheckoutBot(" + from + ":" + branch.name() + ", " + to + ")";
    }

    @Override
    public List<WorkItem> getPeriodicItems() {
        return List.of(this);
    }

    @Override
    public Collection<WorkItem> run(Path scratchPath) {
        try {
            var fromDir = storage.resolve(urlEncode(from));
            Repository fromRepo = null;
            if (!Files.exists(fromDir)) {
                Files.createDirectories(fromDir);
                log.info("Cloning " + from + " to " + fromDir);
                fromRepo = Repository.clone(from, fromDir);
            } else {
                log.info("Getting existing \"from\" repository from " + fromDir);
                fromRepo = Repository.get(fromDir).orElseThrow(() ->
                    new IllegalStateException("Repository vanished from " + fromDir));
            }

            var marksFile = storage.resolve("marks").resolve(urlEncode(to)).resolve("marks.txt");
            Files.createDirectories(marksFile.getParent());

            var converter = new GitToHgConverter(branch);
            try {
                if (!Files.exists(to)) {
                    Files.createDirectories(to);
                    var toRepo = Repository.init(to, VCS.HG);
                    converter.convert(fromRepo, toRepo);
                } else {
                    var toRepo = Repository.get(to).orElseThrow(() ->
                        new IllegalStateException("Repository vanished from " + to));
                    var existing = Files.lines(marksFile).map(line -> line.split(" "))
                                                         .map(words -> new Mark(Integer.parseInt(words[0]),
                                                                                new Hash(words[1]),
                                                                                new Hash(words[2])))
                                                         .collect(Collectors.toList());
                    converter.pull(fromRepo, from, toRepo, existing);
                }
            } finally {
                var marks = converter.marks();
                if (!marks.isEmpty()) {
                    try (var writer = Files.newBufferedWriter(marksFile)) {
                        for (var mark : marks) {
                            writer.write(Integer.toString(mark.key()));
                            writer.write(" ");
                            writer.write(mark.hg().hex());
                            writer.write(" ");
                            writer.write(mark.git().hex());
                            writer.newLine();
                        }
                    } catch (IOException e) {
                        // pass
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return List.of();
    }
}