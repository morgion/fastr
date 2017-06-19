/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.test;

import java.util.function.Supplier;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import com.oracle.truffle.r.engine.TruffleRLanguageImpl;
import com.oracle.truffle.r.runtime.RArguments;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.ReturnException;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.context.RContext.RCloseable;
import com.oracle.truffle.r.runtime.data.RComplex;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.model.RAbstractDoubleVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractIntVector;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;

public class TestUtilities {

    private static final int NA_INDEX = 3;

    public static RAbstractVector generateVector(RType type, int size, boolean complete) {
        switch (type) {
            case Raw:
                return generateRaw(size);
            case Logical:
                return generateLogical(size, complete);
            case Integer:
                return generateInteger(size, complete);
            case Double:
                return generateDouble(size, complete);
            case Complex:
                return generateComplex(size, complete);
            case Character:
                return generateCharacter(size, complete);
            case List:
                return generateList(size, complete);
            default:
                throw new AssertionError();

        }
    }

    private static RAbstractVector generateRaw(int size) {
        byte[] array = new byte[size];
        for (int i = 0; i < size; i++) {
            array[i] = (byte) i;
        }
        return RDataFactory.createRawVector(array);
    }

    public static RAbstractVector generateLogical(int size, boolean complete) {
        byte[] array = new byte[size];
        for (int i = 0; i < size; i++) {
            int mode;
            if (complete) {
                mode = i % 2;
            } else {
                mode = i % 3;
            }
            byte value;
            switch (mode) {
                case 0:
                    value = RRuntime.LOGICAL_FALSE;
                    break;
                case 1:
                    value = RRuntime.LOGICAL_TRUE;
                    break;
                case 2:
                    value = RRuntime.LOGICAL_NA;
                    break;
                default:
                    throw new AssertionError();

            }
            array[i] = value;
        }
        return RDataFactory.createLogicalVector(array, complete || !complete && size < 3);
    }

    public static RAbstractIntVector generateInteger(int size, boolean complete) {
        int[] array = new int[size];
        for (int i = 0; i < size; i++) {
            array[i] = !complete && i % (NA_INDEX + 1) == NA_INDEX ? RRuntime.INT_NA : i;
        }
        return RDataFactory.createIntVector(array, complete || !complete && size < 3);
    }

    public static RAbstractDoubleVector generateDouble(int size, boolean complete) {
        double[] array = new double[size];
        for (int i = 0; i < size; i++) {
            array[i] = !complete && i % (NA_INDEX + 1) == NA_INDEX ? RRuntime.DOUBLE_NA : i;
        }
        return RDataFactory.createDoubleVector(array, complete || !complete && size < 3);
    }

    public static RAbstractVector generateComplex(int size, boolean complete) {
        double[] array = new double[size << 1];
        for (int i = 0; i < size; i++) {
            boolean useNA = !complete && i % (NA_INDEX + 1) == NA_INDEX;
            array[i << 1 - 1] = useNA ? RComplex.createNA().getRealPart() : i;
            array[i << 1] = useNA ? RComplex.createNA().getRealPart() : i;
        }
        return RDataFactory.createComplexVector(array, complete || !complete && size < NA_INDEX);
    }

    public static RAbstractVector generateCharacter(int size, boolean complete) {
        String[] array = new String[size];
        for (int i = 0; i < size; i++) {
            array[i] = !complete && i % (NA_INDEX + 1) == NA_INDEX ? RRuntime.STRING_NA : String.valueOf(i);
        }
        return RDataFactory.createStringVector(array, complete || !complete && size < 3);
    }

    public static RAbstractVector generateList(int size, boolean complete) {
        Object[] array = new Object[size];
        for (int i = 0; i < size; i++) {
            array[i] = !complete && i % (NA_INDEX + 1) == NA_INDEX ? RNull.instance : i;
        }
        RAbstractVector list = RDataFactory.createList(array);
        list.setComplete(complete || !complete && size < 3);
        return list;
    }

    public static RAbstractVector copy(RAbstractVector orig) {
        return withinTestContext(() -> orig.copy());
    }

    /**
     * Certain code needs to be run within valid RContext, e.g. copying, which tries to report to
     * memory tracer taken from RContext.
     */
    @SuppressWarnings("try")
    public static <T> T withinTestContext(Supplier<T> action) {
        try (RCloseable c = RContext.withinContext(TestBase.testVMContext)) {
            return action.get();
        }
    }

    /**
     * Creates a handle that emulates the behavior as if this node would be executed inside of an R
     * call.
     */
    public static <T extends Node> NodeHandle<T> createHandle(T node, NodeAdapter<T> invoke) {
        return new NodeHandle<>(Truffle.getRuntime().createCallTarget(new TestRoot<>(node, invoke)));
    }

    public interface NodeAdapter<T extends Node> {

        Object invoke(T node, Object... args);

    }

    public static class NodeHandle<T extends Node> {

        private final RootCallTarget target;

        public NodeHandle(RootCallTarget target) {
            this.target = target;
        }

        public RootCallTarget getTarget() {
            return target;
        }

        @SuppressWarnings("unchecked")
        public T getNode() {
            return ((TestRoot<T>) target.getRootNode()).node;
        }

        @SuppressWarnings("try")
        public Object call(Object... args) {
            try (RCloseable c = RContext.withinContext(TestBase.testVMContext)) {
                return target.call(RArguments.createUnitialized((Object) args));
            }
        }
    }

    private static class TestRoot<T extends Node> extends RootNode {

        private final NodeAdapter<T> invoke;
        @Child private T node;

        TestRoot(T node, NodeAdapter<T> invoke) {
            super(TruffleRLanguageImpl.getCurrentLanguage());
            this.node = node;
            this.invoke = invoke;
        }

        @Override
        public SourceSection getSourceSection() {
            return RSyntaxNode.INTERNAL;
        }

        @Override
        public Object execute(VirtualFrame frame) {
            try {
                return invoke.invoke(node, (Object[]) RArguments.getArgument(frame, 0));
            } catch (ReturnException e) {
                return e.getResult();
            }
        }
    }
}
