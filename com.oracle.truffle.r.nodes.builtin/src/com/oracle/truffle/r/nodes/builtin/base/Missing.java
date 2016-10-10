/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.builtin.base;

import static com.oracle.truffle.r.runtime.RDispatch.SPECIAL;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.COMPLEX;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.PRIMITIVE;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.builtin.base.MissingFactory.MissingCheckCacheNodeGen;
import com.oracle.truffle.r.nodes.function.GetMissingValueNode;
import com.oracle.truffle.r.nodes.function.PromiseHelperNode;
import com.oracle.truffle.r.nodes.function.RMissingHelper;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RError.Message;
import com.oracle.truffle.r.runtime.RInternalError;
import com.oracle.truffle.r.runtime.RRuntime;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.data.RPromise;
import com.oracle.truffle.r.runtime.data.RPromise.PromiseState;
import com.oracle.truffle.r.runtime.nodes.RNode;
import com.oracle.truffle.r.runtime.nodes.RSyntaxConstant;
import com.oracle.truffle.r.runtime.nodes.RSyntaxLookup;
import com.oracle.truffle.r.runtime.nodes.RSyntaxNode;

@RBuiltin(name = "missing", kind = PRIMITIVE, parameterNames = {"x"}, dispatch = SPECIAL, behavior = COMPLEX)
public final class Missing extends RBuiltinNode {

    private final String symbol;

    @Child private MissingCheckCache cache;

    private Missing(String symbol) {
        this.symbol = symbol;
        this.cache = MissingCheckCache.create(0);
    }

    public abstract static class MissingCheckCache extends Node {

        protected static final int CACHE_LIMIT = 3;

        private final int level;

        protected MissingCheckCache(int level) {
            this.level = level;
        }

        public static MissingCheckCache create(int level) {
            return MissingCheckCacheNodeGen.create(level);
        }

        public abstract boolean execute(Frame frame, String symbol);

        protected MissingCheckLevel createNodeForRep(String symbol) {
            return new MissingCheckLevel(symbol, level);
        }

        @Specialization(limit = "CACHE_LIMIT", guards = "cachedSymbol == symbol")
        public static boolean checkCached(Frame frame, @SuppressWarnings("unused") String symbol, //
                        @SuppressWarnings("unused") @Cached("symbol") String cachedSymbol, //
                        @Cached("createNodeForRep(symbol)") MissingCheckLevel node) {
            return node.execute(frame);
        }

        @Specialization(contains = "checkCached")
        public static boolean check(Frame frame, String symbol) {
            return RMissingHelper.isMissingArgument(frame, symbol);
        }
    }

    protected static class MissingCheckLevel extends Node {

        @Child private GetMissingValueNode getMissingValue;
        @Child private MissingCheckCache recursive;
        @Child private PromiseHelperNode promiseHelper;

        @CompilationFinal private FrameDescriptor recursiveDesc;

        private final ConditionProfile isNullProfile = ConditionProfile.createBinaryProfile();
        private final ConditionProfile isMissingProfile = ConditionProfile.createBinaryProfile();
        private final ConditionProfile isPromiseProfile = ConditionProfile.createBinaryProfile();
        private final ConditionProfile isSymbolNullProfile = ConditionProfile.createBinaryProfile();
        private final int level;

        MissingCheckLevel(String symbol, int level) {
            this.level = level;
            this.getMissingValue = GetMissingValueNode.create(symbol);
        }

        public boolean execute(Frame frame) {
            // Read symbols value directly
            Object value = getMissingValue.execute(frame);
            if (isNullProfile.profile(value == null)) {
                // In case we are not able to read the symbol in current frame: This is not an
                // argument and thus return false
                return false;
            }

            if (isMissingProfile.profile(RMissingHelper.isMissing(value))) {
                return true;
            }

            // This might be a promise...
            if (isPromiseProfile.profile(value instanceof RPromise)) {
                RPromise promise = (RPromise) value;
                if (promiseHelper == null) {
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    promiseHelper = insert(new PromiseHelperNode());
                    recursiveDesc = !promise.isEvaluated() && promise.getFrame() != null ? promise.getFrame().getFrameDescriptor() : null;
                }
                if (level == 0 && promiseHelper.isDefaultArgument(promise)) {
                    return true;
                }
                if (promiseHelper.isEvaluated(promise)) {
                    if (level > 0) {
                        return false;
                    }
                } else {
                    // Check: If there is a cycle, return true. (This is done like in GNU R)
                    if (promiseHelper.isUnderEvaluation(promise)) {
                        return true;
                    }
                }
                String symbol = promise.getClosure().asSymbol();
                if (isSymbolNullProfile.profile(symbol == null)) {
                    return false;
                } else {
                    if (recursiveDesc != null) {
                        promiseHelper.materialize(promise); // Ensure that promise holds a frame
                    }
                    if (recursiveDesc == null || recursiveDesc != promise.getFrame().getFrameDescriptor()) {
                        if (promiseHelper.isEvaluated(promise)) {
                            return false;
                        } else {
                            return RMissingHelper.isMissingName(promise);
                        }
                    } else {
                        if (recursiveDesc == null) {
                            promiseHelper.materialize(promise); // Ensure that promise holds a frame
                        }
                        PromiseState state = promise.getState();
                        try {
                            promise.setState(PromiseState.UnderEvaluation);
                            if (recursive == null) {
                                CompilerDirectives.transferToInterpreterAndInvalidate();
                                recursive = insert(MissingCheckCache.create(level + 1));
                            }
                            return recursive.execute(promise.getFrame(), symbol);
                        } finally {
                            promise.setState(state);
                        }
                    }
                }
            }
            return false;
        }
    }

    @Override
    public Object executeBuiltin(VirtualFrame frame, Object... args) {
        throw RInternalError.shouldNotReachHere();
    }

    @Override
    public Object execute(VirtualFrame frame) {
        return RRuntime.asLogical(cache.execute(frame, symbol));
    }

    public static RBuiltinNode create(RNode[] arguments) {
        if (arguments.length != 1) {
            throw RError.error(RError.SHOW_CALLER, Message.ARGUMENTS_REQUIRED_COUNT, arguments.length, "missing", 1);
        }
        RSyntaxNode arg = arguments[0].asRSyntaxNode();
        String symbol = null;
        if (arg instanceof RSyntaxLookup) {
            symbol = ((RSyntaxLookup) arg).getIdentifier();
        } else if (arg instanceof RSyntaxConstant) {
            symbol = RRuntime.asStringLengthOne(((RSyntaxConstant) arg).getValue());
        }
        if (symbol == null) {
            throw RError.error(RError.SHOW_CALLER, Message.INVALID_USE, "missing");
        }
        return new Missing(symbol);
    }
}
