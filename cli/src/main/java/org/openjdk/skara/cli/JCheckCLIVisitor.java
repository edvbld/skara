/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import org.openjdk.skara.jcheck.*;
import org.openjdk.skara.vcs.Hash;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class JCheckCLIVisitor implements IssueVisitor {
    private final boolean isLocal;

    public JCheckCLIVisitor() {
        this(false);
    }

    public JCheckCLIVisitor(boolean isLocal) {
        this.isLocal = isLocal;
    }

    private static void println(Issue i, String message) {
        System.out.print("[");
        System.out.print(i.check().name());
        System.out.print("] ");
        System.out.print(i.severity());
        System.out.print(": ");
        System.out.println(message);
    }

    private static void println(CommitIssue i, String message) {
        System.out.print("[");
        System.out.print(i.check().name());
        System.out.print("] ");
        System.out.print(i.severity());
        System.out.print(": ");
        System.out.print(i.commit().hash().abbreviate());
        System.out.print(": ");
        System.out.println(message);
    }

    public void visit(DuplicateIssuesIssue i) {
        var id = i.issue().id();
        var hash = i.commit().hash().abbreviate();
        var other = i.hashes()
                     .stream()
                     .map(Hash::abbreviate)
                     .map(s -> "         - " + s)
                     .collect(Collectors.toList());
        println(i, "issue id '" + id + "' in commit " + hash + " is already used in commits:");
        other.forEach(System.out::println);
    }

    public void visit(TagIssue i) {
        println(i, "illegal tag name: " + i.tag().name());
    }

    public void visit(BranchIssue i) {
        if (!isLocal) {
            println(i, "illegal branch name: " + i.branch().name());
        }
    }

    public void visit(SelfReviewIssue i) {
        println(i, "self-reviews are not allowed");
    }

    public void visit(TooFewReviewersIssue i) {
        if (!isLocal) {
            var required = i.numRequired();
            var actual = i.numActual();
            var reviewers = required == 1 ? " reviewer" : " reviewers";
            println(i, required + reviewers + " required, found " + actual);
        }
    }

    public void visit(InvalidReviewersIssue i) {
        if (!isLocal) {
            var invalid = String.join(", ", i.invalid());
            var wording = i.invalid().size() == 1 ? " is" : " are";
            println(i, invalid + wording + " not part of OpenJDK");
        }
    }

    public void visit(MergeMessageIssue i) {
        println(i, "merge commits should only use the commit message '" + i.expected() + "'");
    }

    public void visit(HgTagCommitIssue i) {
        switch (i.error()) {
            case TOO_MANY_LINES:
                println(i, "message should only be one line");
                return;
            case BAD_FORMAT:
                println(i, "message should be of format 'Added tag <tag> for changeset <hash>'");
                return;
            case TOO_MANY_CHANGES:
                println(i, "should only add one line to .hgtags");
                return;
            case TAG_DIFFERS:
                println(i, "tag differs in commit message and .hgtags");
                return;
        }
    }

    public void visit(CommitterIssue i) {
        var committer = i.commit().committer().name();
        var project = i.project().name();
        println(i, committer + " is not committer in project " + project);
    }

    private static class WhitespaceRange {
        private final WhitespaceIssue.Whitespace kind;
        private final int start;
        private final int end;

        public WhitespaceRange(WhitespaceIssue.Whitespace kind, int start, int end) {
            this.kind = kind;
            this.start = start;
            this.end = end;
        }

        public WhitespaceIssue.Whitespace kind() {
            return kind;
        }

        public int start() {
            return start;
        }

        public int end() {
            return end;
        }
    }

    private static List<WhitespaceRange> ranges(List<WhitespaceIssue.Error> errors) {
        if (errors.size() == 1) {
            var res = new ArrayList<WhitespaceRange>();
            res.add(new WhitespaceRange(errors.get(0).kind(), errors.get(0).index(), errors.get(0).index()));
            return res;
        }

        var merged = new ArrayList<WhitespaceRange>();
        var start = errors.get(0);
        var end = start;
        for (int i = 1; i < errors.size(); i++) {
            var e = errors.get(i);
            if (e.index() == (end.index() + 1) && e.kind() == end.kind()) {
                end = e;
            } else {
                merged.add(new WhitespaceRange(e.kind(), start.index(), end.index()));
                start = e;
            }
        }

        return merged;
    }

    public void visit(WhitespaceIssue i) {
        var hex = i.commit().hash().abbreviate();
        var prefix = i.severity() + ": " + hex + ": ";
        var indent = prefix.replaceAll(".", " ");
        var pos = i.path() + ":" + i.row();

        System.out.println(prefix + i.describe() + " in " + pos);
        System.out.println(indent + i.escapeLine());
        System.out.println(indent + i.hints());
    }

    public void visit(MessageIssue i) {
        println(i, "contains additional lines in commit message");
        for (var line : i.message().additional()) {
            System.out.println("> " + line);
        }
    }

    public void visit(IssuesIssue i) {
        println(i, "missing reference to JBS issue in commit message");
        for (var line : i.commit().message()) {
            System.out.println("> " + line);
        }
    }

    public void visit(ExecutableIssue i) {
        println(i, "file " + i.path() + " is executable");
    }

    public void visit(AuthorNameIssue i) {
        println(i, "missing author name");
    }

    public void visit(AuthorEmailIssue i) {
        println(i, "missing author email");
    }

    public void visit(CommitterNameIssue i) {
        println(i, "missing committer name");
    }

    public void visit(CommitterEmailIssue i) {
        if (!isLocal) {
            var domain = i.expectedDomain();
            println(i, "missing committer email from domain " + domain);
        }
    }

    public void visit(BlacklistIssue i) {
        println(i, "commit is blacklisted");
    }

    public void visit(BinaryIssue i) {
        println(i, "adds binary file: " + i.path().toString());
    }
}
