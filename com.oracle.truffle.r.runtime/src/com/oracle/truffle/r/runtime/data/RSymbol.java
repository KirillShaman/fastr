/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 3 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.r.runtime.data;

import java.util.concurrent.ConcurrentHashMap;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.r.runtime.RType;

/**
 * Denotes an R "symbol" or "name". Its rep is a {@code String} but it's a different type in the
 * Truffle sense.
 */
@ValueType
public final class RSymbol extends RAttributeStorage {

    /**
     * Note: GnuR caches all symbols and some packages rely on their identity. Moreover, the cached
     * symbols are never garbage collected. This table corresponds to {@code R_SymbolTable} in GNUR.
     */
    private static final ConcurrentHashMap<String, RSymbol> symbolTable = new ConcurrentHashMap<>(1024);

    public static final RSymbol MISSING = RDataFactory.createSymbol("");

    private final CharSXPWrapper name;

    private RSymbol(String name) {
        this.name = CharSXPWrapper.create(name);
    }

    @TruffleBoundary
    public static RSymbol install(String name) {
        return symbolTable.computeIfAbsent(name, RSymbol::new);
    }

    @Override
    public RType getRType() {
        return RType.Symbol;
    }

    public String getName() {
        return name.getContents();
    }

    public CharSXPWrapper getWrappedName() {
        return name;
    }

    @Override
    public String toString() {
        return getName();
    }

    public boolean isMissing() {
        return getName().isEmpty();
    }

    @Override
    public int hashCode() {
        return this.getName().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (obj instanceof RSymbol) {
            return ((RSymbol) obj).getName().equals(this.getName());
        }
        return false;
    }
}
