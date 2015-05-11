/*
 * Copyright (c) 2015, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.control;

import com.oracle.truffle.api.CompilerDirectives.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.env.*;

/**
 * A {@link BlockNode} represents a sequence of statements such as the body of a {@code while} loop.
 *
 */
public class BlockNode extends SequenceNode implements RSyntaxNode {
    public static final RNode[] EMPTY_BLOCK = new RNode[0];

    public BlockNode(SourceSection src, RNode[] sequence) {
        super(sequence);
        assignSourceSection(src);
    }

    /**
     * A convenience method where {@code node} may or may not be a a {@link SequenceNode} already.
     */
    public BlockNode(SourceSection src, RSyntaxNode node) {
        this(src, convert(node));
    }

    /**
     * Ensures that {@code node} is a {@link BlockNode}.
     */
    public static RSyntaxNode ensureBlock(SourceSection src, RSyntaxNode node) {
        if (node == null || node instanceof BlockNode) {
            return node;
        } else {
            return new BlockNode(src, new RNode[]{node.asRNode()});
        }
    }

    private static RNode[] convert(RSyntaxNode node) {
        if (node instanceof BlockNode) {
            return ((SequenceNode) node).sequence;
        } else {
            return new RNode[]{node.asRNode()};
        }
    }

    @TruffleBoundary
    @Override
    public void deparse(RDeparse.State state) {
        for (int i = 0; i < sequence.length; i++) {
            state.mark();
            ((RSyntaxNode) sequence[i]).deparse(state);
            if (state.changed()) {
                // not all nodes will produce output
                state.writeline();
                state.mark(); // in case last
            }
        }
    }

    @Override
    public void serialize(RSerialize.State state) {
        /*
         * In GnuR there are no empty statement sequences, because "{" is really a function in R, so
         * it is represented as a LANGSXP with symbol "{" and a NULL cdr, representing the empty
         * sequence. This is an unpleasant special case in FastR that we can only detect by
         * re-examining the original source.
         * 
         * A sequence of length 1, i.e. a single statement, is represented as itself, e.g. a SYMSXP
         * for "x" or a LANGSXP for a function call. Otherwise, the representation is a LISTSXP
         * pairlist, where the car is the statement and the cdr is either NILSXP or a LISTSXP for
         * the next statement. Typically the statement (car) is itself a LANGSXP pairlist but it
         * might be a simple value, e.g. SYMSXP.
         */
        if (sequence.length == 0) {
            state.setNull();
        } else {
            for (int i = 0; i < sequence.length; i++) {
                state.serializeNodeSetCar(sequence[i]);
                if (i != sequence.length - 1) {
                    state.openPairList();
                }
            }
            state.linkPairList(sequence.length);
        }
    }

    @TruffleBoundary
    @Override
    public RSyntaxNode substitute(REnvironment env) {
        RNode[] sequenceSubs = new RNode[sequence.length];
        for (int i = 0; i < sequence.length; i++) {
            sequenceSubs[i] = ((RSyntaxNode) sequence[i]).substitute(env).asRNode();
        }
        return new BlockNode(null, sequenceSubs);
    }

}