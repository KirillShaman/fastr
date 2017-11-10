/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.access.vector;

import static com.oracle.truffle.r.runtime.RError.Message.NO_METHOD_ASSIGNING_SUBSET_S4;

import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.r.nodes.objects.GetS4DataSlot;
import com.oracle.truffle.r.nodes.objects.GetS4DataSlotNodeGen;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RS4Object;
import com.oracle.truffle.r.runtime.data.RTypedValue;

public class ReplaceS4ObjectNode extends Node {
    @Child private GetS4DataSlot getS4DataSlotNode = GetS4DataSlotNodeGen.create(RType.Environment);
    @Child private ReplaceVectorNode replaceVectorNode;

    public ReplaceS4ObjectNode(ElementAccessMode mode, boolean ignoreRecursive) {
        replaceVectorNode = ReplaceVectorNode.create(mode, ignoreRecursive);
    }

    public Object execute(RS4Object obj, Object[] positions, Object values) {
        RTypedValue dataSlot = getS4DataSlotNode.executeObject(obj);
        if (dataSlot == RNull.instance) {
            throw RError.error(RError.SHOW_CALLER, NO_METHOD_ASSIGNING_SUBSET_S4);
        }
        // No need to update the data slot, the value is env and they have reference semantics.
        replaceVectorNode.execute(dataSlot, positions, values);
        return obj;
    }
}
