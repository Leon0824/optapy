package org.optaplanner.jpyinterpreter.types;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.objectweb.asm.Type;
import org.optaplanner.jpyinterpreter.FieldDescriptor;
import org.optaplanner.jpyinterpreter.PythonBinaryOperators;
import org.optaplanner.jpyinterpreter.PythonClassTranslator;
import org.optaplanner.jpyinterpreter.PythonFunctionSignature;
import org.optaplanner.jpyinterpreter.PythonLikeObject;
import org.optaplanner.jpyinterpreter.PythonOverloadImplementor;
import org.optaplanner.jpyinterpreter.PythonTernaryOperators;
import org.optaplanner.jpyinterpreter.PythonUnaryOperator;
import org.optaplanner.jpyinterpreter.builtins.TernaryDunderBuiltin;
import org.optaplanner.jpyinterpreter.types.collections.PythonLikeDict;
import org.optaplanner.jpyinterpreter.types.collections.PythonLikeTuple;
import org.optaplanner.jpyinterpreter.types.errors.AttributeError;
import org.optaplanner.jpyinterpreter.types.errors.TypeError;
import org.optaplanner.jpyinterpreter.types.errors.ValueError;

public class PythonLikeType implements PythonLikeObject,
        PythonLikeFunction {
    public final Map<String, PythonLikeObject> __dir__;

    private final String TYPE_NAME;

    private final String JAVA_TYPE_INTERNAL_NAME;
    private final List<PythonLikeType> PARENT_TYPES;
    public final List<PythonLikeType> MRO;

    private final Map<String, PythonKnownFunctionType> functionNameToKnownFunctionType;
    private Optional<PythonKnownFunctionType> constructorKnownFunctionType;

    private final Map<String, FieldDescriptor> instanceFieldToFieldDescriptorMap;

    private PythonLikeFunction constructor;

    public PythonLikeType(String typeName, Class<? extends PythonLikeObject> javaClass) {
        this(typeName, javaClass, List.of(BuiltinTypes.BASE_TYPE));
    }

    public PythonLikeType(String typeName, Class<? extends PythonLikeObject> javaClass, List<PythonLikeType> parents) {
        TYPE_NAME = typeName;
        JAVA_TYPE_INTERNAL_NAME = Type.getInternalName(javaClass);
        PARENT_TYPES = parents;
        constructor = (positional, keywords, callerInstance) -> {
            throw new UnsupportedOperationException("Cannot create instance of type (" + TYPE_NAME + ").");
        };
        __dir__ = new HashMap<>();
        functionNameToKnownFunctionType = new HashMap<>();
        constructorKnownFunctionType = Optional.empty();
        instanceFieldToFieldDescriptorMap = new HashMap<>();
        MRO = determineMRO();
    }

    public PythonLikeType(String typeName, String javaTypeInternalName, List<PythonLikeType> parents) {
        TYPE_NAME = typeName;
        JAVA_TYPE_INTERNAL_NAME = javaTypeInternalName;
        PARENT_TYPES = parents;
        constructor = (positional, keywords, callerInstance) -> {
            throw new UnsupportedOperationException("Cannot create instance of type (" + TYPE_NAME + ").");
        };
        __dir__ = new HashMap<>();
        functionNameToKnownFunctionType = new HashMap<>();
        constructorKnownFunctionType = Optional.empty();
        instanceFieldToFieldDescriptorMap = new HashMap<>();
        MRO = determineMRO();
    }

    public PythonLikeType(String typeName, Class<? extends PythonLikeObject> javaClass, Consumer<PythonLikeType> initializer) {
        this(typeName, javaClass, List.of(BuiltinTypes.BASE_TYPE));
        initializer.accept(this);
    }

    private List<PythonLikeType> determineMRO() {
        List<PythonLikeType> out = new ArrayList<>();
        out.add(this);
        out.addAll(mergeMRO());
        return out;
    }

    private List<PythonLikeType> mergeMRO() {
        List<PythonLikeType> out = new ArrayList<>();
        List<List<PythonLikeType>> parentMROLists = new ArrayList<>();
        for (PythonLikeType parent : PARENT_TYPES) {
            parentMROLists.add(new ArrayList<>(parent.MRO));
        }
        parentMROLists.add(new ArrayList<>(PARENT_TYPES)); // to preserve local precedent order, add list of parents last

        while (!parentMROLists.stream().allMatch(List::isEmpty)) {
            boolean candidateFound = false;
            for (List<PythonLikeType> parentMRO : parentMROLists) {
                if (!parentMRO.isEmpty()) {
                    PythonLikeType candidate = parentMRO.get(0);
                    if (parentMROLists.stream().allMatch(mro -> mro.indexOf(candidate) < 1)) {
                        out.add(candidate);
                        parentMROLists.forEach(mro -> {
                            if (!mro.isEmpty() && mro.get(0) == candidate) {
                                mro.remove(0);
                            }
                        });
                        candidateFound = true;
                        break;
                    }
                }
            }
            if (!candidateFound) {
                throw new TypeError("Cannot calculate MRO; Cycle found");
            }
        }
        return out;
    }

    public boolean isInstance(PythonLikeObject object) {
        PythonLikeType objectType = object.__getType();
        return objectType.isSubclassOf(this);
    }

    public static PythonLikeType registerBaseType() {
        try {
            BuiltinTypes.BASE_TYPE.addBinaryMethod(PythonBinaryOperators.GET_ATTRIBUTE,
                    PythonLikeObject.class.getMethod("$method$__getattribute__", PythonString.class));
            BuiltinTypes.BASE_TYPE.addTernaryMethod(PythonTernaryOperators.SET_ATTRIBUTE,
                    PythonLikeObject.class.getMethod("$method$__setattr__", PythonString.class, PythonLikeObject.class));
            BuiltinTypes.BASE_TYPE.addBinaryMethod(PythonBinaryOperators.DELETE_ATTRIBUTE,
                    PythonLikeObject.class.getMethod("$method$__delattr__", PythonString.class));
            BuiltinTypes.BASE_TYPE.addBinaryMethod(PythonBinaryOperators.EQUAL,
                    PythonLikeObject.class.getMethod("$method$__eq__", PythonLikeObject.class));
            BuiltinTypes.BASE_TYPE.addBinaryMethod(PythonBinaryOperators.NOT_EQUAL,
                    PythonLikeObject.class.getMethod("$method$__ne__", PythonLikeObject.class));
            BuiltinTypes.BASE_TYPE.addUnaryMethod(PythonUnaryOperator.AS_STRING,
                    PythonLikeObject.class.getMethod("$method$__str__"));
            BuiltinTypes.BASE_TYPE.addUnaryMethod(PythonUnaryOperator.REPRESENTATION,
                    PythonLikeObject.class.getMethod("$method$__repr__"));
            BuiltinTypes.BASE_TYPE.addUnaryMethod(PythonUnaryOperator.HASH,
                    PythonLikeObject.class.getMethod("$method$__hash__"));
            BuiltinTypes.BASE_TYPE.addBinaryMethod(PythonBinaryOperators.FORMAT,
                    PythonLikeObject.class.getMethod("$method$__format__", PythonLikeObject.class));
            BuiltinTypes.BASE_TYPE
                    .setConstructor((vargs, kwargs, callerInstance) -> new AbstractPythonLikeObject(BuiltinTypes.BASE_TYPE) {
                    });

            PythonOverloadImplementor.createDispatchesFor(BuiltinTypes.BASE_TYPE);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
        return BuiltinTypes.BASE_TYPE;
    }

    public static PythonLikeType registerTypeType() {
        BuiltinTypes.TYPE_TYPE.setConstructor((positional, keywords, callerInstance) -> {
            if (positional.size() == 1) {
                return positional.get(0).__getType();
            } else if (positional.size() == 3) {
                PythonString name = (PythonString) positional.get(0);
                PythonLikeTuple baseClasses = (PythonLikeTuple) positional.get(1);
                PythonLikeDict dict = (PythonLikeDict) positional.get(2);

                PythonLikeType out;
                if (baseClasses.isEmpty()) {
                    out = new PythonLikeType(name.value, PythonLikeObject.class);
                } else {
                    out = new PythonLikeType(name.value, PythonLikeObject.class, (List) baseClasses);
                }

                for (Map.Entry<PythonLikeObject, PythonLikeObject> entry : dict.entrySet()) {
                    PythonString attributeName = (PythonString) entry.getKey();

                    out.__setAttribute(attributeName.value, entry.getValue());
                }

                return out;
            } else {
                throw new ValueError("type takes 1 or 3 positional arguments, got " + positional.size());
            }
        });

        return BuiltinTypes.TYPE_TYPE;
    }

    @Override
    public PythonLikeObject $method$__getattribute__(PythonString pythonName) {
        String name = pythonName.value;
        PythonLikeObject typeResult = __getAttributeOrNull(name);
        if (typeResult != null) {
            PythonLikeObject maybeDescriptor = typeResult.__getAttributeOrNull(PythonTernaryOperators.GET.dunderMethod);
            if (maybeDescriptor == null) {
                maybeDescriptor = typeResult.__getType().__getAttributeOrNull(PythonTernaryOperators.GET.dunderMethod);
            }

            if (maybeDescriptor != null) {
                if (!(maybeDescriptor instanceof PythonLikeFunction)) {
                    throw new UnsupportedOperationException("'" + maybeDescriptor.__getType() + "' is not callable");
                }
                return TernaryDunderBuiltin.GET_DESCRIPTOR.invoke(typeResult, PythonNone.INSTANCE, this);
            }
            return typeResult;
        }

        throw new AttributeError("object '" + this + "' does not have attribute '" + name + "'");
    }

    public void addMethod(String methodName, Method method) {
        addMethod(methodName, PythonFunctionSignature.forMethod(method));
    }

    public void addUnaryMethod(PythonUnaryOperator operator, Method method) {
        addMethod(operator.getDunderMethod(), PythonFunctionSignature.forMethod(method));
    }

    public void addBinaryMethod(PythonBinaryOperators operator, Method method) {
        addMethod(operator.getDunderMethod(), PythonFunctionSignature.forMethod(method));
        if (operator.hasRightDunderMethod() && !operator.isComparisonMethod()) {
            addMethod(operator.getRightDunderMethod(), PythonFunctionSignature.forMethod(method));
        }
    }

    public void addLeftBinaryMethod(PythonBinaryOperators operator, Method method) {
        addMethod(operator.getDunderMethod(), PythonFunctionSignature.forMethod(method));
    }

    public void addRightBinaryMethod(PythonBinaryOperators operator, Method method) {
        addMethod(operator.getRightDunderMethod(), PythonFunctionSignature.forMethod(method));
    }

    public void addTernaryMethod(PythonTernaryOperators operator, Method method) {
        addMethod(operator.getDunderMethod(), PythonFunctionSignature.forMethod(method));
    }

    public void addUnaryMethod(PythonUnaryOperator operator, PythonFunctionSignature method) {
        addMethod(operator.getDunderMethod(), method);
    }

    public void addBinaryMethod(PythonBinaryOperators operator, PythonFunctionSignature method) {
        addMethod(operator.getDunderMethod(), method);
        if (operator.hasRightDunderMethod() && !operator.isComparisonMethod()) {
            addMethod(operator.getRightDunderMethod(), method);
        }
    }

    public void addLeftBinaryMethod(PythonBinaryOperators operator, PythonFunctionSignature method) {
        addMethod(operator.getDunderMethod(), method);
    }

    public void addRightBinaryMethod(PythonBinaryOperators operator, PythonFunctionSignature method) {
        addMethod(operator.getRightDunderMethod(), method);
    }

    public void addTernaryMethod(PythonTernaryOperators operator, PythonFunctionSignature method) {
        addMethod(operator.getDunderMethod(), method);
    }

    public void clearMethod(String methodName) {
        PythonKnownFunctionType knownFunctionType = functionNameToKnownFunctionType.computeIfAbsent(methodName,
                key -> new PythonKnownFunctionType(methodName, new ArrayList<>()));
        knownFunctionType.getOverloadFunctionSignatureList().clear();
    }

    public void addMethod(String methodName, PythonFunctionSignature method) {
        PythonKnownFunctionType knownFunctionType = functionNameToKnownFunctionType.computeIfAbsent(methodName,
                key -> new PythonKnownFunctionType(methodName, new ArrayList<>()));
        knownFunctionType.getOverloadFunctionSignatureList().add(method);
    }

    public Set<String> getKnownMethodsDefinedByClass() {
        return functionNameToKnownFunctionType.keySet();
    }

    public Set<String> getKnownMethods() {
        Set<String> out = new HashSet<>();
        getAssignableTypesStream().forEach(type -> out.addAll(type.getKnownMethodsDefinedByClass()));
        return out;
    }

    public void setConstructor(PythonLikeFunction constructor) {
        this.constructor = constructor;
    }

    public void addConstructor(PythonFunctionSignature constructor) {
        if (constructorKnownFunctionType.isEmpty()) {
            constructorKnownFunctionType = Optional.of(new PythonKnownFunctionType("<init>", new ArrayList<>()));
        }
        constructorKnownFunctionType.get().getOverloadFunctionSignatureList().add(constructor);
    }

    public Optional<PythonKnownFunctionType> getMethodType(String methodName) {
        PythonKnownFunctionType out = new PythonKnownFunctionType(methodName, new ArrayList<>());
        getAssignableTypesStream().forEach(type -> {
            PythonKnownFunctionType knownFunctionType = type.functionNameToKnownFunctionType.get(methodName);
            if (knownFunctionType != null) {
                out.getOverloadFunctionSignatureList().addAll(knownFunctionType.getOverloadFunctionSignatureList());
            }
        });

        if (out.getOverloadFunctionSignatureList().isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(out);
    }

    public Optional<PythonClassTranslator.PythonMethodKind> getMethodKind(String methodName) {
        PythonLikeObject maybeMethod = __getAttributeOrNull(methodName);
        if (maybeMethod != null) {
            PythonLikeType methodKind = maybeMethod.__getType();
            if (methodKind == BuiltinTypes.FUNCTION_TYPE) {
                return Optional.of(PythonClassTranslator.PythonMethodKind.VIRTUAL_METHOD);
            }
            if (methodKind == BuiltinTypes.STATIC_FUNCTION_TYPE) {
                return Optional.of(PythonClassTranslator.PythonMethodKind.STATIC_METHOD);
            }
            if (methodKind == BuiltinTypes.CLASS_FUNCTION_TYPE) {
                return Optional.of(PythonClassTranslator.PythonMethodKind.CLASS_METHOD);
            }
            return Optional.empty();
        }

        Optional<PythonKnownFunctionType> maybeKnownFunctionType = getMethodType(methodName);
        if (maybeKnownFunctionType.isPresent()) {
            PythonKnownFunctionType knownFunctionType = maybeKnownFunctionType.get();
            switch (knownFunctionType.getOverloadFunctionSignatureList().get(0).getMethodDescriptor().getMethodType()) {
                case VIRTUAL:
                case INTERFACE:
                case CONSTRUCTOR:
                case STATIC_AS_VIRTUAL:
                    return Optional.of(PythonClassTranslator.PythonMethodKind.VIRTUAL_METHOD);
                case STATIC:
                    return Optional.of(PythonClassTranslator.PythonMethodKind.STATIC_METHOD);
                case CLASS:
                    return Optional.of(PythonClassTranslator.PythonMethodKind.CLASS_METHOD);
                default:
                    throw new IllegalStateException("Unhandled case " + knownFunctionType.getOverloadFunctionSignatureList()
                            .get(0).getMethodDescriptor().getMethodType());
            }
        }

        return Optional.empty();
    }

    public Optional<PythonKnownFunctionType> getConstructorType() {
        return constructorKnownFunctionType;
    }

    public Optional<FieldDescriptor> getInstanceFieldDescriptor(String fieldName) {
        return getAssignableTypesStream().map(PythonLikeType::getInstanceFieldToFieldDescriptorMap)
                .filter(map -> map.containsKey(fieldName))
                .map(map -> map.get(fieldName))
                .findAny();
    }

    public void addInstanceField(FieldDescriptor fieldDescriptor) {
        Optional<FieldDescriptor> maybeExistingField = getInstanceFieldDescriptor(fieldDescriptor.getPythonFieldName());
        if (maybeExistingField.isPresent()) {
            PythonLikeType existingFieldType = maybeExistingField.get().getFieldPythonLikeType();
            if (!fieldDescriptor.getFieldPythonLikeType().isSubclassOf(existingFieldType)) {
                throw new IllegalStateException("Field (" + fieldDescriptor.getPythonFieldName() + ") already exist with type ("
                        +
                        existingFieldType + ") which is not assignable from (" + fieldDescriptor.getFieldPythonLikeType()
                        + ").");
            }
        } else {
            instanceFieldToFieldDescriptorMap.put(fieldDescriptor.getPythonFieldName(), fieldDescriptor);
        }
    }

    private Map<String, FieldDescriptor> getInstanceFieldToFieldDescriptorMap() {
        return instanceFieldToFieldDescriptorMap;
    }

    public PythonLikeType unifyWith(PythonLikeType other) {
        Optional<PythonLikeType> maybeCommonType = other.getAssignableTypesStream().filter(otherType -> {
            if (otherType.isSubclassOf(this)) {
                return true;
            }
            return this.isSubclassOf(otherType);
        }).findAny();

        if (maybeCommonType.isPresent() && maybeCommonType.get() != BuiltinTypes.BASE_TYPE) {
            PythonLikeType commonType = maybeCommonType.get();
            if (commonType.isSubclassOf(this)) {
                return this;
            } else {
                return commonType;
            }
        }

        for (PythonLikeType parent : getParentList()) {
            PythonLikeType parentUnification = parent.unifyWith(other);
            if (parentUnification != BuiltinTypes.BASE_TYPE) {
                return parentUnification;
            }
        }
        return BuiltinTypes.BASE_TYPE;
    }

    public boolean isSubclassOf(PythonLikeType type) {
        return isSubclassOf(type, new HashSet<>());
    }

    private Stream<PythonLikeType> getAssignableTypesStream() {
        return Stream.concat(
                Stream.of(this),
                getParentList().stream()
                        .flatMap(PythonLikeType::getAssignableTypesStream))
                .distinct();
    }

    private boolean isSubclassOf(PythonLikeType type, Set<PythonLikeType> visited) {
        if (visited.contains(this)) {
            return false;
        }

        if (this == type) {
            return true;
        }

        visited.add(this);
        for (PythonLikeType parent : PARENT_TYPES) {
            if (parent.isSubclassOf(type, visited)) {
                return true;
            }
        }
        return false;
    }

    public int getDepth() {
        if (PARENT_TYPES.size() == 0) {
            return 0;
        } else {
            return 1 + PARENT_TYPES.stream().map(PythonLikeType::getDepth).max(Comparator.naturalOrder()).get();
        }
    }

    @Override
    public PythonLikeObject $call(List<PythonLikeObject> positionalArguments,
            Map<PythonString, PythonLikeObject> namedArguments, PythonLikeObject callerInstance) {
        return constructor.$call(positionalArguments, namedArguments, null);
    }

    public PythonLikeObject loadMethod(String methodName) {
        PythonLikeObject out = __getAttributeOrNull(methodName);
        if (out == null) {
            return null;
        }

        if (out.__getType() == BuiltinTypes.FUNCTION_TYPE) {
            return out;
        }

        return null;
        //if (out.__getType() == PythonLikeFunction.getClassFunctionType()) {
        //    return FunctionBuiltinOperations.bindFunctionToType((PythonLikeFunction) out, null, this);
        //} else {
        //    return null;
        //}
    }

    public PythonLikeType getDefiningTypeOrNull(String attributeName) {
        if (__dir__.containsKey(attributeName) &&
                (this == BuiltinTypes.BASE_TYPE
                        || (__dir__.get(attributeName).getClass() != BuiltinTypes.BASE_TYPE.__dir__.get(attributeName)
                                .getClass()))) {
            return this;
        }

        for (PythonLikeType parent : PARENT_TYPES) {
            PythonLikeType out = parent.getDefiningTypeOrNull(attributeName);
            if (out != null) {
                return out;
            }
        }
        return null;
    }

    public PythonLikeObject __getAttributeOrNull(String attributeName) {
        PythonLikeObject out = __dir__.get(attributeName);
        if (out == null) {
            for (PythonLikeType type : PARENT_TYPES) {
                out = type.__getAttributeOrNull(attributeName);
                if (out != null) {
                    return out;
                }
            }
            return null;
        } else {
            return out;
        }
    }

    @Override
    public void __setAttribute(String attributeName, PythonLikeObject value) {
        __dir__.put(attributeName, value);
    }

    @Override
    public void __deleteAttribute(String attributeName) {
        // TODO: Descriptors: https://docs.python.org/3/howto/descriptor.html
        __dir__.remove(attributeName);
    }

    @Override
    public PythonLikeType __getType() {
        return new PythonLikeGenericType(this);
    }

    public String getTypeName() {
        return TYPE_NAME;
    }

    public String getJavaTypeInternalName() {
        return JAVA_TYPE_INTERNAL_NAME;
    }

    public String getJavaTypeDescriptor() {
        return "L" + JAVA_TYPE_INTERNAL_NAME + ";";
    }

    /**
     * Return the Java class corresponding to this type, if it exists. Throws {@link ClassNotFoundException} otherwise.
     */
    public Class<?> getJavaClass() throws ClassNotFoundException {
        return Class.forName(JAVA_TYPE_INTERNAL_NAME.replace('/', '.'), true,
                BuiltinTypes.asmClassLoader);
    }

    public List<PythonLikeType> getParentList() {
        return PARENT_TYPES;
    }

    @Override
    public String toString() {
        return "<class " + TYPE_NAME + ">";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !PythonLikeType.class.isAssignableFrom(o.getClass())) {
            return false;
        }
        PythonLikeType that = (PythonLikeType) o;
        return JAVA_TYPE_INTERNAL_NAME.equals(that.JAVA_TYPE_INTERNAL_NAME);
    }

    @Override
    public int hashCode() {
        return Objects.hash(JAVA_TYPE_INTERNAL_NAME);
    }
}
