/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.runtime.data.nodes.attributes;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeCost;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.r.runtime.DSLConfig;
import com.oracle.truffle.r.runtime.RType;

@ImportStatic(DSLConfig.class)
@NodeInfo(cost = NodeCost.NONE)
public abstract class TypeFromModeNode extends Node {

    protected static final int CACHE_LIMIT = 2;

    public static TypeFromModeNode create() {
        return TypeFromModeNodeGen.create();
    }

    public abstract RType execute(Object mode);

    @Specialization(limit = "getCacheSize(CACHE_LIMIT)", guards = "mode == cachedMode")
    protected RType getTypeCAched(@SuppressWarnings("unused") String mode,
                    @Cached("mode") @SuppressWarnings("unused") String cachedMode,
                    @Cached("fromMode(mode)") RType type) {
        return type;
    }

    @Specialization
    protected RType getType(String mode) {
        return RType.fromMode(mode);
    }
}
