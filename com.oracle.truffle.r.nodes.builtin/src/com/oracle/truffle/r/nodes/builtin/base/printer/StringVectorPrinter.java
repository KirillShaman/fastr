/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html
 *
 * Copyright (c) 1995, 1996  Robert Gentleman and Ross Ihaka
 * Copyright (c) 1997-2013,  The R Core Team
 * Copyright (c) 2016, Oracle and/or its affiliates
 *
 * All rights reserved.
 */
package com.oracle.truffle.r.nodes.builtin.base.printer;

import static com.oracle.truffle.r.nodes.builtin.base.printer.Utils.asBlankArg;

import java.io.IOException;

import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.data.model.RAbstractStringVector;

//Transcribed from GnuR, src/main/format.c

public final class StringVectorPrinter extends VectorPrinter<RAbstractStringVector> {

    public static final StringVectorPrinter INSTANCE = new StringVectorPrinter();

    @Override
    protected VectorPrinter<RAbstractStringVector>.VectorPrintJob createJob(RAbstractStringVector vector, int indx, boolean quote, PrintContext printCtx) {
        return new StringVectorPrintJob(vector, indx, quote, printCtx);
    }

    private final class StringVectorPrintJob extends VectorPrintJob {

        protected StringVectorPrintJob(RAbstractStringVector vector, int indx, boolean quote, PrintContext printCtx) {
            super(vector, indx, quote, printCtx);
        }

        @Override
        protected FormatMetrics formatVector(int offs, int len) {
            int w = formatString(vector, offs, len, quote, printCtx.parameters());
            return new FormatMetrics(w);
        }

        @Override
        protected void printElement(int i, FormatMetrics fm) throws IOException {
            String s = vector.getDataAt(i);
            StringPrinter.printString(s, fm.maxWidth, printCtx);
        }

        @Override
        protected void printCell(int i, FormatMetrics fm) throws IOException {
            String s = vector.getDataAt(i);
            String outS = StringPrinter.encode(s, fm.maxWidth, printCtx.parameters());
            int g = printCtx.parameters().getGap();
            String fmt = "%" + asBlankArg(g) + "s%s";
            printCtx.output().printf(fmt, "", outS);
        }

        @Override
        protected void printEmptyVector() throws IOException {
            out.println("character(0)");
        }

        @Override
        protected void printMatrixColumnLabels(RAbstractStringVector cl, int jmin, int jmax, FormatMetrics[] w) {
            if (printCtx.parameters().getRight()) {
                for (int j = jmin; j < jmax; j++) {
                    rightMatrixColumnLabel(cl, j, w[j].maxWidth);
                }
            } else {
                for (int j = jmin; j < jmax; j++) {
                    leftMatrixColumnLabel(cl, j, w[j].maxWidth);
                }
            }
        }

        @Override
        protected int matrixColumnWidthCorrection1() {
            return 0;
        }

        @Override
        protected int matrixColumnWidthCorrection2() {
            return printCtx.parameters().getGap();
        }

        @Override
        protected String elementTypeName() {
            return "character";
        }

    }

    public static int formatString(RAbstractStringVector x, int offs, int n, boolean quote, PrintParameters pp) {
        int xmax = 0;
        int l;

        // output argument
        int fieldwidth;

        for (int i = 0; i < n; i++) {
            String s = x.getDataAt(offs + i);
            String xi = quote ? RRuntime.quoteString(s) : s;

            if (xi == RRuntime.STRING_NA) {
                l = quote ? pp.getNaWidth() : pp.getNaWidthNoquote();
            } else {
                l = xi.length();
            }
            if (l > xmax) {
                xmax = l;
            }
        }

        fieldwidth = xmax;

        return fieldwidth;
    }

}
