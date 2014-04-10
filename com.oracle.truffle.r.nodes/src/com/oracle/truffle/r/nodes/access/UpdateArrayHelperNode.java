/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.access;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.dsl.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.Node.*;
import com.oracle.truffle.r.nodes.*;
import com.oracle.truffle.r.nodes.unary.*;
import com.oracle.truffle.r.nodes.access.AccessArrayNode.*;
import com.oracle.truffle.r.nodes.access.ArrayPositionCast.*;
import com.oracle.truffle.r.nodes.access.ArrayPositionCastFactory.*;
import com.oracle.truffle.r.nodes.access.ArrayPositionCast.OperatorConverterNode;
import com.oracle.truffle.r.nodes.access.UpdateArrayHelperNode.CoerceOperand;
import com.oracle.truffle.r.nodes.access.UpdateArrayHelperNodeFactory.CoerceOperandFactory;
import com.oracle.truffle.r.runtime.*;
import com.oracle.truffle.r.runtime.data.*;
import com.oracle.truffle.r.runtime.data.model.*;
import com.oracle.truffle.r.runtime.ops.na.*;

@SuppressWarnings("unused")
@NodeChildren({@NodeChild(value = "v", type = RNode.class), @NodeChild(value = "newValue", type = RNode.class),
                @NodeChild(value = "vector", type = CoerceOperand.class, executeWith = {"newValue", "v"}), @NodeChild(value = "recursionLevel", type = RNode.class),
                @NodeChild(value = "positions", type = PositionsArrayNodeValue.class, executeWith = {"vector", "newValue"})})
public abstract class UpdateArrayHelperNode extends RNode {

    private final boolean isSubset;

    private final NACheck elementNACheck = NACheck.create();
    private final NACheck posNACheck = NACheck.create();
    private final NACheck namesNACheck = NACheck.create();

    abstract RNode getVector();

    abstract RNode getNewValue();

    abstract Object executeUpdate(VirtualFrame frame, Object v, Object value, Object vector, int recLevel, Object positions);

    @Child private UpdateArrayHelperNode updateRecursive;
    @Child private CastComplexNode castComplex;
    @Child private CastDoubleNode castDouble;
    @Child private CastIntegerNode castInteger;
    @Child private CastStringNode castString;
    @Child private CoerceOperand coerceOperand;
    @Child private ArrayPositionCast castPosition;
    @Child private OperatorConverterNode operatorConverter;

    public UpdateArrayHelperNode(boolean isSubset) {
        this.isSubset = isSubset;
    }

    public UpdateArrayHelperNode(UpdateArrayHelperNode other) {
        this.isSubset = other.isSubset;
    }

    private Object updateRecursive(VirtualFrame frame, Object v, Object value, Object vector, Object operand, int recLevel) {
        if (updateRecursive == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            updateRecursive = insert(UpdateArrayHelperNodeFactory.create(this.isSubset, null, null, null, null, null));
        }
        return executeUpdate(frame, v, value, vector, recLevel, operand);
    }

    private Object castComplex(VirtualFrame frame, Object operand) {
        if (castComplex == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castComplex = insert(CastComplexNodeFactory.create(null, true, true));
        }
        return castComplex.executeCast(frame, operand);
    }

    private Object castDouble(VirtualFrame frame, Object operand) {
        if (castDouble == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castDouble = insert(CastDoubleNodeFactory.create(null, true, true));
        }
        return castDouble.executeCast(frame, operand);
    }

    private Object castInteger(VirtualFrame frame, Object operand) {
        if (castInteger == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castInteger = insert(CastIntegerNodeFactory.create(null, true, true));
        }
        return castInteger.executeCast(frame, operand);
    }

    private Object castString(VirtualFrame frame, Object operand) {
        if (castString == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castString = insert(CastStringNodeFactory.create(null, false, true, true));
        }
        return castString.executeCast(frame, operand);
    }

    private Object coerceOperand(VirtualFrame frame, Object operand, Object value) {
        if (coerceOperand == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            coerceOperand = insert(CoerceOperandFactory.create(null, null));
        }
        return coerceOperand.executeEvaluated(frame, value, operand);
    }

    private Object castPosition(VirtualFrame frame, Object vector, Object operand) {
        if (castPosition == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            castPosition = insert(ArrayPositionCastFactory.create(0, 1, true, false, null, null, null));
        }
        return castPosition.executeArg(frame, operand, vector, operand);
    }

    private void initOperatorConvert() {
        if (operatorConverter == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            operatorConverter = insert(OperatorConverterNodeFactory.create(0, 1, true, false, null, null));
        }
    }

    private Object convertOperand(VirtualFrame frame, Object vector, int operand) {
        initOperatorConvert();
        return operatorConverter.executeConvert(frame, vector, operand);
    }

    @CreateCast({"newValue"})
    public RNode createCastValue(RNode child) {
        return CastToVectorNodeFactory.create(child, false, false, true);
    }

    @Specialization(order = 5, guards = "emptyValue")
    RAbstractVector update(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, Object[] positions) {
        if (isSubset) {
            int replacementLength = getReplacementLength(positions, value, false);
            if (replacementLength == 0) {
                return vector;
            }
        }
        throw RError.getReplacementZero(getEncapsulatingSourceSection());
    }

    @Specialization(order = 7)
    RNull accessFunction(Object v, Object value, RFunction vector, int recLevel, Object position) {
        throw RError.getObjectNotSubsettable(getEncapsulatingSourceSection(), "closure");
    }

    @Specialization(order = 8)
    RAbstractVector update(Object v, RNull value, RList vector, int recLevel, Object[] positions) {
        if (isSubset) {
            throw RError.getNotMultipleReplacement(getEncapsulatingSourceSection());
        } else {
            throw RError.getSubscriptTypes(getEncapsulatingSourceSection(), "NULL", "list");
        }
    }

    @Specialization(order = 9, guards = "isPosZero")
    RAbstractVector updateNAOrZero(Object v, RNull value, RList vector, int recLevel, int position) {
        if (!isSubset) {
            throw RError.getSelectLessThanOne(getEncapsulatingSourceSection());
        } else {
            return vector;
        }
    }

    @Specialization(order = 11)
    RAbstractVector update(Object v, RNull value, RAbstractVector vector, int recLevel, Object[] positions) {
        if (isSubset) {
            throw RError.getNotMultipleReplacement(getEncapsulatingSourceSection());
        } else {
            throw RError.getMoreElementsSupplied(getEncapsulatingSourceSection());
            // CheckStyle: stop system..print check
            // with no "--silent" command flag, this should get thrown
            // throw RError.getSubscriptTypes(getEncapsulatingSourceSection(), "NULL",
            // RRuntime.classToString(vector.getElementClass(), false));
            // CheckStyle: resume system..print check
        }
    }

    @Specialization(order = 12, guards = {"emptyValue", "isPosZero"})
    RAbstractVector updatePosZero(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, int position) {
        if (!isSubset) {
            throw RError.getReplacementZero(getEncapsulatingSourceSection());
        }
        return vector;
    }

    @Specialization(order = 13, guards = {"emptyValue", "!isPosZero", "!isPosNA"})
    RAbstractVector update(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, int position) {
        throw RError.getReplacementZero(getEncapsulatingSourceSection());
    }

    @Specialization(order = 14, guards = "!isVectorLongerThanOne")
    RAbstractVector updateVectorLongerThanOne(Object v, RNull value, RList vector, int recLevel, RNull position) {
        throw RError.getSelectLessThanOne(getEncapsulatingSourceSection());
    }

    @Specialization(order = 15, guards = "isVectorLongerThanOne")
    RAbstractVector update(Object v, RNull value, RList vector, int recLevel, RNull position) {
        throw RError.getSelectMoreThanOne(getEncapsulatingSourceSection());
    }

    @Specialization(order = 16)
    RAbstractVector update(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, RNull position) {
        throw RError.getSelectMoreThanOne(getEncapsulatingSourceSection());
    }

    @Specialization(order = 17, guards = {"isPosNA", "isValueLengthOne", "isVectorLongerThanOne"})
    RAbstractVector updateNAValueLengthOneLongVector(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, int position) {
        if (!isSubset) {
            throw RError.getSelectMoreThanOne(getEncapsulatingSourceSection());
        } else {
            return vector;
        }
    }

    @Specialization(order = 18, guards = {"isPosNA", "isValueLengthOne", "!isVectorLongerThanOne"})
    RAbstractVector updateNAValueLengthOne(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, int position) {
        if (!isSubset) {
            throw RError.getSelectLessThanOne(getEncapsulatingSourceSection());
        } else {
            return vector;
        }
    }

    @Specialization(order = 19, guards = {"isPosNA", "!isValueLengthOne"})
    RAbstractVector updateNA(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, int position) {
        if (isSubset) {
            throw RError.getNASubscripted(getEncapsulatingSourceSection());
        } else {
            throw RError.getMoreElementsSupplied(getEncapsulatingSourceSection());
        }
    }

    @Specialization(order = 20, guards = {"isPosZero", "isValueLengthOne"})
    RAbstractVector updateZeroValueLengthOne(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, int position) {
        if (!isSubset) {
            throw RError.getSelectLessThanOne(getEncapsulatingSourceSection());
        } else {
            return vector;
        }
    }

    @Specialization(order = 21, guards = {"isPosZero", "!isValueLengthOne"})
    RAbstractVector updateZero(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, int position) {
        if (!isSubset) {
            throw RError.getMoreElementsSupplied(getEncapsulatingSourceSection());
        } else {
            return vector;
        }
    }

    @Specialization(order = 22, guards = "isPosZero")
    RAbstractVector updateZero(Object v, RNull value, RAbstractVector vector, int recLevel, int position) {
        if (!isSubset) {
            throw RError.getMoreElementsSupplied(getEncapsulatingSourceSection());
        } else {
            return vector;
        }
    }

    private int getNewArrayBase(int srcArrayBase, int pos, int newAccSrcDimensions) {
        int newSrcArrayBase;
        if (posNACheck.check(pos)) {
            throw RError.getNASubscripted(getEncapsulatingSourceSection());
        } else {
            newSrcArrayBase = srcArrayBase + newAccSrcDimensions * (pos - 1);
        }
        return newSrcArrayBase;
    }

    private int getSrcIndex(int srcArrayBase, int pos, int newAccSrcDimensions) {
        if (posNACheck.check(pos)) {
            throw RError.getNASubscripted(getEncapsulatingSourceSection());
        } else {
            return srcArrayBase + newAccSrcDimensions * (pos - 1);
        }
    }

    private int getSrcArrayBase(int pos, int accSrcDimensions) {
        if (posNACheck.check(pos)) {
            throw RError.getNASubscripted(getEncapsulatingSourceSection());
        } else {
            return accSrcDimensions * (pos - 1);
        }
    }

    private int getReplacementLength(Object[] positions, RAbstractVector value, boolean isList) {
        int valueLength = value.getLength();
        int length = 1;
        for (int i = 0; i < positions.length; i++) {
            RIntVector p = (RIntVector) positions[i];
            int len = p.getLength();
            posNACheck.enable(p);
            boolean allZeros = true;
            for (int j = 0; j < len; j++) {
                int pos = p.getDataAt(j);
                if (pos != 0) {
                    allZeros = false;
                    if (seenNAMultiDim(pos, value, isList)) {
                        continue;
                    }
                }
            }
            if (allZeros) {
                length = 0;
            } else {
                length *= p.getLength();
            }
        }
        if (valueLength != 0 && length != 0 && length % valueLength != 0) {
            throw RError.getNotMultipleReplacement(getEncapsulatingSourceSection());
        }
        return length;
    }

    private int getHighestPos(RIntVector positions) {
        int highestPos = 0;
        posNACheck.enable(positions);
        int numNAs = 0;
        for (int i = 0; i < positions.getLength(); i++) {
            int pos = positions.getDataAt(i);
            if (posNACheck.check(pos)) {
                // ignore
                numNAs++;
                continue;
            } else if (pos < 0) {
                if (-pos > highestPos)
                    highestPos = -pos;
            } else if (pos > highestPos) {
                highestPos = pos;
            }
        }
        if (numNAs == positions.getLength()) {
            return numNAs;
        } else {
            return highestPos;
        }
    }

    private static RStringVector getNamesVector(RVector resultVector) {
        if (resultVector.getNames() == RNull.instance) {
            String[] namesData = new String[resultVector.getLength()];
            Arrays.fill(namesData, RRuntime.NAMES_ATTR_EMPTY_VALUE);
            RStringVector names = RDataFactory.createStringVector(namesData, RDataFactory.COMPLETE_VECTOR);
            resultVector.setNames(names);
            return names;
        } else {
            return (RStringVector) resultVector.getNames();
        }
    }

    private void updateNames(RVector resultVector, RIntVector positions) {
        if (positions.getNames() != RNull.instance) {
            RStringVector names = getNamesVector(resultVector);
            RStringVector newNames = (RStringVector) positions.getNames();
            namesNACheck.enable(newNames);
            for (int i = 0; i < positions.getLength(); i++) {
                int p = positions.getDataAt(i);
                names.updateDataAt(p - 1, newNames.getDataAt(i), namesNACheck);
            }
        }
    }

    // null

    @Specialization(order = 45)
    RNull updateWrongDimensions(Object v, RNull value, RNull vector, int recLevel, Object[] positions) {
        return vector;
    }

    @Specialization(order = 46, guards = {"!wrongDimensionsMatrix", "!wrongDimensions"})
    RNull updateWrongDimensions(Object v, RAbstractVector value, RNull vector, int recLevel, Object[] positions) {
        return vector;
    }

    @Specialization(order = 50)
    RIntVector update(Object v, RAbstractIntVector value, RNull vector, int recLevel, RIntVector positions) {
        int highestPos = getHighestPos(positions);
        int[] data = new int[highestPos];
        Arrays.fill(data, RRuntime.INT_NA);
        return updateSingleDimVector(value, 0, RDataFactory.createIntVector(data, RDataFactory.INCOMPLETE_VECTOR), positions);
    }

    @Specialization(order = 51, guards = {"!isPosNA", "!isPosZero"})
    RIntVector update(Object v, RAbstractIntVector value, RNull vector, int recLevel, int position) {
        if (position > 1) {
            int[] data = new int[position];
            Arrays.fill(data, RRuntime.INT_NA);
            return updateSingleDim(value, RDataFactory.createIntVector(data, RDataFactory.INCOMPLETE_VECTOR), position);
        } else {
            return updateSingleDim(value, RDataFactory.createIntVector(position), position);
        }
    }

    @Specialization(order = 55)
    RDoubleVector update(Object v, RAbstractDoubleVector value, RNull vector, int recLevel, RIntVector positions) {
        int highestPos = getHighestPos(positions);
        double[] data = new double[highestPos];
        Arrays.fill(data, RRuntime.DOUBLE_NA);
        return updateSingleDimVector(value, 0, RDataFactory.createDoubleVector(data, RDataFactory.INCOMPLETE_VECTOR), positions);
    }

    @Specialization(order = 56, guards = {"!isPosNA", "!isPosZero"})
    RDoubleVector update(Object v, RAbstractDoubleVector value, RNull vector, int recLevel, int position) {
        if (position > 1) {
            double[] data = new double[position];
            Arrays.fill(data, RRuntime.DOUBLE_NA);
            return updateSingleDim(value, RDataFactory.createDoubleVector(data, RDataFactory.INCOMPLETE_VECTOR), position);
        } else {
            return updateSingleDim(value, RDataFactory.createDoubleVector(position), position);
        }
    }

    @Specialization(order = 60)
    RLogicalVector update(Object v, RAbstractLogicalVector value, RNull vector, int recLevel, RIntVector positions) {
        int highestPos = getHighestPos(positions);
        byte[] data = new byte[highestPos];
        Arrays.fill(data, RRuntime.LOGICAL_NA);
        return updateSingleDimVector(value, 0, RDataFactory.createLogicalVector(data, RDataFactory.INCOMPLETE_VECTOR), positions);
    }

    @Specialization(order = 61, guards = {"!isPosNA", "!isPosZero"})
    RLogicalVector update(Object v, RAbstractLogicalVector value, RNull vector, int recLevel, int position) {
        if (position > 1) {
            byte[] data = new byte[position];
            Arrays.fill(data, RRuntime.LOGICAL_NA);
            return updateSingleDim(value, RDataFactory.createLogicalVector(data, RDataFactory.INCOMPLETE_VECTOR), position);
        } else {
            return updateSingleDim(value, RDataFactory.createLogicalVector(position), position);
        }
    }

