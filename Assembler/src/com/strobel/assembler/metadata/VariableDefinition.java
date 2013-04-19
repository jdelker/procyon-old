/*
 * VariableDefinition.java
 *
 * Copyright (c) 2013 Mike Strobel
 *
 * This source code is based on Mono.Cecil from Jb Evain, Copyright (c) Jb Evain;
 * and ILSpy/ICSharpCode from SharpDevelop, Copyright (c) AlphaSierraPapa.
 *
 * This source code is subject to terms and conditions of the Apache License, Version 2.0.
 * A copy of the license can be found in the License.html file at the root of this distribution.
 * By using this source code in any fashion, you are agreeing to be bound by the terms of the
 * Apache License, Version 2.0.
 *
 * You must not remove this notice, or any other, from this software.
 */

package com.strobel.assembler.metadata;

/**
 * User: Mike Strobel
 * Date: 1/6/13
 * Time: 2:11 PM
 */
public final class VariableDefinition extends VariableReference {
    private final int _slot;

    private int _scopeStart;
    private int _scopeEnd;
    private boolean _isTypeKnown;
    private boolean _fromMetadata;
    private TypeReference _declaringType;

    public VariableDefinition(final int slot, final String name, final TypeReference variableType) {
        super(name, variableType);
        _slot = slot;
    }

    @Override
    public final TypeReference getDeclaringType() {
        return _declaringType;
    }

    final void setDeclaringType(final TypeReference declaringType) {
        _declaringType = declaringType;
    }

    public final int getSlot() {
        return _slot;
    }

    public final int getSize() {
        return getVariableType().getSimpleType().stackSlots();
    }

    public final int getScopeStart() {
        return _scopeStart;
    }

    public final void setScopeStart(final int scopeStart) {
        _scopeStart = scopeStart;
    }

    public final int getScopeEnd() {
        return _scopeEnd;
    }

    public final void setScopeEnd(final int scopeEnd) {
        _scopeEnd = scopeEnd;
    }

    public final boolean isTypeKnown() {
        return _isTypeKnown;
    }

    public final void setTypeKnown(final boolean typeKnown) {
        _isTypeKnown = typeKnown;
    }

    public final boolean isFromMetadata() {
        return _fromMetadata;
    }

    public final void setFromMetadata(final boolean fromMetadata) {
        _fromMetadata = fromMetadata;
    }

    @Override
    public VariableDefinition resolve() {
        return this;
    }

    @Override
    public String toString() {
        return "VariableDefinition{" +
               "Slot=" + _slot +
               ", ScopeStart=" + _scopeStart +
               ", ScopeEnd=" + _scopeEnd +
               ", Name=" + getName() +
               ", IsFromMetadata=" + _fromMetadata +
               ", IsTypeKnown=" + _isTypeKnown +
               ", Type=" + getVariableType().getSignature() +
               '}';
    }
}
