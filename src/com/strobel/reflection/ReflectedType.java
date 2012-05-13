package com.strobel.reflection;

import com.strobel.core.VerifyArgument;

import java.lang.annotation.Annotation;
import java.lang.reflect.TypeVariable;

/**
 * @author Mike Strobel
 */
final class ReflectedType<T> extends Type<T> {
    private final Class<T> _class;
    private final Type _baseType;
    private final TypeBindings _typeArguments;
    private final TypeList _interfaces;

    ReflectedType(final Class<T> rawType, final TypeBindings typeArguments, final Type baseType, final TypeList interfaces) {
        _class = VerifyArgument.notNull(rawType, "rawType");
        _typeArguments = VerifyArgument.notNull(typeArguments, "typeArguments");

        if (baseType == null && rawType != java.lang.Object.class && !rawType.isInterface()) {
            throw new IllegalArgumentException("Base type cannot be null.");
        }

        final TypeList genericParameters = typeArguments.getGenericParameters();

        for (int i = 0, n = genericParameters.size(); i < n; i++) {
            final Type p = genericParameters.get(i);
            if (p instanceof GenericParameterType) {
                final GenericParameterType gp = (GenericParameterType) p;
                if (gp.getRawTypeVariable().getGenericDeclaration() == rawType) {
                    gp.setDeclaringType(this);
                }
            }
        }

        _baseType = baseType;
        _interfaces = VerifyArgument.notNull(interfaces, "interfaces");
    }

    protected TypeList populateGenericParameters() {
        final TypeVariable<Class<T>>[] typeParameters = _class.getTypeParameters();
        final Type[] genericParameters = new Type[typeParameters.length];

        for (int i = 0, n = typeParameters.length; i < n; i++) {
            final TypeVariable<?> typeVariable = typeParameters[i];
            genericParameters[i] = new GenericParameterType(typeVariable, this, i);
        }

        return new TypeList(genericParameters);
    }

    @Override
    public Type makeGenericTypeCore(final TypeList typeArguments) {
        return resolve(_class, TypeBindings.create(getGenericTypeParameters(), typeArguments));
    }

    public TypeContext getContext() {
        return TypeContext.SYSTEM;
    }

    @Override
    public Type getBaseType() {
        return _baseType;
    }

    @Override
    public TypeList getInterfaces() {
        return _interfaces;
    }

    @Override
    public Class<T> getErasedClass() {
        return _class;
    }

    @Override
    public boolean isGenericType() {
        return !_typeArguments.isEmpty();
    }

    @Override
    public TypeBindings getTypeBindings() {
        return _typeArguments;
    }

    @Override
    public Type getGenericTypeDefinition() {
        if (isGenericTypeDefinition()) {
            return this;
        }
        return Type.of(_class);
    }

    @Override
    public <A extends Annotation> A getAnnotation(final Class<A> annotationClass) {
        return _class.getAnnotation(annotationClass);
    }

    @Override
    public boolean isAnnotationPresent(final Class<? extends Annotation> annotationClass) {
        return _class.isAnnotationPresent(annotationClass);
    }

    @Override
    public Annotation[] getAnnotations() {
        return _class.getAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return _class.getDeclaredAnnotations();
    }

    @Override
    public MemberType getMemberType() {
        return MemberType.TypeInfo;
    }

    @Override
    public Type getDeclaringType() {
        return of(_class.getDeclaringClass());
    }

    @Override
    int getModifiers() {
        return _class.getModifiers();
    }

    @Override
    public <P, R> R accept(final TypeVisitor<P, R> visitor, final P parameter) {
        return visitor.visitType(this, parameter);
    }
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // MEMBER LOOKUP                                                                                                      //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
}