    @Specialization(order = 65)
    RStringVector update(Object v, RAbstractStringVector value, RNull vector, int recLevel, RIntVector positions) {
        int highestPos = getHighestPos(positions);
        String[] data = new String[highestPos];
        Arrays.fill(data, RRuntime.STRING_NA);
        return updateSingleDimVector(value, 0, RDataFactory.createStringVector(data, RDataFactory.INCOMPLETE_VECTOR), positions);
    }

    @Specialization(order = 66, guards = {"!isPosNA", "!isPosZero"})
    RStringVector update(Object v, RAbstractStringVector value, RNull vector, int recLevel, int position) {
        if (position > 1) {
            String[] data = new String[position];
            Arrays.fill(data, RRuntime.STRING_NA);
            return updateSingleDim(value, RDataFactory.createStringVector(data, RDataFactory.INCOMPLETE_VECTOR), position);
        } else {
            return updateSingleDim(value, RDataFactory.createStringVector(position), position);
        }
    }

    @Specialization(order = 70)
    RComplexVector update(Object v, RAbstractComplexVector value, RNull vector, int recLevel, RIntVector positions) {
        int highestPos = getHighestPos(positions);
        double[] data = new double[highestPos << 1];
        int ind = 0;
        for (int i = 0; i < highestPos; i++) {
            data[ind++] = RRuntime.COMPLEX_NA_REAL_PART;
            data[ind++] = RRuntime.COMPLEX_NA_IMAGINARY_PART;
        }
        return updateSingleDimVector(value, 0, RDataFactory.createComplexVector(data, RDataFactory.INCOMPLETE_VECTOR), positions);
    }

    @Specialization(order = 71, guards = {"!isPosNA", "!isPosZero"})
    RComplexVector update(Object v, RAbstractComplexVector value, RNull vector, int recLevel, int position) {
        if (position > 1) {
            double[] data = new double[position << 1];
            int ind = 0;
            for (int i = 0; i < position; i++) {
                data[ind++] = RRuntime.COMPLEX_NA_REAL_PART;
                data[ind++] = RRuntime.COMPLEX_NA_IMAGINARY_PART;
            }
            return updateSingleDim(value, RDataFactory.createComplexVector(data, RDataFactory.INCOMPLETE_VECTOR), position);
        } else {
            return updateSingleDim(value, RDataFactory.createComplexVector(position), position);
        }
    }

    @Specialization(order = 75)
    RRawVector update(Object v, RAbstractRawVector value, RNull vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector(value, 0, RDataFactory.createRawVector(getHighestPos(positions)), positions);
    }

    @Specialization(order = 76, guards = {"!isPosNA", "!isPosZero"})
    RRawVector update(Object v, RAbstractRawVector value, RNull vector, int recLevel, int position) {
        return updateSingleDim(value, RDataFactory.createRawVector(position), position);
    }

    @Specialization(order = 80, guards = {"!isPosNA", "isPositionNegative", "!isVectorList"})
    RList updateNegativeNull(Object v, RNull value, RAbstractVector vector, int recLevel, int position) {
        throw RError.getMoreElementsSupplied(getEncapsulatingSourceSection());
    }

    @Specialization(order = 81, guards = {"!isPosNA", "isPositionNegative", "!outOfBoundsNegative"})
    RList updateNegativeNull(Object v, RNull value, RList vector, int recLevel, int position) {
        throw RError.getSelectMoreThanOne(getEncapsulatingSourceSection());
    }

    @Specialization(order = 82, guards = {"!isPosNA", "isPositionNegative", "outOfBoundsNegative", "oneElemVector"})
    RList updateNegativeOutOfBoundsOneElemNull(Object v, RNull value, RList vector, int recLevel, int position) {
        throw RError.getSelectLessThanOne(getEncapsulatingSourceSection());
    }

    @Specialization(order = 83, guards = {"!isPosNA", "isPositionNegative", "outOfBoundsNegative", "!oneElemVector"})
    RList updateNegativeOutOfBoundsNull(Object v, RNull value, RList vector, int recLevel, int position) {
        throw RError.getSelectMoreThanOne(getEncapsulatingSourceSection());
    }

    @Specialization(order = 85, guards = {"!isPosNA", "isPositionNegative", "!outOfBoundsNegative"})
    RList updateNegative(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, int position) {
        throw RError.getSelectMoreThanOne(getEncapsulatingSourceSection());
    }

    @Specialization(order = 86, guards = {"!isPosNA", "isPositionNegative", "outOfBoundsNegative", "oneElemVector"})
    RList updateNegativeOneElem(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, int position) {
        throw RError.getSelectLessThanOne(getEncapsulatingSourceSection());
    }

    @Specialization(order = 87, guards = {"!isPosNA", "isPositionNegative", "outOfBoundsNegative", "!oneElemVector"})
    RList updateOutOfBoundsNegative(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, int position) {
        throw RError.getSelectMoreThanOne(getEncapsulatingSourceSection());
    }

    // list

    private void setData(RAbstractVector value, RList vector, Object[] positions, int currentDimLevel, int srcArrayBase, int dstArrayBase, int accSrcDimensions, int accDstDimensions) {
        int[] srcDimensions = vector.getDimensions();
        RIntVector p = (RIntVector) positions[currentDimLevel - 1];
        int srcDimSize = srcDimensions[currentDimLevel - 1];
        int newAccSrcDimensions = accSrcDimensions / srcDimSize;
        int newAccDstDimensions = accDstDimensions / p.getLength();
        posNACheck.enable(p);
        if (currentDimLevel == 1) {
            for (int i = 0; i < p.getLength(); i++) {
                int pos = p.getDataAt(i);
                if (seenNAMultiDim(pos, value, true)) {
                    continue;
                }
                int dstIndex = dstArrayBase + newAccDstDimensions * i;
                int srcIndex = getSrcIndex(srcArrayBase, pos, newAccSrcDimensions);
                vector.updateDataAt(srcIndex, value.getDataAtAsObject(dstIndex % value.getLength()), null);
            }
        } else {
            for (int i = 0; i < p.getLength(); i++) {
                int pos = p.getDataAt(i);
                if (seenNAMultiDim(pos, value, true)) {
                    continue;
                }
                int newDstArrayBase = dstArrayBase + newAccDstDimensions * i;
                int newSrcArrayBase = getNewArrayBase(srcArrayBase, pos, newAccSrcDimensions);
                setData(value, vector, positions, currentDimLevel - 1, newSrcArrayBase, newDstArrayBase, newAccSrcDimensions, newAccDstDimensions);
            }
        }
    }

    private RList updateVector(RAbstractVector value, RList vector, Object[] positions) {
        int replacementLength = getReplacementLength(positions, value, true);
        RList resultVector = vector;
        if (replacementLength == 0) {
            return resultVector;
        }
        if (vector.isShared()) {
            resultVector = (RList) vector.copy();
        }
        int[] srcDimensions = resultVector.getDimensions();
        int numSrcDimensions = srcDimensions.length;
        int srcDimSize = srcDimensions[numSrcDimensions - 1];
        int accSrcDimensions = resultVector.getLength() / srcDimSize;
        RIntVector p = (RIntVector) positions[positions.length - 1];
        int accDstDimensions = replacementLength / p.getLength();
        posNACheck.enable(p);
        for (int i = 0; i < p.getLength(); i++) {
            int dstArrayBase = accDstDimensions * i;
            int pos = p.getDataAt(i);
            if (seenNAMultiDim(pos, value, true)) {
                continue;
            }
            int srcArrayBase = getSrcArrayBase(pos, accSrcDimensions);
            setData(value, resultVector, positions, numSrcDimensions - 1, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions);
        }
        return resultVector;
    }

    private static RList getResultVector(RList vector, int highestPos, boolean resetDims) {
        RList resultVector = vector;
        if (resultVector.isShared()) {
            resultVector = (RList) vector.copy();
        }
        if (resultVector.getLength() < highestPos) {
            int orgLength = resultVector.getLength();
            resultVector.resizeWithNames(highestPos);
            for (int i = orgLength; i < highestPos; i++) {
                resultVector.updateDataAt(i, RNull.instance, null);
            }
        } else if (resetDims) {
            resultVector.setDimensions(null);
            resultVector.setDimNames(null);
        }
        return resultVector;
    }

    private int getPositionInRecursion(RList vector, int position, int recLevel, boolean lastPos) {
        if (RRuntime.isNA(position)) {
            if (lastPos && recLevel > 0) {
                throw RError.getSelectLessThanOne(getEncapsulatingSourceSection());
            } else if (recLevel == 0) {
                throw RError.getSelectMoreThanOne(getEncapsulatingSourceSection());
            } else {
                throw RError.getNoSuchIndexAtLevel(getEncapsulatingSourceSection(), recLevel + 1);
            }
        } else if (!lastPos && position > vector.getLength()) {
            throw RError.getNoSuchIndexAtLevel(getEncapsulatingSourceSection(), recLevel + 1);
        } else if (position < 0) {
            return AccessArrayNode.getPositionFromNegative(vector, position, getEncapsulatingSourceSection());
        } else if (position == 0) {
            throw RError.getSelectLessThanOne(getEncapsulatingSourceSection());
        }
        return position;
    }

    private static RList updateSingleDim(RAbstractVector value, RList resultVector, int position) {
        resultVector.updateDataAt(position - 1, value.getDataAtAsObject(0), null);
        return resultVector;
    }

    private RList updateSingleDimRec(RList value, RList resultVector, RIntVector p, int recLevel) {
        int position = getPositionInRecursion(resultVector, p.getDataAt(0), recLevel, true);
        resultVector.updateDataAt(position - 1, value, null);
        updateNames(resultVector, p);
        return resultVector;
    }

    private RList updateSingleDimRec(RAbstractVector value, RList resultVector, RIntVector p, int recLevel) {
        int position = getPositionInRecursion(resultVector, p.getDataAt(0), recLevel, true);
        resultVector.updateDataAt(position - 1, value.getDataAtAsObject(0), null);
        updateNames(resultVector, p);
        return resultVector;
    }

    private RList updateSingleDimVector(RAbstractVector value, int orgVectorLength, RList resultVector, RIntVector positions) {
        for (int i = 0; i < positions.getLength(); i++) {
            int p = positions.getDataAt(i);
            if (seenNA(p, value)) {
                continue;
            }
            if (p < 0) {
                int pos = -(p + 1);
                if (pos >= orgVectorLength) {
                    resultVector.updateDataAt(pos, RNull.instance, null);
                }
            } else {
                resultVector.updateDataAt(p - 1, value.getDataAtAsObject(i % value.getLength()), null);
            }
        }
        if (positions.getLength() % value.getLength() != 0) {
            RContext.getInstance().setEvalWarning(RError.NOT_MULTIPLE_REPLACEMENT);
        }
        updateNames(resultVector, positions);
        return resultVector;
    }

    @Specialization(order = 100, guards = {"multiDim", "!wrongDimensionsMatrix", "!wrongDimensions"})
    RList update(Object v, RAbstractVector value, RList vector, int recLevel, Object[] positions) {
        return updateVector(value, vector, positions);
    }

