/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 * 
 * Copyright (c) 2014, Purdue University
 * Copyright (c) 2014, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.test.testrgen;

import org.junit.*;

import com.oracle.truffle.r.test.*;

// Checkstyle: stop line length check
public class TestrGenBuiltinrow extends TestBase {

    @Test
    @Ignore
    public void testrow1() {
        assertEval("argv <- list(c(14L, 14L)); .Internal(row(argv[[1]]))");
    }

    @Test
    @Ignore
    public void testrow2() {
        assertEval("argv <- list(c(4L, 3L)); .Internal(row(argv[[1]]))");
    }

    @Test
    @Ignore
    public void testrow3() {
        assertEval("argv <- list(0:1); .Internal(row(argv[[1]]))");
    }
}

