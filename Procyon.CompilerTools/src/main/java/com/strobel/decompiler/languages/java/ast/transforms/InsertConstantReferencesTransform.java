package com.strobel.decompiler.languages.java.ast.transforms;

import com.strobel.assembler.metadata.FieldReference;
import com.strobel.assembler.metadata.IMetadataResolver;
import com.strobel.assembler.metadata.JvmType;
import com.strobel.assembler.metadata.MetadataParser;
import com.strobel.assembler.metadata.TypeDefinition;
import com.strobel.assembler.metadata.TypeReference;
import com.strobel.decompiler.DecompilerContext;
import com.strobel.decompiler.languages.java.ast.*;

public class InsertConstantReferencesTransform extends ContextTrackingVisitor<Void> {
    public InsertConstantReferencesTransform(final DecompilerContext context) {
        super(context);
    }

    @Override
    public Void visitPrimitiveExpression(final PrimitiveExpression node, final Void data) {
        final Object value = node.getValue();

        if (value instanceof Number) {
            tryRewriteConstant(node, value);
        }
        
        return null;
    }

    private void tryRewriteConstant(final PrimitiveExpression node, final Object value) {
        final JvmType jvmType;
        final String fieldName;

        if (value instanceof Double) {
            final double d = (double) value;

            jvmType = JvmType.Double;

            if (d == Double.POSITIVE_INFINITY) {
                fieldName = "POSITIVE_INFINITY";
            }
            else if (d == Double.NEGATIVE_INFINITY) {
                fieldName = "POSITIVE_INFINITY";
            }
            else if (Double.isNaN(d)) {
                fieldName = "NaN";
            }
            else if (d == Double.MIN_VALUE) {
                fieldName = "MIN_VALUE";
            }
            else if (d == Double.MAX_VALUE) {
                fieldName = "MAX_VALUE";
            }
            else if (d == Double.MIN_NORMAL) {
                fieldName = "MIN_NORMAL";
            }
            else {
                return;
            }
        }
        else if (value instanceof Float) {
            final float f = (float) value;

            jvmType = JvmType.Float;

            if (f == Float.POSITIVE_INFINITY) {
                fieldName = "POSITIVE_INFINITY";
            }
            else if (f == Float.NEGATIVE_INFINITY) {
                fieldName = "POSITIVE_INFINITY";
            }
            else if (Float.isNaN(f)) {
                fieldName = "NaN";
            }
            else if (f == Float.MIN_VALUE) {
                fieldName = "MIN_VALUE";
            }
            else if (f == Float.MAX_VALUE) {
                fieldName = "MAX_VALUE";
            }
            else if (f == Float.MIN_NORMAL) {
                fieldName = "MIN_NORMAL";
            }
            else {
                return;
            }
        }
        else if (value instanceof Long) {
            final long l = (long) value;

            jvmType = JvmType.Long;

            if (l == Long.MIN_VALUE) {
                fieldName = "MIN_VALUE";
            }
            else if (l == Long.MAX_VALUE) {
                fieldName = "MAX_VALUE";
            }
            else {
                return;
            }
        }
        else if (value instanceof Integer) {
            final int i = (int) value;

            jvmType = JvmType.Integer;

            if (i == Integer.MIN_VALUE) {
                fieldName = "MIN_VALUE";
            }
            else if (i == Integer.MAX_VALUE) {
                fieldName = "MAX_VALUE";
            }
            else {
                return;
            }
        }
        else if (value instanceof Short) {
            final short s = (short) value;

            jvmType = JvmType.Short;

            if (s == Short.MIN_VALUE) {
                fieldName = "MIN_VALUE";
            }
            else if (s == Short.MAX_VALUE) {
                fieldName = "MAX_VALUE";
            }
            else {
                return;
            }
        }
        else if (value instanceof Byte) {
            final byte b = (byte) value;

            jvmType = JvmType.Byte;

            if (b == Byte.MIN_VALUE) {
                fieldName = "MIN_VALUE";
            }
            else if (b == Byte.MAX_VALUE) {
                fieldName = "MAX_VALUE";
            }
            else {
                return;
            }
        }
        else {
            return;
        }

        final MetadataParser parser;
        final TypeDefinition currentType = context.getCurrentType();

        if (currentType != null) {
            parser = new MetadataParser(currentType);
        }
        else {
            parser = new MetadataParser(IMetadataResolver.EMPTY);
        }

        final TypeReference declaringType = parser.parseTypeDescriptor("java/lang/" + jvmType.name());
        final FieldReference field = parser.parseField(declaringType, fieldName, jvmType.getDescriptorPrefix());

        final AstType astType;
        final AstBuilder astBuilder = context.getUserData(Keys.AST_BUILDER);

        if (astBuilder != null) {
            astType = astBuilder.convertType(declaringType);
        }
        else {
            astType = new SimpleType(declaringType.getName());
            astType.putUserData(Keys.TYPE_REFERENCE, declaringType);
        }

        final MemberReferenceExpression memberReference = new MemberReferenceExpression(
            new TypeReferenceExpression(astType),
            fieldName
        );

        memberReference.putUserData(Keys.MEMBER_REFERENCE, field);
        memberReference.putUserData(Keys.CONSTANT_VALUE, value);

        node.replaceWith(memberReference);
    }
}