    @Specialization(order = 105, guards = {"isSubset", "!posNames", "multiPos"})
    RList update(Object v, RAbstractVector value, RList vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions), false), positions);
    }

    @Specialization(order = 106, guards = {"isSubset", "!posNames", "onePosition"})
    Object updateOne(VirtualFrame frame, Object v, RAbstractVector value, RList vector, int recLevel, RIntVector positions) {
        return updateRecursive(frame, v, value, vector, recLevel, positions.getDataAt(0));
    }

    @Specialization(order = 107, guards = {"isSubset", "posNames"})
    RList updateNames(Object v, RAbstractVector value, RList vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions), false), positions);
    }

    @Specialization(order = 111, guards = {"!isValueLengthOne", "isSubset", "!isPosNA", "!isPosZero", "!isPositionNegative"})
    RList updateTooManyValuesSubset(Object v, RAbstractVector value, RList vector, int recLevel, int position) {
        RContext.getInstance().setEvalWarning(RError.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim(value, getResultVector(vector, position, false), position);
    }

    @Specialization(order = 112, guards = {"isValueLengthOne", "isSubset", "!isPosNA", "!isPosZero", "!isPositionNegative"})
    RList update(Object v, RAbstractVector value, RList vector, int recLevel, int position) {
        return updateSingleDim(value, getResultVector(vector, position, false), position);
    }

    @Specialization(order = 113, guards = {"!isSubset", "!isPosNA", "!isPosZero", "!isPositionNegative"})
    RList updateTooManyValuesSubscript(Object v, RAbstractVector value, RList vector, int recLevel, int position) {
        RList resultVector = getResultVector(vector, position, false);
        resultVector.updateDataAt(position - 1, value, null);
        return resultVector;
    }

    @Specialization(order = 118, guards = "isPosNA")
    RList updateListNullValue(Object v, RNull value, RList vector, int recLevel, int position) {
        return vector;
    }

    @Specialization(order = 119, guards = {"!isPosZero", "emptyList", "!isPosNA", "!isPositionNegative"})
    RList updateEmptyList(Object v, RNull value, RList vector, int recLevel, int position) {
        return vector;
    }

    private RList removeElement(RList vector, int position, boolean inRecursion, boolean resetDims) {
        if (position > vector.getLength()) {
            if (inRecursion || !isSubset) {
                // simply return the vector unchanged
                return vector;
            } else {
                // this is equivalent to extending the vector to appropriate length and then
                // removing the last element
                return getResultVector(vector, position - 1, resetDims);
            }
        } else {
            Object[] data = new Object[vector.getLength() - 1];
            RStringVector orgNames = null;
            String[] namesData = null;
            if (vector.getNames() != RNull.instance) {
                namesData = new String[vector.getLength() - 1];
                orgNames = (RStringVector) vector.getNames();
            }

            int ind = 0;
            for (int i = 0; i < vector.getLength(); i++) {
                if (i != (position - 1)) {
                    data[ind] = vector.getDataAt(i);
                    if (orgNames != null) {
                        namesData[ind] = orgNames.getDataAt(i);
                    }
                    ind++;
                }
            }

            RList result;
            if (orgNames == null) {
                result = RDataFactory.createList(data);
            } else {
                result = RDataFactory.createList(data, RDataFactory.createStringVector(namesData, vector.isComplete()));
            }
            result.copyRegAttributesFrom(vector);
            return result;
        }
    }

    @Specialization(order = 120, guards = {"!isPosZero", "!emptyList", "!isPosNA", "!isPositionNegative"})
    RList update(Object v, RNull value, RList vector, int recLevel, int position) {
        return removeElement(vector, position, false, isSubset);
    }

    private static final Object DELETE_MARKER = new Object();

    @Specialization(order = 121, guards = {"isSubset", "noPosition"})
    RList updateEmptyPos(Object v, RNull value, RList vector, int recLevel, RIntVector positions) {
        return vector;
    }

    @Specialization(order = 122, guards = {"isSubset", "!noPosition"})
    RList update(Object v, RNull value, RList vector, int recLevel, RIntVector positions) {
        if (!isSubset) {
            throw RError.getMoreElementsSupplied(getEncapsulatingSourceSection());
        }

        RList list = vector;
        if (list.isShared()) {
            list = (RList) vector.copy();
        }
        int highestPos = getHighestPos(positions);
        if (list.getLength() < highestPos) {
            // to mark duplicate deleted elements with positions > vector length
            list = list.copyResized(highestPos, false);
        }
        int posDeleted = 0;
        for (int i = 0; i < positions.getLength(); i++) {
            int pos = positions.getDataAt(i);
            if (RRuntime.isNA(pos)) {
                continue;
            }
            if (list.getDataAt(pos - 1) != DELETE_MARKER) {
                list.updateDataAt(pos - 1, DELETE_MARKER, null);
                // count each position only once
                posDeleted++;
            }
        }
        int resultVectorLength = highestPos > list.getLength() ? highestPos - posDeleted : list.getLength() - posDeleted;
        Object[] data = new Object[resultVectorLength];
        RStringVector orgNames = null;
        String[] namesData = null;
        if (vector.getNames() != RNull.instance) {
            namesData = new String[resultVectorLength];
            orgNames = (RStringVector) vector.getNames();
        }

        int ind = 0;
        for (int i = 0; i < vector.getLength(); i++) {
            Object el = list.getDataAt(i);
            if (el != DELETE_MARKER) {
                data[ind] = el;
                if (orgNames != null) {
                    namesData[ind] = orgNames.getDataAt(i);
                }
                ind++;
            }
        }
        for (; ind < data.length; ind++) {
            data[ind] = RNull.instance;
            if (orgNames != null) {
                namesData[ind] = RRuntime.NAMES_ATTR_EMPTY_VALUE;
            }
        }
        RList result;
        if (orgNames == null) {
            result = RDataFactory.createList(data);
        } else {
            result = RDataFactory.createList(data, RDataFactory.createStringVector(namesData, orgNames.isComplete()));
        }
        result.copyRegAttributesFrom(vector);
        return result;
    }

    private Object updateRecursive(VirtualFrame frame, Object v, Object value, RList vector, int recLevel, RIntVector p) {
        int position = getPositionInRecursion(vector, p.getDataAt(0), recLevel, false);
        if (p.getLength() == 2 && RRuntime.isNA(p.getDataAt(1))) {
            // catch it here, otherwise it will get caught at lower level of recursion resulting in
            // a different message
            throw RError.getSelectLessThanOne(getEncapsulatingSourceSection());
        }
        Object el;
        if (p.getLength() == 2) {
            Object finalVector = coerceOperand(frame, vector.getDataAt(position - 1), value);
            Object lastPosition = castPosition(frame, finalVector, convertOperand(frame, finalVector, p.getDataAt(1)));
            el = updateRecursive(frame, v, value, finalVector, lastPosition, recLevel + 1);
        } else {
            RIntVector newP = AccessArrayNode.popHead(p, posNACheck);
            el = updateRecursive(frame, v, value, vector.getDataAt(position - 1), newP, recLevel + 1);
        }

        RList resultList = vector;
        if (vector.isShared()) {
            resultList = (RList) vector.copy();
        }
        resultList.updateDataAt(position - 1, el, null);
        return resultList;
    }

    @Specialization(order = 150, guards = {"!isSubset", "multiPos"})
    Object access(VirtualFrame frame, Object v, RNull value, RList vector, int recLevel, RIntVector p) {
        return updateRecursive(frame, v, value, vector, recLevel, p);
    }

    @Specialization(order = 151, guards = {"!isSubset", "multiPos"})
    Object access(VirtualFrame frame, Object v, RAbstractVector value, RList vector, int recLevel, RIntVector p) {
        return updateRecursive(frame, v, value, vector, recLevel, p);
    }

    @Specialization(order = 160, guards = {"!isSubset", "inRecursion", "multiPos"})
    Object accessRecFailed(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, RIntVector p) {
        throw RError.getRecursiveIndexingFailed(getEncapsulatingSourceSection(), recLevel + 1);
    }

    @Specialization(order = 170, guards = {"!isSubset", "!multiPos"})
    Object accessSubscriptListValue(Object v, RList value, RList vector, int recLevel, RIntVector p) {
        int position = getPositionInRecursion(vector, p.getDataAt(0), recLevel, true);
        return updateSingleDimRec(value, getResultVector(vector, position, false), p, recLevel);
    }

    @Specialization(order = 171, guards = {"!isSubset", "inRecursion", "!multiPos"})
    Object accessSubscriptNullValueInRecursion(Object v, RNull value, RList vector, int recLevel, RIntVector p) {
        int position = getPositionInRecursion(vector, p.getDataAt(0), recLevel, true);
        return removeElement(vector, position, true, false);
    }

    @Specialization(order = 172, guards = {"!isSubset", "!inRecursion", "!multiPos"})
    Object accessSubscriptNullValue(Object v, RNull value, RList vector, int recLevel, RIntVector p) {
        int position = getPositionInRecursion(vector, p.getDataAt(0), recLevel, true);
        return removeElement(vector, position, false, false);
    }

    @Specialization(order = 173, guards = {"!isSubset", "!multiPos"})
    Object accessSubscript(Object v, RAbstractVector value, RList vector, int recLevel, RIntVector p) {
        int position = getPositionInRecursion(vector, p.getDataAt(0), recLevel, true);
        return updateSingleDimRec(value, getResultVector(vector, position, false), p, recLevel);
    }

    @Specialization(order = 180, guards = {"!isValueLengthOne", "!isSubset", "!isPosNA", "!isPosZero"})
    RAbstractVector updateTooManyValues(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, int position) {
        throw RError.getMoreElementsSupplied(getEncapsulatingSourceSection());
    }

    // null value (with vectors)

    @Specialization(order = 185, guards = {"isPosZero", "!isVectorList"})
    RAbstractVector updatePosZero(Object v, RNull value, RAbstractVector vector, int recLevel, int position) {
        if (!isSubset) {
            throw RError.getReplacementZero(getEncapsulatingSourceSection());
        }
        return vector;
    }

    @Specialization(order = 186, guards = {"!isPosZero", "!isPosNA", "!isVectorList"})
    RAbstractVector update(Object v, RNull value, RAbstractVector vector, int recLevel, int position) {
        if (!isSubset) {
            throw RError.getMoreElementsSupplied(getEncapsulatingSourceSection());
        } else {
            throw RError.getReplacementZero(getEncapsulatingSourceSection());
        }
    }

    @Specialization(order = 190, guards = {"isSubset", "!isVectorList", "noPosition"})
    RAbstractVector updateNullSubsetNoPos(Object v, RNull value, RAbstractVector vector, int recLevel, RIntVector positions) {
        return vector;
    }

    @Specialization(order = 191, guards = {"isSubset", "!isVectorList", "!noPosition"})
    RAbstractVector updateNullSubset(Object v, RNull value, RAbstractVector vector, int recLevel, RIntVector positions) {
        throw RError.getReplacementZero(getEncapsulatingSourceSection());
    }

    @Specialization(order = 192, guards = {"!isSubset", "!isVectorList", "!twoPositions"})
    RAbstractVector updateNull(Object v, RNull value, RAbstractVector vector, int recLevel, RIntVector positions) {
        throw RError.getSelectMoreThanOne(getEncapsulatingSourceSection());
    }

    @Specialization(order = 193, guards = {"!isSubset", "!isVectorList", "twoPositions", "firstPosZero"})
    RAbstractVector updateNullTwoElemsZero(Object v, RNull value, RAbstractVector vector, int recLevel, RIntVector positions) {
        throw RError.getSelectLessThanOne(getEncapsulatingSourceSection());
    }

    @Specialization(order = 194, guards = {"!isSubset", "!isVectorList", "twoPositions", "!firstPosZero"})
    RAbstractVector updateNullTwoElems(Object v, RNull value, RAbstractVector vector, int recLevel, RIntVector positions) {
        throw RError.getSelectMoreThanOne(getEncapsulatingSourceSection());
    }

    // int vector

    private void setData(RAbstractIntVector value, RIntVector vector, Object[] positions, int currentDimLevel, int srcArrayBase, int dstArrayBase, int accSrcDimensions, int accDstDimensions) {
        int[] srcDimensions = vector.getDimensions();
        RIntVector p = (RIntVector) positions[currentDimLevel - 1];
        int srcDimSize = srcDimensions[currentDimLevel - 1];
        int newAccSrcDimensions = accSrcDimensions / srcDimSize;
        int newAccDstDimensions = accDstDimensions / p.getLength();
        posNACheck.enable(p);
        if (currentDimLevel == 1) {
            for (int i = 0; i < p.getLength(); i++) {
                int pos = p.getDataAt(i);
                if (seenNAMultiDim(pos, value, false)) {
                    continue;
                }
                int dstIndex = dstArrayBase + newAccDstDimensions * i;
                int srcIndex = getSrcIndex(srcArrayBase, pos, newAccSrcDimensions);
                vector.updateDataAt(srcIndex, value.getDataAt(dstIndex % value.getLength()), elementNACheck);
            }
        } else {
            for (int i = 0; i < p.getLength(); i++) {
                int pos = p.getDataAt(i);
                if (seenNAMultiDim(pos, value, false)) {
                    continue;
                }
                int newDstArrayBase = dstArrayBase + newAccDstDimensions * i;
                int newSrcArrayBase = getNewArrayBase(srcArrayBase, pos, newAccSrcDimensions);
                setData(value, vector, positions, currentDimLevel - 1, newSrcArrayBase, newDstArrayBase, newAccSrcDimensions, newAccDstDimensions);
            }
        }
    }

    private RIntVector updateVector(RAbstractIntVector value, RAbstractIntVector vector, Object[] positions) {
        int replacementLength = getReplacementLength(positions, value, false);
        RIntVector resultVector = vector.materialize();
        if (replacementLength == 0) {
            return resultVector;
        }
        if (resultVector.isShared()) {
            resultVector = (RIntVector) vector.copy();
        }
        int[] srcDimensions = resultVector.getDimensions();
        int numSrcDimensions = srcDimensions.length;
        int srcDimSize = srcDimensions[numSrcDimensions - 1];
        int accSrcDimensions = resultVector.getLength() / srcDimSize;
        RIntVector p = (RIntVector) positions[positions.length - 1];
        int accDstDimensions = replacementLength / p.getLength();
        elementNACheck.enable(value);
        posNACheck.enable(p);
        for (int i = 0; i < p.getLength(); i++) {
            int dstArrayBase = accDstDimensions * i;
            int pos = p.getDataAt(i);
            if (seenNAMultiDim(pos, value, false)) {
                continue;
            }
            int srcArrayBase = getSrcArrayBase(pos, accSrcDimensions);
            setData(value, resultVector, positions, numSrcDimensions - 1, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions);
        }
        return resultVector;
    }

    private static RIntVector getResultVector(RAbstractIntVector vector, int highestPos) {
        RIntVector resultVector = vector.materialize();
        if (resultVector.isShared()) {
            resultVector = (RIntVector) vector.copy();
        }
        if (resultVector.getLength() < highestPos) {
            resultVector.resizeWithNames(highestPos);
        }
        return resultVector;
    }

    private RIntVector updateSingleDim(RAbstractIntVector value, RIntVector resultVector, int position) {
        elementNACheck.enable(value);
        resultVector.updateDataAt(position - 1, value.getDataAt(0), elementNACheck);
        return resultVector;
    }

    private boolean seenNA(int p, RAbstractVector value) {
        if (posNACheck.check(p) || p < 0) {
            if (value.getLength() == 1) {
                return true;
            } else {
                throw RError.getNASubscripted(getEncapsulatingSourceSection());
            }
        } else {
            return false;
        }
    }

    private boolean seenNAMultiDim(int p, RAbstractVector value, boolean isList) {
        if (posNACheck.check(p)) {
            if (value.getLength() == 1) {
                if (!isSubset) {
                    throw RError.getSubscriptBoundsSub(getEncapsulatingSourceSection());
                } else {
                    return true;
                }
            } else {
                if (!isSubset) {
                    if (isList) {
                        throw RError.getSubscriptBoundsSub(getEncapsulatingSourceSection());
                    } else {
                        throw RError.getMoreElementsSupplied(getEncapsulatingSourceSection());
                    }
                } else {
                    throw RError.getNASubscripted(getEncapsulatingSourceSection());
                }
            }
        } else {
            return false;
        }

    }

    private RIntVector updateSingleDimVector(RAbstractIntVector value, int orgVectorLength, RIntVector resultVector, RIntVector positions) {
        elementNACheck.enable(value);
        for (int i = 0; i < positions.getLength(); i++) {
            int p = positions.getDataAt(i);
            if (seenNA(p, value)) {
                continue;
            }
            if (p < 0) {
                int pos = -(p + 1);
                if (pos >= orgVectorLength) {
                    resultVector.updateDataAt(pos, RRuntime.INT_NA, elementNACheck);
                }
            } else {
                resultVector.updateDataAt(p - 1, value.getDataAt(i % value.getLength()), elementNACheck);
            }
        }
        if (positions.getLength() % value.getLength() != 0) {
            RContext.getInstance().setEvalWarning(RError.NOT_MULTIPLE_REPLACEMENT);
        }
        updateNames(resultVector, positions);
        return resultVector;
    }

    @Specialization(order = 195, guards = {"!isSubset", "!isVectorList", "!posNames", "!twoPositions"})
    Object update(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, RIntVector positions) {
        throw RError.getSelectMoreThanOne(getEncapsulatingSourceSection());
    }

    @Specialization(order = 196, guards = {"!isSubset", "!isVectorList", "!posNames", "twoPositions", "firstPosZero"})
    RList updateTwoElemsZero(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, RIntVector positions) {
        throw RError.getSelectLessThanOne(getEncapsulatingSourceSection());
    }

    @Specialization(order = 197, guards = {"!isSubset", "!isVectorList", "!posNames", "twoPositions", "!firstPosZero"})
    RList updateTwoElems(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, RIntVector positions) {
        throw RError.getSelectMoreThanOne(getEncapsulatingSourceSection());
    }

    @Specialization(order = 200, guards = {"multiDim", "!wrongDimensionsMatrix", "!wrongDimensions"})
    RIntVector update(Object v, RAbstractIntVector value, RAbstractIntVector vector, int recLevel, Object[] positions) {
        return updateVector(value, vector, positions);
    }

    @Specialization(order = 202, guards = {"multiDim", "!wrongDimensionsMatrix", "!wrongDimensions"})
    RIntVector update(VirtualFrame frame, Object v, RAbstractLogicalVector value, RAbstractIntVector vector, int recLevel, Object[] positions) {
        return updateVector((RIntVector) castInteger(frame, value), vector, positions);
    }

    @Specialization(order = 220, guards = {"isSubset", "!posNames", "multiPos"})
    RAbstractIntVector updateSubset(Object v, RAbstractIntVector value, RAbstractIntVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 221, guards = {"isSubset", "!posNames", "onePosition"})
    Object updateSubsetOne(VirtualFrame frame, Object v, RAbstractIntVector value, RAbstractIntVector vector, int recLevel, RIntVector positions) {
        return updateRecursive(frame, v, value, vector, recLevel, positions.getDataAt(0));
    }

    @Specialization(order = 222, guards = {"isSubset", "posNames"})
    RAbstractIntVector updateSubsetNames(Object v, RAbstractIntVector value, RAbstractIntVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 223, guards = {"!isSubset", "posNames"})
    RAbstractIntVector update(Object v, RAbstractIntVector value, RAbstractIntVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 224, guards = {"!isValueLengthOne", "isSubset", "!isPosNA", "!isPosZero"})
    RIntVector updateTooManyValuesSubset(Object v, RAbstractIntVector value, RAbstractIntVector vector, int recLevel, int position) {
        RContext.getInstance().setEvalWarning(RError.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    @Specialization(order = 225, guards = {"isValueLengthOne", "!isPosNA", "!isPosZero"})
    RIntVector update(Object v, RAbstractIntVector value, RAbstractIntVector vector, int recLevel, int position) {
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    @Specialization(order = 240, guards = {"isSubset", "!posNames", "multiPos"})
    RAbstractIntVector updateSubset(VirtualFrame frame, Object v, RAbstractLogicalVector value, RAbstractIntVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector((RIntVector) castInteger(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 241, guards = {"isSubset", "!posNames", "onePosition"})
    Object updateSubsetOne(VirtualFrame frame, Object v, RAbstractLogicalVector value, RAbstractIntVector vector, int recLevel, RIntVector positions) {
        return updateRecursive(frame, v, value, vector, recLevel, positions.getDataAt(0));
    }

    @Specialization(order = 242, guards = {"isSubset", "posNames"})
    RAbstractIntVector updateSubsetNames(VirtualFrame frame, Object v, RAbstractLogicalVector value, RAbstractIntVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector((RIntVector) castInteger(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 243, guards = {"!isSubset", "posNames"})
    RAbstractIntVector update(VirtualFrame frame, Object v, RAbstractLogicalVector value, RAbstractIntVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector((RIntVector) castInteger(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 244, guards = {"!isValueLengthOne", "isSubset", "!isPosNA", "!isPosZero"})
    RIntVector updateTooManyValuesSubset(VirtualFrame frame, Object v, RAbstractLogicalVector value, RAbstractIntVector vector, int recLevel, int position) {
        RContext.getInstance().setEvalWarning(RError.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim((RIntVector) castInteger(frame, value), getResultVector(vector, position), position);
    }

    @Specialization(order = 245, guards = {"isValueLengthOne", "!isPosNA", "!isPosZero"})
    RIntVector update(VirtualFrame frame, Object v, RAbstractLogicalVector value, RAbstractIntVector vector, int recLevel, int position) {
        return updateSingleDim((RIntVector) castInteger(frame, value), getResultVector(vector, position), position);
    }

    // double vector

    private void setData(RAbstractDoubleVector value, RDoubleVector vector, Object[] positions, int currentDimLevel, int srcArrayBase, int dstArrayBase, int accSrcDimensions, int accDstDimensions) {
        int[] srcDimensions = vector.getDimensions();
        RIntVector p = (RIntVector) positions[currentDimLevel - 1];
        int srcDimSize = srcDimensions[currentDimLevel - 1];
        int newAccSrcDimensions = accSrcDimensions / srcDimSize;
        int newAccDstDimensions = accDstDimensions / p.getLength();
        posNACheck.enable(p);
        if (currentDimLevel == 1) {
            for (int i = 0; i < p.getLength(); i++) {
                int pos = p.getDataAt(i);
                if (seenNAMultiDim(pos, value, false)) {
                    continue;
                }
                int dstIndex = dstArrayBase + newAccDstDimensions * i;
                int srcIndex = getSrcIndex(srcArrayBase, pos, newAccSrcDimensions);
                vector.updateDataAt(srcIndex, value.getDataAt(dstIndex % value.getLength()), elementNACheck);
            }
        } else {
            for (int i = 0; i < p.getLength(); i++) {
                int pos = p.getDataAt(i);
                if (seenNAMultiDim(pos, value, false)) {
                    continue;
                }
                int newDstArrayBase = dstArrayBase + newAccDstDimensions * i;
                int newSrcArrayBase = getNewArrayBase(srcArrayBase, pos, newAccSrcDimensions);
                setData(value, vector, positions, currentDimLevel - 1, newSrcArrayBase, newDstArrayBase, newAccSrcDimensions, newAccDstDimensions);
            }
        }
    }

    private RDoubleVector updateVector(RAbstractDoubleVector value, RAbstractDoubleVector vector, Object[] positions) {
        int replacementLength = getReplacementLength(positions, value, false);
        RDoubleVector resultVector = vector.materialize();
        if (replacementLength == 0) {
            return resultVector;
        }
        if (resultVector.isShared()) {
            resultVector = (RDoubleVector) vector.copy();
        }
        int[] srcDimensions = resultVector.getDimensions();
        int numSrcDimensions = srcDimensions.length;
        int srcDimSize = srcDimensions[numSrcDimensions - 1];
        int accSrcDimensions = resultVector.getLength() / srcDimSize;
        RIntVector p = (RIntVector) positions[positions.length - 1];
        int accDstDimensions = replacementLength / p.getLength();
        elementNACheck.enable(value);
        posNACheck.enable(p);
        for (int i = 0; i < p.getLength(); i++) {
            int dstArrayBase = accDstDimensions * i;
            int pos = p.getDataAt(i);
            if (seenNAMultiDim(pos, value, false)) {
                continue;
            }
            int srcArrayBase = getSrcArrayBase(pos, accSrcDimensions);
            setData(value, resultVector, positions, numSrcDimensions - 1, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions);
        }
        return resultVector;
    }

    private static RDoubleVector getResultVector(RAbstractDoubleVector vector, int highestPos) {
        RDoubleVector resultVector = vector.materialize();
        if (resultVector.isShared()) {
            resultVector = (RDoubleVector) vector.copy();
        }
        if (resultVector.getLength() < highestPos) {
            resultVector.resizeWithNames(highestPos);
        }
        return resultVector;
    }

    private RDoubleVector updateSingleDim(RAbstractDoubleVector value, RDoubleVector resultVector, int position) {
        elementNACheck.enable(value);
        resultVector.updateDataAt(position - 1, value.getDataAt(0), elementNACheck);
        return resultVector;
    }

    private RDoubleVector updateSingleDimVector(RAbstractDoubleVector value, int orgVectorLength, RDoubleVector resultVector, RIntVector positions) {
        for (int i = 0; i < positions.getLength(); i++) {
            int p = positions.getDataAt(i);
            if (seenNA(p, value)) {
                continue;
            }
            if (p < 0) {
                int pos = -(p + 1);
                if (pos >= orgVectorLength) {
                    resultVector.updateDataAt(pos, RRuntime.DOUBLE_NA, elementNACheck);
                }
            } else {
                resultVector.updateDataAt(p - 1, value.getDataAt(i % value.getLength()), elementNACheck);
            }
        }
        if (value.getLength() == 0) {
            Utils.nyi();
        }
        if (positions.getLength() % value.getLength() != 0) {
            RContext.getInstance().setEvalWarning(RError.NOT_MULTIPLE_REPLACEMENT);
        }
        updateNames(resultVector, positions);
        return resultVector;
    }

    @Specialization(order = 300, guards = {"multiDim", "!wrongDimensionsMatrix", "!wrongDimensions"})
    RDoubleVector update(VirtualFrame frame, Object v, RAbstractIntVector value, RAbstractDoubleVector vector, int recLevel, Object[] positions) {
        return updateVector((RDoubleVector) castDouble(frame, value), vector, positions);
    }

    @Specialization(order = 301, guards = {"multiDim", "!wrongDimensionsMatrix", "!wrongDimensions"})
    RDoubleVector update(Object v, RAbstractDoubleVector value, RAbstractDoubleVector vector, int recLevel, Object[] positions) {
        return updateVector(value, vector, positions);
    }

    @Specialization(order = 302, guards = {"multiDim", "!wrongDimensionsMatrix", "!wrongDimensions"})
    RDoubleVector update(VirtualFrame frame, Object v, RAbstractLogicalVector value, RAbstractDoubleVector vector, int recLevel, Object[] positions) {
        return updateVector((RDoubleVector) castDouble(frame, value), vector, positions);
    }

    @Specialization(order = 320, guards = {"isSubset", "!posNames", "multiPos"})
    RAbstractDoubleVector updateSubset(VirtualFrame frame, Object v, RAbstractIntVector value, RAbstractDoubleVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector((RDoubleVector) castDouble(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 321, guards = {"isSubset", "!posNames", "onePosition"})
    Object updateSubsetOne(VirtualFrame frame, Object v, RAbstractIntVector value, RAbstractDoubleVector vector, int recLevel, RIntVector positions) {
        return updateRecursive(frame, v, value, vector, recLevel, positions.getDataAt(0));
    }

    @Specialization(order = 322, guards = {"isSubset", "posNames"})
    RAbstractDoubleVector updateSubsetNames(VirtualFrame frame, Object v, RAbstractIntVector value, RAbstractDoubleVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector((RDoubleVector) castDouble(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 323, guards = {"!isSubset", "posNames"})
    RAbstractDoubleVector update(VirtualFrame frame, Object v, RAbstractIntVector value, RAbstractDoubleVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector((RDoubleVector) castDouble(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 324, guards = {"!isValueLengthOne", "isSubset", "!isPosNA", "!isPosZero"})
    RDoubleVector updateTooManyValuesSubset(VirtualFrame frame, Object v, RAbstractIntVector value, RAbstractDoubleVector vector, int recLevel, int position) {
        RContext.getInstance().setEvalWarning(RError.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim((RDoubleVector) castDouble(frame, value), getResultVector(vector, position), position);
    }

    @Specialization(order = 325, guards = {"isValueLengthOne", "!isPosNA", "!isPosZero"})
    RDoubleVector update(VirtualFrame frame, Object v, RAbstractIntVector value, RAbstractDoubleVector vector, int recLevel, int position) {
        return updateSingleDim((RDoubleVector) castDouble(frame, value), getResultVector(vector, position), position);
    }

    @Specialization(order = 330, guards = {"isSubset", "!posNames", "multiPos"})
    RAbstractDoubleVector updateSubset(Object v, RAbstractDoubleVector value, RAbstractDoubleVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 331, guards = {"isSubset", "!posNames", "onePosition"})
    Object updateSubsetOne(VirtualFrame frame, Object v, RAbstractDoubleVector value, RAbstractDoubleVector vector, int recLevel, RIntVector positions) {
        return updateRecursive(frame, v, value, vector, recLevel, positions.getDataAt(0));
    }

    @Specialization(order = 332, guards = {"isSubset", "posNames"})
    RAbstractDoubleVector updateSubsetNames(VirtualFrame frame, Object v, RAbstractDoubleVector value, RAbstractDoubleVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector((RDoubleVector) castDouble(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 333, guards = {"!isSubset", "posNames"})
    RAbstractDoubleVector update(Object v, RAbstractDoubleVector value, RAbstractDoubleVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 334, guards = {"!isValueLengthOne", "isSubset", "!isPosNA", "!isPosZero"})
    RDoubleVector updateTooManyValuesSubset(Object v, RAbstractDoubleVector value, RAbstractDoubleVector vector, int recLevel, int position) {
        RContext.getInstance().setEvalWarning(RError.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    @Specialization(order = 335, guards = {"isValueLengthOne", "!isPosNA", "!isPosZero"})
    RDoubleVector update(Object v, RAbstractDoubleVector value, RAbstractDoubleVector vector, int recLevel, int position) {
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    @Specialization(order = 340, guards = {"isSubset", "!posNames", "multiPos"})
    RAbstractDoubleVector updateSubset(VirtualFrame frame, Object v, RAbstractLogicalVector value, RAbstractDoubleVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector((RDoubleVector) castDouble(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 341, guards = {"isSubset", "!posNames", "onePosition"})
    Object updateSubsetOne(VirtualFrame frame, Object v, RAbstractLogicalVector value, RAbstractDoubleVector vector, int recLevel, RIntVector positions) {
        return updateRecursive(frame, v, value, vector, recLevel, positions.getDataAt(0));
    }

    @Specialization(order = 342, guards = {"isSubset", "posNames"})
    RAbstractDoubleVector updateSubsetNames(VirtualFrame frame, Object v, RAbstractLogicalVector value, RAbstractDoubleVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector((RDoubleVector) castDouble(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 343, guards = {"!isSubset", "posNames"})
    RAbstractDoubleVector update(VirtualFrame frame, Object v, RAbstractLogicalVector value, RAbstractDoubleVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector((RDoubleVector) castDouble(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 344, guards = {"!isValueLengthOne", "isSubset", "!isPosNA", "!isPosZero"})
    RDoubleVector updateTooManyValuesSubset(VirtualFrame frame, Object v, RAbstractLogicalVector value, RAbstractDoubleVector vector, int recLevel, int position) {
        RContext.getInstance().setEvalWarning(RError.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim((RDoubleVector) castDouble(frame, value), getResultVector(vector, position), position);
    }

    @Specialization(order = 345, guards = {"isValueLengthOne", "!isPosNA", "!isPosZero"})
    RDoubleVector update(VirtualFrame frame, Object v, RAbstractLogicalVector value, RAbstractDoubleVector vector, int recLevel, int position) {
        return updateSingleDim((RDoubleVector) castDouble(frame, value), getResultVector(vector, position), position);
    }

    // logical vector

    private void setData(RAbstractLogicalVector value, RLogicalVector vector, Object[] positions, int currentDimLevel, int srcArrayBase, int dstArrayBase, int accSrcDimensions, int accDstDimensions) {
        int[] srcDimensions = vector.getDimensions();
        RIntVector p = (RIntVector) positions[currentDimLevel - 1];
        int srcDimSize = srcDimensions[currentDimLevel - 1];
        int newAccSrcDimensions = accSrcDimensions / srcDimSize;
        int newAccDstDimensions = accDstDimensions / p.getLength();
        posNACheck.enable(p);
        if (currentDimLevel == 1) {
            for (int i = 0; i < p.getLength(); i++) {
                int pos = p.getDataAt(i);
                if (seenNAMultiDim(pos, value, false)) {
                    continue;
                }
                int dstIndex = dstArrayBase + newAccDstDimensions * i;
                int srcIndex = getSrcIndex(srcArrayBase, pos, newAccSrcDimensions);
                vector.updateDataAt(srcIndex, value.getDataAt(dstIndex % value.getLength()), elementNACheck);
            }
        } else {
            for (int i = 0; i < p.getLength(); i++) {
                int pos = p.getDataAt(i);
                if (seenNAMultiDim(pos, value, false)) {
                    continue;
                }
                int newDstArrayBase = dstArrayBase + newAccDstDimensions * i;
                int newSrcArrayBase = getNewArrayBase(srcArrayBase, pos, newAccSrcDimensions);
                setData(value, vector, positions, currentDimLevel - 1, newSrcArrayBase, newDstArrayBase, newAccSrcDimensions, newAccDstDimensions);
            }
        }
    }

    private RLogicalVector updateVector(RAbstractLogicalVector value, RLogicalVector vector, Object[] positions) {
        int replacementLength = getReplacementLength(positions, value, false);
        RLogicalVector resultVector = vector;
        if (replacementLength == 0) {
            return resultVector;
        }
        if (vector.isShared()) {
            resultVector = (RLogicalVector) vector.copy();
        }
        int[] srcDimensions = resultVector.getDimensions();
        int numSrcDimensions = srcDimensions.length;
        int srcDimSize = srcDimensions[numSrcDimensions - 1];
        int accSrcDimensions = resultVector.getLength() / srcDimSize;
        RIntVector p = (RIntVector) positions[positions.length - 1];
        int accDstDimensions = replacementLength / p.getLength();
        elementNACheck.enable(value);
        posNACheck.enable(p);
        for (int i = 0; i < p.getLength(); i++) {
            int dstArrayBase = accDstDimensions * i;
            int pos = p.getDataAt(i);
            if (seenNAMultiDim(pos, value, false)) {
                continue;
            }
            int srcArrayBase = getSrcArrayBase(pos, accSrcDimensions);
            setData(value, resultVector, positions, numSrcDimensions - 1, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions);
        }
        return resultVector;
    }

    private static RLogicalVector getResultVector(RLogicalVector vector, int highestPos) {
        RLogicalVector resultVector = vector;
        if (vector.isShared()) {
            resultVector = (RLogicalVector) vector.copy();
        }
        if (resultVector.getLength() < highestPos) {
            resultVector.resizeWithNames(highestPos);
        }
        return resultVector;
    }

    private RLogicalVector updateSingleDim(RAbstractLogicalVector value, RLogicalVector resultVector, int position) {
        elementNACheck.enable(value);
        resultVector.updateDataAt(position - 1, value.getDataAt(0), elementNACheck);
        return resultVector;
    }

    private RLogicalVector updateSingleDimVector(RAbstractLogicalVector value, int orgVectorLength, RLogicalVector resultVector, RIntVector positions) {
        for (int i = 0; i < positions.getLength(); i++) {
            int p = positions.getDataAt(i);
            if (seenNA(p, value)) {
                continue;
            }
            if (p < 0) {
                int pos = -(p + 1);
                if (pos >= orgVectorLength) {
                    resultVector.updateDataAt(pos, RRuntime.LOGICAL_NA, elementNACheck);
                }
            } else {
                resultVector.updateDataAt(p - 1, value.getDataAt(i % value.getLength()), elementNACheck);
            }
        }
        if (positions.getLength() % value.getLength() != 0) {
            RContext.getInstance().setEvalWarning(RError.NOT_MULTIPLE_REPLACEMENT);
        }
        updateNames(resultVector, positions);
        return resultVector;
    }

    @Specialization(order = 402, guards = {"multiDim", "!wrongDimensionsMatrix", "!wrongDimensions"})
    RLogicalVector update(Object v, RAbstractLogicalVector value, RLogicalVector vector, int recLevel, Object[] positions) {
        return updateVector(value, vector, positions);
    }

    @Specialization(order = 440, guards = {"isSubset", "!posNames", "multiPos"})
    RAbstractLogicalVector updateSubset(Object v, RAbstractLogicalVector value, RLogicalVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 441, guards = {"isSubset", "!posNames", "onePosition"})
    Object updateSubsetOne(VirtualFrame frame, Object v, RAbstractLogicalVector value, RLogicalVector vector, int recLevel, RIntVector positions) {
        return updateRecursive(frame, v, value, vector, recLevel, positions.getDataAt(0));
    }

    @Specialization(order = 442, guards = {"isSubset", "posNames"})
    RAbstractLogicalVector updateSubsetNames(Object v, RAbstractLogicalVector value, RLogicalVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 443, guards = {"!isSubset", "posNames"})
    RAbstractLogicalVector update(Object v, RAbstractLogicalVector value, RLogicalVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 444, guards = {"!isValueLengthOne", "isSubset", "!isPosNA", "!isPosZero"})
    RLogicalVector updateTooManyValuesSubset(Object v, RAbstractLogicalVector value, RLogicalVector vector, int recLevel, int position) {
        RContext.getInstance().setEvalWarning(RError.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    @Specialization(order = 445, guards = {"isValueLengthOne", "!isPosNA", "!isPosZero"})
    RLogicalVector update(Object v, RAbstractLogicalVector value, RLogicalVector vector, int recLevel, int position) {
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    // string vector

    private void setData(RAbstractStringVector value, RStringVector vector, Object[] positions, int currentDimLevel, int srcArrayBase, int dstArrayBase, int accSrcDimensions, int accDstDimensions) {
        int[] srcDimensions = vector.getDimensions();
        RIntVector p = (RIntVector) positions[currentDimLevel - 1];
        int srcDimSize = srcDimensions[currentDimLevel - 1];
        int newAccSrcDimensions = accSrcDimensions / srcDimSize;
        int newAccDstDimensions = accDstDimensions / p.getLength();
        posNACheck.enable(p);
        if (currentDimLevel == 1) {
            for (int i = 0; i < p.getLength(); i++) {
                int pos = p.getDataAt(i);
                if (seenNAMultiDim(pos, value, false)) {
                    continue;
                }
                int dstIndex = dstArrayBase + newAccDstDimensions * i;
                int srcIndex = getSrcIndex(srcArrayBase, pos, newAccSrcDimensions);
                vector.updateDataAt(srcIndex, value.getDataAt(dstIndex % value.getLength()), elementNACheck);
            }
        } else {
            for (int i = 0; i < p.getLength(); i++) {
                int pos = p.getDataAt(i);
                if (seenNAMultiDim(pos, value, false)) {
                    continue;
                }
                int newDstArrayBase = dstArrayBase + newAccDstDimensions * i;
                int newSrcArrayBase = getNewArrayBase(srcArrayBase, pos, newAccSrcDimensions);
                setData(value, vector, positions, currentDimLevel - 1, newSrcArrayBase, newDstArrayBase, newAccSrcDimensions, newAccDstDimensions);
            }
        }
    }

    private RStringVector updateVector(RAbstractStringVector value, RStringVector vector, Object[] positions) {
        int replacementLength = getReplacementLength(positions, value, false);
        RStringVector resultVector = vector;
        if (replacementLength == 0) {
            return resultVector;
        }
        if (vector.isShared()) {
            resultVector = (RStringVector) vector.copy();
        }
        int[] srcDimensions = resultVector.getDimensions();
        int numSrcDimensions = srcDimensions.length;
        int srcDimSize = srcDimensions[numSrcDimensions - 1];
        int accSrcDimensions = resultVector.getLength() / srcDimSize;
        RIntVector p = (RIntVector) positions[positions.length - 1];
        int accDstDimensions = replacementLength / p.getLength();
        elementNACheck.enable(value);
        posNACheck.enable(p);
        for (int i = 0; i < p.getLength(); i++) {
            int dstArrayBase = accDstDimensions * i;
            int pos = p.getDataAt(i);
            if (seenNAMultiDim(pos, value, false)) {
                continue;
            }
            int srcArrayBase = getSrcArrayBase(pos, accSrcDimensions);
            setData(value, resultVector, positions, numSrcDimensions - 1, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions);
        }
        return resultVector;
    }

    private static RStringVector getResultVector(RStringVector vector, int highestPos) {
        RStringVector resultVector = vector;
        if (vector.isShared()) {
            resultVector = (RStringVector) vector.copy();
        }
        if (resultVector.getLength() < highestPos) {
            resultVector.resizeWithNames(highestPos);
        }
        return resultVector;
    }

    private RStringVector updateSingleDim(RAbstractStringVector value, RStringVector resultVector, int position) {
        elementNACheck.enable(value);
        resultVector.updateDataAt(position - 1, value.getDataAt(0), elementNACheck);
        return resultVector;
    }

    private RStringVector updateSingleDimVector(RAbstractStringVector value, int orgVectorLength, RStringVector resultVector, RIntVector positions) {
        for (int i = 0; i < positions.getLength(); i++) {
            int p = positions.getDataAt(i);
            if (seenNA(p, value)) {
                continue;
            }
            if (p < 0) {
                int pos = -(p + 1);
                if (pos >= orgVectorLength) {
                    resultVector.updateDataAt(pos, RRuntime.STRING_NA, elementNACheck);
                }
            } else {
                resultVector.updateDataAt(p - 1, value.getDataAt(i % value.getLength()), elementNACheck);
            }
        }
        if (positions.getLength() % value.getLength() != 0) {
            RContext.getInstance().setEvalWarning(RError.NOT_MULTIPLE_REPLACEMENT);
        }
        updateNames(resultVector, positions);
        return resultVector;
    }

    @Specialization(order = 503, guards = {"multiDim", "!wrongDimensionsMatrix", "!wrongDimensions"})
    RStringVector update(Object v, RAbstractStringVector value, RStringVector vector, int recLevel, Object[] positions) {
        return updateVector(value, vector, positions);
    }

    @Specialization(order = 507, guards = {"multiDim", "!wrongDimensionsMatrix", "!wrongDimensions"})
    RStringVector update(VirtualFrame frame, Object v, RAbstractVector value, RStringVector vector, int recLevel, Object[] positions) {
        return updateVector((RStringVector) castString(frame, value), vector, positions);
    }

    @Specialization(order = 550, guards = {"isSubset", "!posNames", "multiPos"})
    RAbstractStringVector updateSubset(Object v, RAbstractStringVector value, RStringVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 551, guards = {"isSubset", "!posNames", "onePosition"})
    Object updateSubsetOne(VirtualFrame frame, Object v, RAbstractStringVector value, RStringVector vector, int recLevel, RIntVector positions) {
        return updateRecursive(frame, v, value, vector, recLevel, positions.getDataAt(0));
    }

    @Specialization(order = 552, guards = {"isSubset", "posNames"})
    RAbstractStringVector updateSubsetNames(Object v, RAbstractStringVector value, RStringVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 553, guards = {"!isSubset", "posNames"})
    RAbstractStringVector update(Object v, RAbstractStringVector value, RStringVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 554, guards = {"!isValueLengthOne", "isSubset", "!isPosNA", "!isPosZero"})
    RStringVector updateTooManyValuesSubset(Object v, RAbstractStringVector value, RStringVector vector, int recLevel, int position) {
        RContext.getInstance().setEvalWarning(RError.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    @Specialization(order = 555, guards = {"isValueLengthOne", "!isPosNA", "!isPosZero"})
    RStringVector update(Object v, RAbstractStringVector value, RStringVector vector, int recLevel, int position) {
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    @Specialization(order = 590, guards = {"isSubset", "!posNames", "multiPos"})
    RAbstractStringVector updateSubset(VirtualFrame frame, Object v, RAbstractVector value, RStringVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector((RStringVector) castString(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 591, guards = {"isSubset", "!posNames", "onePosition"})
    Object updateSubsetOne(VirtualFrame frame, Object v, RAbstractVector value, RStringVector vector, int recLevel, RIntVector positions) {
        return updateRecursive(frame, v, value, vector, recLevel, positions.getDataAt(0));
    }

    @Specialization(order = 592, guards = {"isSubset", "posNames"})
    RAbstractStringVector updateSubsetNames(VirtualFrame frame, Object v, RAbstractVector value, RStringVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector((RStringVector) castString(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 593, guards = {"!isSubset", "posNames"})
    RAbstractStringVector update(VirtualFrame frame, Object v, RAbstractVector value, RStringVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector((RStringVector) castString(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 594, guards = {"!isValueLengthOne", "isSubset", "!isPosNA", "!isPosZero"})
    RStringVector updateTooManyValuesSubset(VirtualFrame frame, Object v, RAbstractVector value, RStringVector vector, int recLevel, int position) {
        RContext.getInstance().setEvalWarning(RError.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim((RStringVector) castString(frame, value), getResultVector(vector, position), position);
    }

    @Specialization(order = 595, guards = {"isValueLengthOne", "!isPosNA", "!isPosZero"})
    RStringVector update(VirtualFrame frame, Object v, RAbstractVector value, RStringVector vector, int recLevel, int position) {
        return updateSingleDim((RStringVector) castString(frame, value), getResultVector(vector, position), position);
    }

    // complex vector

    private void setData(RAbstractComplexVector value, RComplexVector vector, Object[] positions, int currentDimLevel, int srcArrayBase, int dstArrayBase, int accSrcDimensions, int accDstDimensions) {
        int[] srcDimensions = vector.getDimensions();
        RIntVector p = (RIntVector) positions[currentDimLevel - 1];
        int srcDimSize = srcDimensions[currentDimLevel - 1];
        int newAccSrcDimensions = accSrcDimensions / srcDimSize;
        int newAccDstDimensions = accDstDimensions / p.getLength();
        posNACheck.enable(p);
        if (currentDimLevel == 1) {
            for (int i = 0; i < p.getLength(); i++) {
                int pos = p.getDataAt(i);
                if (seenNAMultiDim(pos, value, false)) {
                    continue;
                }
                int dstIndex = dstArrayBase + newAccDstDimensions * i;
                int srcIndex = getSrcIndex(srcArrayBase, pos, newAccSrcDimensions);
                vector.updateDataAt(srcIndex, value.getDataAt(dstIndex % value.getLength()), elementNACheck);
            }
        } else {
            for (int i = 0; i < p.getLength(); i++) {
                int pos = p.getDataAt(i);
                if (seenNAMultiDim(pos, value, false)) {
                    continue;
                }
                int newDstArrayBase = dstArrayBase + newAccDstDimensions * i;
                int newSrcArrayBase = getNewArrayBase(srcArrayBase, pos, newAccSrcDimensions);
                setData(value, vector, positions, currentDimLevel - 1, newSrcArrayBase, newDstArrayBase, newAccSrcDimensions, newAccDstDimensions);
            }
        }
    }

    private RComplexVector updateVector(RAbstractComplexVector value, RComplexVector vector, Object[] positions) {
        int replacementLength = getReplacementLength(positions, value, false);
        RComplexVector resultVector = vector;
        if (replacementLength == 0) {
            return resultVector;
        }
        if (vector.isShared()) {
            resultVector = (RComplexVector) vector.copy();
        }
        int[] srcDimensions = resultVector.getDimensions();
        int numSrcDimensions = srcDimensions.length;
        int srcDimSize = srcDimensions[numSrcDimensions - 1];
        int accSrcDimensions = resultVector.getLength() / srcDimSize;
        RIntVector p = (RIntVector) positions[positions.length - 1];
        int accDstDimensions = replacementLength / p.getLength();
        elementNACheck.enable(value);
        posNACheck.enable(p);
        for (int i = 0; i < p.getLength(); i++) {
            int dstArrayBase = accDstDimensions * i;
            int pos = p.getDataAt(i);
            if (seenNAMultiDim(pos, value, false)) {
                continue;
            }
            int srcArrayBase = getSrcArrayBase(pos, accSrcDimensions);
            setData(value, resultVector, positions, numSrcDimensions - 1, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions);
        }
        return resultVector;
    }

    private static RComplexVector getResultVector(RComplexVector vector, int highestPos) {
        RComplexVector resultVector = vector;
        if (vector.isShared()) {
            resultVector = (RComplexVector) vector.copy();
        }
        if (resultVector.getLength() < highestPos) {
            resultVector.resizeWithNames(highestPos);
        }
        return resultVector;
    }

    private RComplexVector updateSingleDim(RAbstractComplexVector value, RComplexVector resultVector, int position) {
        elementNACheck.enable(value);
        resultVector.updateDataAt(position - 1, value.getDataAt(0), elementNACheck);
        return resultVector;
    }

    private RComplexVector updateSingleDimVector(RAbstractComplexVector value, int orgVectorLength, RComplexVector resultVector, RIntVector positions) {
        for (int i = 0; i < positions.getLength(); i++) {
            int p = positions.getDataAt(i);
            if (seenNA(p, value)) {
                continue;
            }
            if (p < 0) {
                int pos = -(p + 1);
                if (pos >= orgVectorLength) {
                    resultVector.updateDataAt(pos, RRuntime.createComplexNA(), elementNACheck);
                }
            } else {
                resultVector.updateDataAt(p - 1, value.getDataAt(i % value.getLength()), elementNACheck);
            }
        }
        if (positions.getLength() % value.getLength() != 0) {
            RContext.getInstance().setEvalWarning(RError.NOT_MULTIPLE_REPLACEMENT);
        }
        updateNames(resultVector, positions);
        return resultVector;
    }

    @Specialization(order = 600, guards = {"multiDim", "!wrongDimensionsMatrix", "!wrongDimensions"})
    RComplexVector update(VirtualFrame frame, Object v, RAbstractIntVector value, RComplexVector vector, int recLevel, Object[] positions) {
        return updateVector((RComplexVector) castComplex(frame, value), vector, positions);
    }

    @Specialization(order = 601, guards = {"multiDim", "!wrongDimensionsMatrix", "!wrongDimensions"})
    RComplexVector update(VirtualFrame frame, Object v, RAbstractDoubleVector value, RComplexVector vector, int recLevel, Object[] positions) {
        return updateVector((RComplexVector) castComplex(frame, value), vector, positions);
    }

    @Specialization(order = 603, guards = {"multiDim", "!wrongDimensionsMatrix", "!wrongDimensions"})
    RComplexVector update(Object v, RAbstractComplexVector value, RComplexVector vector, int recLevel, Object[] positions) {
        return updateVector(value, vector, positions);
    }

    @Specialization(order = 620, guards = {"isSubset", "!posNames", "multiPos"})
    RAbstractComplexVector updateSubset(VirtualFrame frame, Object v, RAbstractIntVector value, RComplexVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector((RComplexVector) castComplex(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 621, guards = {"isSubset", "!posNames", "onePosition"})
    Object updateSubsetOne(VirtualFrame frame, Object v, RAbstractIntVector value, RComplexVector vector, int recLevel, RIntVector positions) {
        return updateRecursive(frame, v, value, vector, recLevel, positions.getDataAt(0));
    }

    @Specialization(order = 622, guards = {"isSubset", "posNames"})
    RAbstractComplexVector updateSubsetNames(VirtualFrame frame, Object v, RAbstractIntVector value, RComplexVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector((RComplexVector) castComplex(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 623, guards = {"!isSubset", "posNames"})
    RAbstractComplexVector update(VirtualFrame frame, Object v, RAbstractIntVector value, RComplexVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector((RComplexVector) castComplex(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 624, guards = {"!isValueLengthOne", "isSubset", "!isPosNA", "!isPosZero"})
    RComplexVector updateTooManyValuesSubset(VirtualFrame frame, Object v, RAbstractIntVector value, RComplexVector vector, int recLevel, int position) {
        RContext.getInstance().setEvalWarning(RError.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim((RComplexVector) castComplex(frame, value), getResultVector(vector, position), position);
    }

    @Specialization(order = 625, guards = {"isValueLengthOne", "!isPosNA", "!isPosZero"})
    RComplexVector update(VirtualFrame frame, Object v, RAbstractIntVector value, RComplexVector vector, int recLevel, int position) {
        return updateSingleDim((RComplexVector) castComplex(frame, value), getResultVector(vector, position), position);
    }

    @Specialization(order = 630, guards = {"isSubset", "!posNames", "multiPos"})
    RAbstractComplexVector updateSubset(VirtualFrame frame, Object v, RAbstractDoubleVector value, RComplexVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector((RComplexVector) castComplex(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 631, guards = {"isSubset", "!posNames", "onePosition"})
    Object updateSubsetOne(VirtualFrame frame, Object v, RAbstractDoubleVector value, RComplexVector vector, int recLevel, RIntVector positions) {
        return updateRecursive(frame, v, value, vector, recLevel, positions.getDataAt(0));
    }

    @Specialization(order = 632, guards = {"isSubset", "posNames"})
    RAbstractComplexVector updateSubsetNames(VirtualFrame frame, Object v, RAbstractDoubleVector value, RComplexVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector((RComplexVector) castComplex(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 633, guards = {"!isSubset", "posNames"})
    RAbstractComplexVector update(VirtualFrame frame, Object v, RAbstractDoubleVector value, RComplexVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector((RComplexVector) castComplex(frame, value), vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 634, guards = {"!isValueLengthOne", "isSubset", "!isPosNA", "!isPosZero"})
    RComplexVector updateTooManyValuesSubset(VirtualFrame frame, Object v, RAbstractDoubleVector value, RComplexVector vector, int recLevel, int position) {
        RContext.getInstance().setEvalWarning(RError.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim((RComplexVector) castComplex(frame, value), getResultVector(vector, position), position);
    }

    @Specialization(order = 635, guards = {"isValueLengthOne", "!isPosNA", "!isPosZero"})
    RComplexVector update(VirtualFrame frame, Object v, RAbstractDoubleVector value, RComplexVector vector, int recLevel, int position) {
        return updateSingleDim((RComplexVector) castComplex(frame, value), getResultVector(vector, position), position);
    }

    @Specialization(order = 650, guards = {"isSubset", "!posNames", "multiPos"})
    RAbstractComplexVector updateSubset(Object v, RAbstractComplexVector value, RComplexVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 651, guards = {"isSubset", "!posNames", "onePosition"})
    Object updateSubsetOne(VirtualFrame frame, Object v, RAbstractComplexVector value, RComplexVector vector, int recLevel, RIntVector positions) {
        return updateRecursive(frame, v, value, vector, recLevel, positions.getDataAt(0));
    }

    @Specialization(order = 652, guards = {"isSubset", "posNames"})
    RAbstractComplexVector updateSubsetNames(Object v, RAbstractComplexVector value, RComplexVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 653, guards = {"!isSubset", "posNames"})
    RAbstractComplexVector update(Object v, RAbstractComplexVector value, RComplexVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 654, guards = {"!isValueLengthOne", "isSubset", "!isPosNA", "!isPosZero"})
    RComplexVector updateTooManyValuesSubset(Object v, RAbstractComplexVector value, RComplexVector vector, int recLevel, int position) {
        RContext.getInstance().setEvalWarning(RError.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    @Specialization(order = 655, guards = {"isValueLengthOne", "!isPosNA", "!isPosZero"})
    RComplexVector update(Object v, RAbstractComplexVector value, RComplexVector vector, int recLevel, int position) {
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    // raw vector

    private void setData(RAbstractRawVector value, RRawVector vector, Object[] positions, int currentDimLevel, int srcArrayBase, int dstArrayBase, int accSrcDimensions, int accDstDimensions) {
        int[] srcDimensions = vector.getDimensions();
        RIntVector p = (RIntVector) positions[currentDimLevel - 1];
        int srcDimSize = srcDimensions[currentDimLevel - 1];
        int newAccSrcDimensions = accSrcDimensions / srcDimSize;
        int newAccDstDimensions = accDstDimensions / p.getLength();
        posNACheck.enable(p);
        if (currentDimLevel == 1) {
            for (int i = 0; i < p.getLength(); i++) {
                int pos = p.getDataAt(i);
                if (seenNAMultiDim(pos, value, false)) {
                    continue;
                }
                int dstIndex = dstArrayBase + newAccDstDimensions * i;
                int srcIndex = getSrcIndex(srcArrayBase, pos, newAccSrcDimensions);
                vector.updateDataAt(srcIndex, value.getDataAt(dstIndex % value.getLength()));
            }
        } else {
            for (int i = 0; i < p.getLength(); i++) {
                int pos = p.getDataAt(i);
                if (seenNAMultiDim(pos, value, false)) {
                    continue;
                }
                int newDstArrayBase = dstArrayBase + newAccDstDimensions * i;
                int newSrcArrayBase = getNewArrayBase(srcArrayBase, pos, newAccSrcDimensions);
                setData(value, vector, positions, currentDimLevel - 1, newSrcArrayBase, newDstArrayBase, newAccSrcDimensions, newAccDstDimensions);
            }
        }
    }

    private RRawVector updateVector(RAbstractRawVector value, RRawVector vector, Object[] positions) {
        int replacementLength = getReplacementLength(positions, value, false);
        RRawVector resultVector = vector;
        if (replacementLength == 0) {
            return resultVector;
        }
        if (vector.isShared()) {
            resultVector = (RRawVector) vector.copy();
        }
        int[] srcDimensions = resultVector.getDimensions();
        int numSrcDimensions = srcDimensions.length;
        int srcDimSize = srcDimensions[numSrcDimensions - 1];
        int accSrcDimensions = resultVector.getLength() / srcDimSize;
        RIntVector p = (RIntVector) positions[positions.length - 1];
        int accDstDimensions = replacementLength / p.getLength();
        posNACheck.enable(p);
        for (int i = 0; i < p.getLength(); i++) {
            int dstArrayBase = accDstDimensions * i;
            int pos = p.getDataAt(i);
            if (seenNAMultiDim(pos, value, false)) {
                continue;
            }
            int srcArrayBase = getSrcArrayBase(pos, accSrcDimensions);
            setData(value, resultVector, positions, numSrcDimensions - 1, srcArrayBase, dstArrayBase, accSrcDimensions, accDstDimensions);
        }
        return resultVector;
    }

    private static RRawVector getResultVector(RRawVector vector, int highestPos) {
        RRawVector resultVector = vector;
        if (vector.isShared()) {
            resultVector = (RRawVector) vector.copy();
        }
        if (resultVector.getLength() < highestPos) {
            resultVector.resizeWithNames(highestPos);
        }
        return resultVector;
    }

    private static RRawVector updateSingleDim(RAbstractRawVector value, RRawVector resultVector, int position) {
        resultVector.updateDataAt(position - 1, value.getDataAt(0));
        return resultVector;
    }

    private RRawVector updateSingleDimVector(RAbstractRawVector value, int orgVectorLength, RRawVector resultVector, RIntVector positions) {
        for (int i = 0; i < positions.getLength(); i++) {
            int p = positions.getDataAt(i);
            if (seenNA(p, value)) {
                continue;
            }
            if (p < 0) {
                int pos = -(p + 1);
                if (pos >= orgVectorLength) {
                    resultVector.updateDataAt(pos, RDataFactory.createRaw((byte) 0));
                }
            } else {
                resultVector.updateDataAt(p - 1, value.getDataAt(i % value.getLength()));
            }
        }
        if (positions.getLength() % value.getLength() != 0) {
            RContext.getInstance().setEvalWarning(RError.NOT_MULTIPLE_REPLACEMENT);
        }
        updateNames(resultVector, positions);
        return resultVector;
    }

    @Specialization(order = 706, guards = {"multiDim", "!wrongDimensionsMatrix", "!wrongDimensions"})
    RRawVector update(Object v, RAbstractRawVector value, RRawVector vector, int recLevel, Object[] positions) {
        return updateVector(value, vector, positions);
    }

    @Specialization(order = 780, guards = {"isSubset", "!posNames", "multiPos"})
    RAbstractRawVector updateSubset(Object v, RAbstractRawVector value, RRawVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 781, guards = {"isSubset", "!posNames", "onePosition"})
    Object updateSubsetOne(VirtualFrame frame, Object v, RAbstractRawVector value, RRawVector vector, int recLevel, RIntVector positions) {
        return updateRecursive(frame, v, value, vector, recLevel, positions.getDataAt(0));
    }

    @Specialization(order = 782, guards = {"isSubset", "posNames"})
    RAbstractRawVector updateSubsetNames(Object v, RAbstractRawVector value, RRawVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 783, guards = {"!isSubset", "posNames"})
    RAbstractRawVector update(Object v, RAbstractRawVector value, RRawVector vector, int recLevel, RIntVector positions) {
        return updateSingleDimVector(value, vector.getLength(), getResultVector(vector, getHighestPos(positions)), positions);
    }

    @Specialization(order = 784, guards = {"!isValueLengthOne", "isSubset", "!isPosNA", "!isPosZero"})
    RRawVector updateTooManyValuesSubset(Object v, RAbstractRawVector value, RRawVector vector, int recLevel, int position) {
        RContext.getInstance().setEvalWarning(RError.NOT_MULTIPLE_REPLACEMENT);
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    @Specialization(order = 785, guards = {"isValueLengthOne", "!isPosNA", "!isPosZero"})
    RRawVector update(Object v, RAbstractRawVector value, RRawVector vector, int recLevel, int position) {
        return updateSingleDim(value, getResultVector(vector, position), position);
    }

    @Specialization(order = 1000, guards = {"noPosition", "emptyValue"})
    Object accessListEmptyPosEmptyValueList(Object v, RAbstractVector value, RList vector, int recLevel, RList positions) {
        if (!isSubset) {
            throw RError.getSelectLessThanOne(getEncapsulatingSourceSection());
        } else {
            throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
        }
    }

    @Specialization(order = 1001, guards = {"noPosition", "emptyValue", "!isVectorList"})
    Object accessListEmptyPosEmptyValue(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, RList positions) {
        if (!isSubset) {
            throw RError.getReplacementZero(getEncapsulatingSourceSection());
        } else {
            throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
        }
    }

    @Specialization(order = 1002, guards = {"noPosition", "valueLengthOne"})
    Object accessListEmptyPosValueLengthOne(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, RList positions) {
        if (!isSubset) {
            throw RError.getSelectLessThanOne(getEncapsulatingSourceSection());
        } else {
            throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
        }
    }

    @Specialization(order = 1003, guards = {"noPosition", "valueLongerThanOne"})
    Object accessListEmptyPosValueLongerThanOne(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, RList positions) {
        if (!isSubset) {
            throw RError.getMoreElementsSupplied(getEncapsulatingSourceSection());
        } else {
            throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
        }
    }

    @Specialization(order = 1004, guards = "noPosition")
    Object accessListEmptyPosValueNullList(Object v, RNull value, RList vector, int recLevel, RList positions) {
        if (!isSubset) {
            throw RError.getSelectLessThanOne(getEncapsulatingSourceSection());
        } else {
            throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
        }
    }

    @Specialization(order = 1005, guards = {"noPosition", "!isVectorList"})
    Object accessListEmptyPosValueNull(Object v, RNull value, RAbstractVector vector, int recLevel, RList positions) {
        if (!isSubset) {
            throw RError.getMoreElementsSupplied(getEncapsulatingSourceSection());
        } else {
            throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
        }
    }

    @Specialization(order = 1010, guards = {"onePosition", "emptyValue"})
    Object accessListOnePosEmptyValueList(Object v, RAbstractVector value, RList vector, int recLevel, RList positions) {
        throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
    }

    @Specialization(order = 1011, guards = {"onePosition", "emptyValue", "!isVectorList"})
    Object accessListOnePosEmptyValue(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, RList positions) {
        if (!isSubset) {
            throw RError.getReplacementZero(getEncapsulatingSourceSection());
        } else {
            throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
        }
    }

    @Specialization(order = 1012, guards = {"onePosition", "valueLengthOne"})
    Object accessListOnePosValueLengthOne(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, RList positions) {
        throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
    }

    @Specialization(order = 1013, guards = {"onePosition", "valueLongerThanOne"})
    Object accessListOnePosValueLongerThanTwo(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, RList positions) {
        if (!isSubset) {
            throw RError.getMoreElementsSupplied(getEncapsulatingSourceSection());
        } else {
            throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
        }
    }

    @Specialization(order = 1014, guards = "onePosition")
    Object accessListOnePosValueNullList(Object v, RNull value, RList vector, int recLevel, RList positions) {
        throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
    }

    @Specialization(order = 1015, guards = {"onePosition", "!isVectorList"})
    Object accessListOnePosValueNull(Object v, RNull value, RAbstractVector vector, int recLevel, RList positions) {
        if (!isSubset) {
            throw RError.getMoreElementsSupplied(getEncapsulatingSourceSection());
        } else {
            throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
        }
    }

    @Specialization(order = 1020, guards = "twoPositions")
    Object accessListTwoPos(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, RList positions) {
        throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
    }

    @Specialization(order = 1021, guards = "twoPositions")
    Object accessListTwoPosValueNull(Object v, RNull value, RAbstractVector vector, int recLevel, RList positions) {
        throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
    }

    @Specialization(order = 1030, guards = {"moreThanTwoPos", "emptyValue"})
    Object accessListMultiPosEmptyValueList(Object v, RAbstractVector value, RList vector, int recLevel, RList positions) {
        throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
    }

    @Specialization(order = 1031, guards = {"moreThanTwoPos", "emptyValue", "!isVectorList"})
    Object accessListMultiPosEmptyValue(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, RList positions) {
        if (!isSubset) {
            throw RError.getSelectMoreThanOne(getEncapsulatingSourceSection());
        } else {
            throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
        }
    }

    @Specialization(order = 1032, guards = {"moreThanTwoPos", "valueLengthOne"})
    Object accessListMultiPosValueLengthOneList(Object v, RAbstractVector value, RList vector, int recLevel, RList positions) {
        throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
    }

    @Specialization(order = 1033, guards = {"moreThanTwoPos", "valueLengthOne", "!isVectorList"})
    Object accessListMultiPosValueLengthOne(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, RList positions) {
        if (!isSubset) {
            throw RError.getSelectMoreThanOne(getEncapsulatingSourceSection());
        } else {
            throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
        }
    }

    @Specialization(order = 1034, guards = {"moreThanTwoPos", "valueLongerThanOne"})
    Object accessListMultiPos(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, RList positions) {
        if (!isSubset) {
            throw RError.getSelectMoreThanOne(getEncapsulatingSourceSection());
        } else {
            throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
        }
    }

    @Specialization(order = 1035, guards = "moreThanTwoPos")
    Object accessListMultiPosValueNullList(Object v, RNull value, RList vector, int recLevel, RList positions) {
        throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
    }

    @Specialization(order = 1036, guards = {"moreThanTwoPos", "!isVectorList"})
    Object accessListMultiPosValueNull(Object v, RNull value, RAbstractVector vector, int recLevel, RList positions) {
        if (!isSubset) {
            throw RError.getSelectMoreThanOne(getEncapsulatingSourceSection());
        } else {
            throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
        }
    }

    @Specialization(order = 1100, guards = {"emptyValue", "!isVectorList"})
    Object accessComplexEmptyValue(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, RComplex position) {
        if (!isSubset) {
            throw RError.getReplacementZero(getEncapsulatingSourceSection());
        } else {
            throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "complex");
        }
    }

    @Specialization(order = 1101, guards = {"valueLongerThanOne", "!isVectorList"})
    Object accessComplexValueLongerThanOne(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, RComplex position) {
        if (!isSubset) {
            throw RError.getMoreElementsSupplied(getEncapsulatingSourceSection());
        } else {
            throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "complex");
        }
    }

    @Specialization(order = 1102, guards = {"!valueLongerThanOne", "!emptyValue", "!isVectorList"})
    Object accessComplex(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, RComplex position) {
        throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "complex");
    }

    @Specialization(order = 1103)
    Object accessComplexList(Object v, RAbstractVector value, RList vector, int recLevel, RComplex position) {
        throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "complex");
    }

    @Specialization(order = 1104, guards = "!isVectorList")
    Object accessComplex(Object v, RNull value, RAbstractVector vector, int recLevel, RComplex position) {
        if (!isSubset) {
            throw RError.getMoreElementsSupplied(getEncapsulatingSourceSection());
        } else {
            throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "complex");
        }
    }

    @Specialization(order = 1105)
    Object accessComplexList(Object v, RNull value, RList vector, int recLevel, RComplex position) {
        throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "complex");
    }

    @Specialization(order = 1200, guards = {"emptyValue", "!isVectorList"})
    Object accessRawEmptyValue(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, RRaw position) {
        if (!isSubset) {
            throw RError.getReplacementZero(getEncapsulatingSourceSection());
        } else {
            throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "raw");
        }
    }

    @Specialization(order = 1201, guards = {"valueLongerThanOne", "!isVectorList"})
    Object accessRawValueLongerThanOne(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, RRaw position) {
        if (!isSubset) {
            throw RError.getMoreElementsSupplied(getEncapsulatingSourceSection());
        } else {
            throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "raw");
        }
    }

    @Specialization(order = 1202, guards = {"!valueLongerThanOne", "!emptyValue", "!isVectorList"})
    Object accessRaw(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, RRaw position) {
        throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "raw");
    }

    @Specialization(order = 1203)
    Object accessRawList(Object v, RAbstractVector value, RList vector, int recLevel, RRaw position) {
        throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "raw");
    }

    @Specialization(order = 1204, guards = "!isVectorList")
    Object accessRaw(Object v, RNull value, RAbstractVector vector, int recLevel, RRaw position) {
        if (!isSubset) {
            throw RError.getMoreElementsSupplied(getEncapsulatingSourceSection());
        } else {
            throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "raw");
        }
    }

    @Specialization(order = 1205)
    Object accessRawList(Object v, RNull value, RList vector, int recLevel, RRaw position) {
        throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "raw");
    }

    protected boolean firstPosZero(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, RIntVector positions) {
        return positions.getDataAt(0) == 0;
    }

    protected boolean firstPosZero(Object v, RNull value, RAbstractVector vector, int recLevel, RIntVector positions) {
        return positions.getDataAt(0) == 0;
    }

    protected boolean outOfBoundsNegative(Object v, RNull value, RAbstractVector vector, int recLevel, int position) {
        return -position > vector.getLength();
    }

    protected boolean outOfBoundsNegative(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, int position) {
        return -position > vector.getLength();
    }

    protected boolean oneElemVector(Object v, RNull value, RAbstractVector vector) {
        return vector.getLength() == 1;
    }

    protected boolean oneElemVector(Object v, RAbstractVector value, RAbstractVector vector) {
        return vector.getLength() == 1;
    }

    protected boolean posNames(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, RIntVector positions) {
        return positions.getNames() != RNull.instance;
    }

    protected boolean isPositionNegative(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, int position) {
        return position < 0;
    }

    protected boolean isPositionNegative(Object v, RNull value, RAbstractVector vector, int recLevel, int position) {
        return position < 0;
    }

    protected boolean isVectorList(Object v, RNull value, RAbstractVector vector) {
        return vector.getElementClass() == Object.class;
    }

    protected boolean isVectorList(Object v, RAbstractVector value, RAbstractVector vector) {
        return vector.getElementClass() == Object.class;
    }

    protected boolean isVectorLongerThanOne(Object v, RAbstractVector value, RAbstractVector vector) {
        return vector.getLength() > 1;
    }

    protected boolean isVectorLongerThanOne(Object v, RNull value, RAbstractVector vector) {
        return vector.getLength() > 1;
    }

    protected boolean emptyValue(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, Object[] positions) {
        return value.getLength() == 0;
    }

    protected boolean emptyValue(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, int position) {
        return value.getLength() == 0;
    }

    protected boolean emptyValue(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, Object position) {
        return value.getLength() == 0;
    }

    protected boolean valueLengthOne(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, Object positions) {
        return value.getLength() == 1;
    }

    protected boolean valueLongerThanOne(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, Object positions) {
        return value.getLength() > 1;
    }

    protected boolean wrongDimensionsMatrix(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, Object[] positions) {
        if (positions.length == 2 && (vector.getDimensions() == null || vector.getDimensions().length != positions.length)) {
            if (isSubset) {
                throw RError.getIncorrectSubscriptsMatrix(getEncapsulatingSourceSection());
            } else {
                throw RError.getImproperSubscript(getEncapsulatingSourceSection());
            }
        }
        return false;
    }

    protected boolean wrongDimensionsMatrix(Object v, RAbstractVector value, RNull vector, int recLevel, Object[] positions) {
        if (positions.length == 2) {
            if (isSubset) {
                throw RError.getIncorrectSubscriptsMatrix(getEncapsulatingSourceSection());
            } else {
                throw RError.getImproperSubscript(getEncapsulatingSourceSection());
            }
        }
        return false;
    }

    protected boolean wrongDimensionsMatrix(Object v, RNull value, RAbstractVector vector, int recLevel, Object[] positions) {
        if (positions.length == 2 && (vector.getDimensions() == null || vector.getDimensions().length != positions.length)) {
            if (isSubset) {
                throw RError.getIncorrectSubscriptsMatrix(getEncapsulatingSourceSection());
            } else {
                throw RError.getImproperSubscript(getEncapsulatingSourceSection());
            }
        }
        return false;
    }

    protected boolean wrongDimensions(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, Object[] positions) {
        if (!((vector.getDimensions() == null && positions.length == 1) || vector.getDimensions().length == positions.length)) {
            if (isSubset) {
                throw RError.getIncorrectSubscripts(getEncapsulatingSourceSection());
            } else {
                throw RError.getImproperSubscript(getEncapsulatingSourceSection());
            }
        }
        return false;
    }

    protected boolean wrongDimensions(Object v, RAbstractVector value, RNull vector, int recLevel, Object[] positions) {
        if (positions.length > 2) {
            if (isSubset) {
                throw RError.getIncorrectSubscripts(getEncapsulatingSourceSection());
            } else {
                throw RError.getImproperSubscript(getEncapsulatingSourceSection());
            }
        }
        return false;
    }

    protected boolean wrongDimensions(Object v, RNull value, RAbstractVector vector, int recLevel, Object[] positions) {
        if (!((vector.getDimensions() == null && positions.length == 1) || vector.getDimensions().length == positions.length)) {
            if (isSubset) {
                throw RError.getIncorrectSubscripts(getEncapsulatingSourceSection());
            } else {
                throw RError.getImproperSubscript(getEncapsulatingSourceSection());
            }
        }
        return false;
    }

    protected boolean multiDim(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, Object[] positions) {
        return vector.getDimensions() != null && vector.getDimensions().length > 1;
    }

    protected boolean wrongLength(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, RIntVector positions) {
        int valLength = value.getLength();
        int posLength = positions.getLength();
        return valLength > posLength || (posLength % valLength != 0);
    }

    protected boolean isPosNA(Object v, RAbstractVector value, RNull vector, int recLevel, int position) {
        return RRuntime.isNA(position);
    }

    protected boolean isPosNA(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, int position) {
        return RRuntime.isNA(position);
    }

    protected boolean isPosNA(Object v, RNull value, RAbstractVector vector, int recLevel, int position) {
        return RRuntime.isNA(position);
    }

    protected boolean isPosZero(Object v, RAbstractVector value, RNull vector, int recLevel, int position) {
        return position == 0;
    }

    protected boolean isPosZero(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, int position) {
        return position == 0;
    }

    protected boolean isPosZero(Object v, RNull value, RAbstractVector vector, int recLevel, int position) {
        return position == 0;
    }

    protected boolean isPosZero(Object v, RNull value, RList vector, int recLevel, int position) {
        return position == 0;
    }

    protected boolean isValueLengthOne(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, int position) {
        return value.getLength() == 1;
    }

    protected boolean twoPositions(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, RAbstractVector position) {
        return position.getLength() == 2;
    }

    protected boolean twoPositions(Object v, RNull value, RAbstractVector vector, int recLevel, RAbstractVector position) {
        return position.getLength() == 2;
    }

    protected boolean onePosition(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, RAbstractVector position) {
        return position.getLength() == 1;
    }

    protected boolean onePosition(Object v, RNull value, RAbstractVector vector, int recLevel, RAbstractVector position) {
        return position.getLength() == 1;
    }

    protected boolean noPosition(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, RAbstractVector position) {
        return position.getLength() == 0;
    }

    protected boolean noPosition(Object v, RNull value, RAbstractVector vector, int recLevel, RAbstractVector position) {
        return position.getLength() == 0;
    }

    protected boolean isSubset() {
        return isSubset;
    }

    protected boolean inRecursion(Object v, RAbstractVector value, RAbstractVector vector, int recLevel) {
        return recLevel > 0;
    }

    protected boolean inRecursion(Object v, RNull value, RAbstractVector vector, int recLevel) {
        return recLevel > 0;
    }

    protected boolean multiPos(Object v, RNull value, RAbstractVector vector, int recLevel, RIntVector positions) {
        return positions.getLength() > 1;
    }

    protected boolean multiPos(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, RIntVector positions) {
        return positions.getLength() > 1;
    }

    protected boolean moreThanTwoPos(Object v, RAbstractVector value, RAbstractVector vector, int recLevel, RList positions) {
        return positions.getLength() > 2;
    }

    protected boolean moreThanTwoPos(Object v, RNull value, RAbstractVector vector, int recLevel, RList positions) {
        return positions.getLength() > 2;
    }

    protected boolean emptyList(Object v, RNull value, RList vector, int recLevel, int positions) {
        return vector.getLength() == 0;
    }

    @NodeChildren({@NodeChild(value = "vector", type = RNode.class), @NodeChild(value = "newValue", type = RNode.class), @NodeChild(value = "operand", type = RNode.class)})
    public abstract static class MultiDimPosConverterValueNode extends RNode {

        public abstract RIntVector executeConvert(VirtualFrame frame, Object vector, Object value, Object p);

        private final boolean isSubset;

        protected MultiDimPosConverterValueNode(boolean isSubset) {
            this.isSubset = isSubset;
        }

        protected MultiDimPosConverterValueNode(MultiDimPosConverterValueNode other) {
            this.isSubset = other.isSubset;
        }

        @Specialization(order = 1, guards = {"!singleOpNegative", "!multiPos"})
        public RAbstractIntVector doIntVector(Object vector, RNull value, RAbstractIntVector operand) {
            return operand;
        }

        @Specialization(order = 2, guards = {"!singleOpNegative", "multiPos"})
        public RAbstractIntVector doIntVectorMultiPos(Object vector, RNull value, RAbstractIntVector operand) {
            if (isSubset) {
                return operand;
            } else {
                throw RError.getMoreElementsSupplied(getEncapsulatingSourceSection());
            }
        }

        @Specialization(order = 4, guards = {"!emptyValue", "!singleOpNegative", "!multiPos"})
        public RAbstractIntVector doIntVector(Object vector, RAbstractVector value, RAbstractIntVector operand) {
            return operand;
        }

        @Specialization(order = 5, guards = {"!emptyValue", "!singleOpNegative", "multiPos"})
        public RAbstractIntVector doIntVectorMultiPos(Object vector, RAbstractVector value, RAbstractIntVector operand) {
            if (isSubset) {
                return operand;
            } else {
                throw RError.getSelectMoreThanOne(getEncapsulatingSourceSection());
            }
        }

        @Specialization(order = 6, guards = {"!emptyValue", "singleOpNegative"})
        public RAbstractIntVector doIntVectorNegative(Object vector, RAbstractVector value, RAbstractIntVector operand) {
            throw RError.getSelectMoreThanOne(getEncapsulatingSourceSection());
        }

        @Specialization(order = 7, guards = "emptyValue")
        public RAbstractIntVector doIntVectorEmptyValue(Object vector, RAbstractVector value, RAbstractIntVector operand) {
            if (!isSubset) {
                throw RError.getReplacementZero(getEncapsulatingSourceSection());

            } else {
                return operand;
            }
        }

        @Specialization(order = 20, guards = {"emptyValue", "!isVectorList"})
        Object accessComplexEmptyValue(RAbstractVector vector, RAbstractVector value, RComplex position) {
            if (!isSubset) {
                throw RError.getReplacementZero(getEncapsulatingSourceSection());
            } else {
                throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "complex");
            }
        }

        @Specialization(order = 21, guards = {"valueLongerThanOne", "!isVectorList"})
        Object accessComplexValueLongerThanOne(RAbstractVector vector, RAbstractVector value, RComplex position) {
            if (!isSubset) {
                throw RError.getMoreElementsSupplied(getEncapsulatingSourceSection());
            } else {
                throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "complex");
            }
        }

        @Specialization(order = 22, guards = {"!valueLongerThanOne", "!emptyValue", "!isVectorList"})
        Object accessComplex(RAbstractVector vector, RAbstractVector value, RComplex position) {
            throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "complex");
        }

        @Specialization(order = 23)
        Object accessComplexList(RList vector, RAbstractVector value, RComplex position) {
            throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "complex");
        }

        @Specialization(order = 24)
        Object accessComplexList(RList vector, RNull value, RComplex position) {
            throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "complex");
        }

        @Specialization(order = 25, guards = "!isVectorList")
        Object accessComplex(RAbstractVector vector, RNull value, RComplex position) {
            if (!isSubset) {
                throw RError.getMoreElementsSupplied(getEncapsulatingSourceSection());
            } else {
                throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "complex");
            }
        }

        @Specialization(order = 30, guards = {"emptyValue", "!isVectorList"})
        Object accessRawEmptyValue(RAbstractVector vector, RAbstractVector value, RRaw position) {
            if (!isSubset) {
                throw RError.getReplacementZero(getEncapsulatingSourceSection());
            } else {
                throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "raw");
            }
        }

        @Specialization(order = 31, guards = {"valueLongerThanOne", "!isVectorList"})
        Object accessRawValueLongerThanOne(RAbstractVector vector, RAbstractVector value, RRaw position) {
            if (!isSubset) {
                throw RError.getMoreElementsSupplied(getEncapsulatingSourceSection());
            } else {
                throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "raw");
            }
        }

        @Specialization(order = 32, guards = {"!valueLongerThanOne", "!emptyValue", "!isVectorList"})
        Object accessRaw(RAbstractVector vector, RAbstractVector value, RRaw position) {
            throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "raw");
        }

        @Specialization(order = 33)
        Object accessRawList(RList vector, RAbstractVector value, RRaw position) {
            throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "raw");
        }

        @Specialization(order = 34)
        Object accessRawList(RList vector, RNull value, RRaw position) {
            throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "raw");
        }

        @Specialization(order = 35, guards = "!isVectorList")
        Object accessRaw(RAbstractVector vector, RNull value, RRaw position) {
            if (!isSubset) {
                throw RError.getMoreElementsSupplied(getEncapsulatingSourceSection());
            } else {
                throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "raw");
            }
        }

        @Specialization(order = 100, guards = {"noPosition", "emptyValue"})
        Object accessListEmptyPosEmptyValueList(RList vector, RAbstractVector value, RList positions) {
            if (!isSubset) {
                throw RError.getSelectLessThanOne(getEncapsulatingSourceSection());
            } else {
                throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
            }
        }

        @Specialization(order = 101, guards = {"noPosition", "emptyValue", "!isVectorList"})
        Object accessListEmptyPosEmptyValue(RAbstractVector vector, RAbstractVector value, RList positions) {
            if (!isSubset) {
                throw RError.getReplacementZero(getEncapsulatingSourceSection());
            } else {
                throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
            }
        }

        @Specialization(order = 102, guards = {"noPosition", "valueLengthOne"})
        Object accessListEmptyPosValueLengthOne(RAbstractVector vector, RAbstractVector value, RList positions) {
            if (!isSubset) {
                throw RError.getSelectLessThanOne(getEncapsulatingSourceSection());
            } else {
                throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
            }
        }

        @Specialization(order = 103, guards = {"noPosition", "valueLongerThanOne"})
        Object accessListEmptyPosValueLongerThanOneList(RList vector, RAbstractVector value, RList positions) {
            if (!isSubset) {
                throw RError.getSelectLessThanOne(getEncapsulatingSourceSection());
            } else {
                throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
            }
        }

        @Specialization(order = 104, guards = {"noPosition", "valueLongerThanOne", "!isVectorList"})
        Object accessListEmptyPosValueLongerThanOne(RAbstractVector vector, RAbstractVector value, RList positions) {
            if (!isSubset) {
                throw RError.getMoreElementsSupplied(getEncapsulatingSourceSection());
            } else {
                throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
            }
        }

        @Specialization(order = 105, guards = "noPosition")
        Object accessListEmptyPosEmptyValueList(RList vector, RNull value, RList positions) {
            if (!isSubset) {
                throw RError.getSelectLessThanOne(getEncapsulatingSourceSection());
            } else {
                throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
            }
        }

        @Specialization(order = 106, guards = {"noPosition", "!isVectorList"})
        Object accessListEmptyPosEmptyValue(RAbstractVector vector, RNull value, RList positions) {
            if (!isSubset) {
                throw RError.getMoreElementsSupplied(getEncapsulatingSourceSection());
            } else {
                throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
            }
        }

        @Specialization(order = 110, guards = {"onePosition", "emptyValue"})
        Object accessListOnePosEmptyValueList(RList vector, RAbstractVector value, RList positions) {
            throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
        }

        @Specialization(order = 111, guards = {"onePosition", "emptyValue", "!isVectorList"})
        Object accessListOnePosEmptyValue(RAbstractVector vector, RAbstractVector value, RList positions) {
            if (!isSubset) {
                throw RError.getReplacementZero(getEncapsulatingSourceSection());
            } else {
                throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
            }
        }

        @Specialization(order = 112, guards = {"onePosition", "valueLengthOne"})
        Object accessListOnePosValueLengthOne(RAbstractVector vector, RAbstractVector value, RList positions) {
            throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
        }

        @Specialization(order = 113, guards = {"onePosition", "valueLongerThanOne"})
        Object accessListOnePosValueLongerThanOneList(RList vector, RAbstractVector value, RList positions) {
            throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
        }

        @Specialization(order = 114, guards = {"onePosition", "valueLongerThanOne", "!isVectorList"})
        Object accessListOnePosValueLongerThanOne(RAbstractVector vector, RAbstractVector value, RList positions) {
            if (!isSubset) {
                throw RError.getMoreElementsSupplied(getEncapsulatingSourceSection());
            } else {
                throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
            }
        }

        @Specialization(order = 115, guards = "onePosition")
        Object accessListOnePosEmptyValueList(RList vector, RNull value, RList positions) {
            throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
        }

        @Specialization(order = 116, guards = {"onePosition", "!isVectorList"})
        Object accessListOnePosValueLongerThanOne(RAbstractVector vector, RNull value, RList positions) {
            if (!isSubset) {
                throw RError.getMoreElementsSupplied(getEncapsulatingSourceSection());
            } else {
                throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
            }
        }

        @Specialization(order = 120, guards = "multiPos")
        Object accessListTwoPosEmptyValueList(RList vector, RAbstractVector value, RList positions) {
            if (!isSubset) {
                throw RError.getSelectMoreThanOne(getEncapsulatingSourceSection());
            } else {
                throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
            }
        }

        @Specialization(order = 121, guards = {"multiPos", "emptyValue", "!isVectorList"})
        Object accessListTwoPosEmptyValue(RAbstractVector vector, RAbstractVector value, RList positions) {
            if (!isSubset) {
                throw RError.getReplacementZero(getEncapsulatingSourceSection());
            } else {
                throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
            }
        }

        @Specialization(order = 122, guards = {"multiPos", "valueLengthOne", "!isVectorList"})
        Object accessListTwoPosValueLengthOne(RAbstractVector vector, RAbstractVector value, RList positions) {
            if (!isSubset) {
                throw RError.getSelectMoreThanOne(getEncapsulatingSourceSection());
            } else {
                throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
            }
        }

        @Specialization(order = 123, guards = {"multiPos", "valueLongerThanOne", "!isVectorList"})
        Object accessListTwoPosValueLongerThanOne(RAbstractVector vector, RAbstractVector value, RList positions) {
            if (!isSubset) {
                throw RError.getMoreElementsSupplied(getEncapsulatingSourceSection());
            } else {
                throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
            }
        }

        @Specialization(order = 124, guards = "multiPos")
        Object accessListTwoPosEmptyValueList(RList vector, RNull value, RList positions) {
            if (!isSubset) {
                throw RError.getSelectMoreThanOne(getEncapsulatingSourceSection());
            } else {
                throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
            }
        }

        @Specialization(order = 125, guards = {"multiPos", "!isVectorList"})
        Object accessListTwoPosValueLongerThanOne(RAbstractVector vector, RNull value, RList positions) {
            if (!isSubset) {
                throw RError.getMoreElementsSupplied(getEncapsulatingSourceSection());
            } else {
                throw RError.getInvalidSubscriptType(getEncapsulatingSourceSection(), "list");
            }
        }

        protected boolean singleOpNegative(Object vector, RNull value, RAbstractIntVector p) {
            return p.getLength() == 1 && p.getDataAt(0) < 0 && !RRuntime.isNA(p.getDataAt(0));
        }

        protected boolean singleOpNegative(Object vector, RAbstractVector value, RAbstractIntVector p) {
            return p.getLength() == 1 && p.getDataAt(0) < 0 && !RRuntime.isNA(p.getDataAt(0));
        }

        protected boolean isVectorList(RAbstractVector vector) {
            return vector.getElementClass() == Object.class;
        }

        // Truffle DSL bug (?) - guards should work with just RAbstractVector as the vector
        // parameter
        protected boolean onePosition(RList vector, RAbstractVector value, RList p) {
            return p.getLength() == 1;
        }

        protected boolean onePosition(RList vector, RNull value, RList p) {
            return p.getLength() == 1;
        }

        protected boolean noPosition(RList vector, RAbstractVector value, RList p) {
            return p.getLength() == 0;
        }

        protected boolean noPosition(RList vector, RNull value, RList p) {
            return p.getLength() == 0;
        }

        protected boolean multiPos(RList vector, RAbstractVector value, RList p) {
            return p.getLength() > 1;
        }

        protected boolean multiPos(RList vector, RNull value, RList p) {
            return p.getLength() > 1;
        }

        protected boolean onePosition(RAbstractVector vector, RAbstractVector value, RList p) {
            return p.getLength() == 1;
        }

        protected boolean onePosition(RAbstractVector vector, RNull value, RList p) {
            return p.getLength() == 1;
        }

        protected boolean noPosition(RAbstractVector vector, RAbstractVector value, RList p) {
            return p.getLength() == 0;
        }

        protected boolean noPosition(RAbstractVector vector, RNull value, RList p) {
            return p.getLength() == 0;
        }

        protected boolean multiPos(RAbstractVector vector, RAbstractVector value, RList p) {
            return p.getLength() > 1;
        }

        protected boolean multiPos(RAbstractVector vector, RNull value, RList p) {
            return p.getLength() > 1;
        }

        protected boolean multiPos(Object vector, RAbstractVector value, RAbstractIntVector p) {
            return p.getLength() > 1;
        }

        protected boolean multiPos(Object vector, RNull value, RAbstractIntVector p) {
            return p.getLength() > 1;
        }

        protected boolean emptyValue(Object vector, RAbstractVector value) {
            return value.getLength() == 0;
        }

        protected boolean emptyValue(RAbstractVector vector, RAbstractVector value) {
            return value.getLength() == 0;
        }

        protected boolean valueLengthOne(RAbstractVector vector, RAbstractVector value) {
            return value.getLength() == 1;
        }

        protected boolean valueLongerThanOne(RAbstractVector vector, RAbstractVector value) {
            return value.getLength() > 1;
        }

    }

    @NodeChildren({@NodeChild(value = "newValue", type = RNode.class), @NodeChild(value = "operand", type = RNode.class)})
    public abstract static class CoerceOperand extends RNode {

        public abstract Object executeEvaluated(VirtualFrame frame, Object value, Object vector);

        @Child private CastComplexNode castComplex;
        @Child private CastDoubleNode castDouble;
        @Child private CastIntegerNode castInteger;
        @Child private CastStringNode castString;
        @Child private CastListNode castList;

        private Object castComplex(VirtualFrame frame, Object operand) {
            if (castComplex == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castComplex = insert(CastComplexNodeFactory.create(null, true, true));
            }
            return castComplex.executeCast(frame, operand);
        }

        private Object castDouble(VirtualFrame frame, Object operand) {
            if (castDouble == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castDouble = insert(CastDoubleNodeFactory.create(null, true, true));
            }
            return castDouble.executeCast(frame, operand);
        }

        private Object castInteger(VirtualFrame frame, Object operand) {
            if (castInteger == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castInteger = insert(CastIntegerNodeFactory.create(null, true, true));
            }
            return castInteger.executeCast(frame, operand);
        }

        private Object castString(VirtualFrame frame, Object operand) {
            if (castString == null) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                castString = insert(CastStringNodeFactory.create(null, true, true, false));
            }
            return castString.executeCast(frame, operand);
        }

        private Object castList(VirtualFrame frame, Object operand) {
            if (castList == null) {
                CompilerDirectives.transferToInterpreter();
                castList = insert(CastListNodeFactory.create(null, true, false));
            }
            return castList.executeCast(frame, operand);
        }

        @Specialization(order = 10)
        RFunction coerce(VirtualFrame frame, Object value, RFunction operand) {
            return operand;
        }

        // int vector value

        @Specialization(order = 100)
        RAbstractIntVector coerce(VirtualFrame frame, RAbstractIntVector value, RAbstractIntVector operand) {
            return operand;
        }

        @Specialization(order = 101)
        RAbstractDoubleVector coerce(VirtualFrame frame, RAbstractIntVector value, RAbstractDoubleVector operand) {
            return operand;
        }

        @Specialization(order = 102)
        RAbstractIntVector coerce(VirtualFrame frame, RAbstractIntVector value, RAbstractLogicalVector operand) {
            return (RIntVector) castInteger(frame, operand);
        }

        @Specialization(order = 103)
        RAbstractStringVector coerce(VirtualFrame frame, RAbstractIntVector value, RAbstractStringVector operand) {
            return operand;
        }

        @Specialization(order = 104)
        RAbstractComplexVector coerce(VirtualFrame frame, RAbstractIntVector value, RAbstractComplexVector operand) {
            return operand;
        }

        @Specialization(order = 105)
        RIntVector coerce(VirtualFrame frame, RAbstractIntVector value, RAbstractRawVector operand) {
            throw RError.getSubassignTypeFix(getEncapsulatingSourceSection(), "integer", "raw");
        }

        @Specialization(order = 107)
        RList coerce(VirtualFrame frame, RAbstractIntVector value, RList operand) {
            return operand;
        }

        // double vector value

        @Specialization(order = 200)
        RDoubleVector coerce(VirtualFrame frame, RAbstractDoubleVector value, RAbstractIntVector operand) {
            return (RDoubleVector) castDouble(frame, operand);
        }

        @Specialization(order = 201)
        RAbstractDoubleVector coerce(VirtualFrame frame, RAbstractDoubleVector value, RAbstractDoubleVector operand) {
            return operand;
        }

        @Specialization(order = 202)
        RDoubleVector coerce(VirtualFrame frame, RAbstractDoubleVector value, RAbstractLogicalVector operand) {
            return (RDoubleVector) castDouble(frame, operand);
        }

        @Specialization(order = 203)
        RAbstractStringVector coerce(VirtualFrame frame, RAbstractDoubleVector value, RAbstractStringVector operand) {
            return operand;
        }

        @Specialization(order = 204)
        RAbstractComplexVector coerce(VirtualFrame frame, RAbstractDoubleVector value, RAbstractComplexVector operand) {
            return operand;
        }

        @Specialization(order = 205)
        RDoubleVector coerce(VirtualFrame frame, RAbstractDoubleVector value, RAbstractRawVector operand) {
            throw RError.getSubassignTypeFix(getEncapsulatingSourceSection(), "double", "raw");
        }

        @Specialization(order = 207)
        RList coerce(VirtualFrame frame, RAbstractDoubleVector value, RList operand) {
            return operand;
        }

        // logical vector value

        @Specialization(order = 300)
        RAbstractIntVector coerce(VirtualFrame frame, RAbstractLogicalVector value, RAbstractIntVector operand) {
            return operand;
        }

        @Specialization(order = 301)
        RAbstractDoubleVector coerce(VirtualFrame frame, RAbstractLogicalVector value, RAbstractDoubleVector operand) {
            return operand;
        }

        @Specialization(order = 302)
        RAbstractLogicalVector coerce(VirtualFrame frame, RAbstractLogicalVector value, RAbstractLogicalVector operand) {
            return operand;
        }

        @Specialization(order = 303)
        RAbstractStringVector coerce(VirtualFrame frame, RAbstractLogicalVector value, RAbstractStringVector operand) {
            return operand;
        }

        @Specialization(order = 304)
        RAbstractComplexVector coerce(VirtualFrame frame, RAbstractLogicalVector value, RAbstractComplexVector operand) {
            return operand;
        }

        @Specialization(order = 305)
        RLogicalVector coerce(VirtualFrame frame, RAbstractLogicalVector value, RAbstractRawVector operand) {
            throw RError.getSubassignTypeFix(getEncapsulatingSourceSection(), "logical", "raw");
        }

        @Specialization(order = 307)
        RList coerce(VirtualFrame frame, RAbstractLogicalVector value, RList operand) {
            return operand;
        }

        // string vector value

        @Specialization(order = 400)
        RStringVector coerce(VirtualFrame frame, RAbstractStringVector value, RAbstractIntVector operand) {
            return (RStringVector) castString(frame, operand);
        }

        @Specialization(order = 401)
        RStringVector coerce(VirtualFrame frame, RAbstractStringVector value, RAbstractDoubleVector operand) {
            return (RStringVector) castString(frame, operand);
        }

        @Specialization(order = 402)
        RStringVector coerce(VirtualFrame frame, RAbstractStringVector value, RAbstractLogicalVector operand) {
            return (RStringVector) castString(frame, operand);
        }

        @Specialization(order = 403)
        RAbstractStringVector coerce(VirtualFrame frame, RAbstractStringVector value, RAbstractStringVector operand) {
            return operand;
        }

        @Specialization(order = 404)
        RStringVector coerce(VirtualFrame frame, RAbstractStringVector value, RAbstractComplexVector operand) {
            return (RStringVector) castString(frame, operand);
        }

        @Specialization(order = 405)
        RStringVector coerce(VirtualFrame frame, RAbstractStringVector value, RAbstractRawVector operand) {
            throw RError.getSubassignTypeFix(getEncapsulatingSourceSection(), "character", "raw");
        }

        @Specialization(order = 407)
        RList coerce(VirtualFrame frame, RAbstractStringVector value, RList operand) {
            return operand;
        }

        // complex vector value

        @Specialization(order = 500)
        RComplexVector coerce(VirtualFrame frame, RAbstractComplexVector value, RAbstractIntVector operand) {
            return (RComplexVector) castComplex(frame, operand);
        }

        @Specialization(order = 501)
        RComplexVector coerce(VirtualFrame frame, RAbstractComplexVector value, RAbstractDoubleVector operand) {
            return (RComplexVector) castComplex(frame, operand);
        }

        @Specialization(order = 502)
        RComplexVector coerce(VirtualFrame frame, RAbstractComplexVector value, RAbstractLogicalVector operand) {
            return (RComplexVector) castComplex(frame, operand);
        }

        @Specialization(order = 503)
        RAbstractStringVector coerce(VirtualFrame frame, RAbstractComplexVector value, RAbstractStringVector operand) {
            return operand;
        }

        @Specialization(order = 504)
        RAbstractComplexVector coerce(VirtualFrame frame, RAbstractComplexVector value, RAbstractComplexVector operand) {
            return operand;
        }

        @Specialization(order = 505)
        RComplexVector coerce(VirtualFrame frame, RAbstractComplexVector value, RAbstractRawVector operand) {
            throw RError.getSubassignTypeFix(getEncapsulatingSourceSection(), "complex", "raw");
        }

        @Specialization(order = 507)
        RList coerce(VirtualFrame frame, RAbstractComplexVector value, RList operand) {
            return operand;
        }

        // raw vector value

        @Specialization(order = 605)
        RAbstractRawVector coerce(VirtualFrame frame, RAbstractRawVector value, RAbstractRawVector operand) {
            return operand;
        }

        @Specialization(order = 606, guards = "!isVectorList")
        RRawVector coerce(VirtualFrame frame, RAbstractRawVector value, RAbstractVector operand) {
            throw RError.getSubassignTypeFix(getEncapsulatingSourceSection(), "raw", RRuntime.classToString(operand.getElementClass(), false));
        }

        @Specialization(order = 607)
        RList coerce(VirtualFrame frame, RAbstractRawVector value, RList operand) {
            return operand;
        }

        // list vector value

        @Specialization(order = 707)
        RList coerce(VirtualFrame frame, RList value, RList operand) {
            return operand;
        }

        @Specialization(order = 708, guards = "!isVectorList")
        RList coerce(VirtualFrame frame, RList value, RAbstractVector operand) {
            return (RList) castList(frame, operand);
        }

        // function vector value

        @Specialization(order = 806)
        RFunction coerce(VirtualFrame frame, RFunction value, RAbstractVector operand) {
            throw RError.getSubassignTypeFix(getEncapsulatingSourceSection(), "closure", RRuntime.classToString(operand.getElementClass(), false));
        }

        // in all other cases, simply return the operand (no coercion)

        @Specialization(order = 1000)
        RNull coerce(RNull value, RNull operand) {
            return operand;
        }

        @Specialization(order = 1001)
        RNull coerce(RAbstractVector value, RNull operand) {
            return operand;
        }

        @Specialization(order = 1002)
        RAbstractVector coerce(RNull value, RAbstractVector operand) {
            return operand;
        }

        @Specialization(order = 1003)
        RAbstractVector coerce(RList value, RAbstractVector operand) {
            return operand;
        }

        protected boolean isVectorList(RAbstractVector value, RAbstractVector operand) {
            return operand.getElementClass() == Object.class;
        }

    }
}
