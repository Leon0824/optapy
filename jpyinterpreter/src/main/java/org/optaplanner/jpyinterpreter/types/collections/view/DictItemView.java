package org.optaplanner.jpyinterpreter.types.collections.view;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.optaplanner.jpyinterpreter.PythonBinaryOperators;
import org.optaplanner.jpyinterpreter.PythonLikeObject;
import org.optaplanner.jpyinterpreter.PythonOverloadImplementor;
import org.optaplanner.jpyinterpreter.PythonUnaryOperator;
import org.optaplanner.jpyinterpreter.builtins.UnaryDunderBuiltin;
import org.optaplanner.jpyinterpreter.types.AbstractPythonLikeObject;
import org.optaplanner.jpyinterpreter.types.BuiltinTypes;
import org.optaplanner.jpyinterpreter.types.PythonLikeType;
import org.optaplanner.jpyinterpreter.types.PythonString;
import org.optaplanner.jpyinterpreter.types.collections.PythonIterator;
import org.optaplanner.jpyinterpreter.types.collections.PythonLikeDict;
import org.optaplanner.jpyinterpreter.types.collections.PythonLikeSet;
import org.optaplanner.jpyinterpreter.types.collections.PythonLikeTuple;
import org.optaplanner.jpyinterpreter.types.numeric.PythonBoolean;
import org.optaplanner.jpyinterpreter.types.numeric.PythonInteger;
import org.optaplanner.jpyinterpreter.util.IteratorUtils;

public class DictItemView extends AbstractPythonLikeObject {
    public final static PythonLikeType $TYPE = BuiltinTypes.DICT_ITEM_VIEW_TYPE;

    final PythonLikeDict mapping;
    final Set<Map.Entry<PythonLikeObject, PythonLikeObject>> entrySet;

    static {
        PythonOverloadImplementor.deferDispatchesFor(DictItemView::registerMethods);
    }

    private static PythonLikeType registerMethods() throws NoSuchMethodException {
        // Unary
        BuiltinTypes.DICT_ITEM_VIEW_TYPE.addUnaryMethod(PythonUnaryOperator.LENGTH,
                DictItemView.class.getMethod("getItemsSize"));
        BuiltinTypes.DICT_ITEM_VIEW_TYPE.addUnaryMethod(PythonUnaryOperator.ITERATOR,
                DictItemView.class.getMethod("getItemsIterator"));
        BuiltinTypes.DICT_ITEM_VIEW_TYPE.addUnaryMethod(PythonUnaryOperator.REVERSED,
                DictItemView.class.getMethod("getReversedItemIterator"));
        BuiltinTypes.DICT_ITEM_VIEW_TYPE.addUnaryMethod(PythonUnaryOperator.AS_STRING,
                DictItemView.class.getMethod("toRepresentation"));
        BuiltinTypes.DICT_ITEM_VIEW_TYPE.addUnaryMethod(PythonUnaryOperator.REPRESENTATION,
                DictItemView.class.getMethod("toRepresentation"));

        // Binary
        BuiltinTypes.DICT_ITEM_VIEW_TYPE.addBinaryMethod(PythonBinaryOperators.CONTAINS,
                DictItemView.class.getMethod("containsItem", PythonLikeObject.class));

        // Set methods
        BuiltinTypes.DICT_ITEM_VIEW_TYPE.addMethod("isdisjoint",
                DictItemView.class.getMethod("isDisjoint", DictItemView.class));
        BuiltinTypes.DICT_ITEM_VIEW_TYPE.addBinaryMethod(PythonBinaryOperators.LESS_THAN_OR_EQUAL,
                DictItemView.class.getMethod("isSubset", DictItemView.class));
        BuiltinTypes.DICT_ITEM_VIEW_TYPE.addBinaryMethod(PythonBinaryOperators.LESS_THAN,
                DictItemView.class.getMethod("isStrictSubset", DictItemView.class));
        BuiltinTypes.DICT_ITEM_VIEW_TYPE.addBinaryMethod(PythonBinaryOperators.GREATER_THAN_OR_EQUAL,
                DictItemView.class.getMethod("isSuperset", DictItemView.class));
        BuiltinTypes.DICT_ITEM_VIEW_TYPE.addBinaryMethod(PythonBinaryOperators.GREATER_THAN,
                DictItemView.class.getMethod("isStrictSuperset", DictItemView.class));
        BuiltinTypes.DICT_ITEM_VIEW_TYPE.addBinaryMethod(PythonBinaryOperators.OR,
                DictItemView.class.getMethod("union", DictItemView.class));
        BuiltinTypes.DICT_ITEM_VIEW_TYPE.addBinaryMethod(PythonBinaryOperators.AND,
                DictItemView.class.getMethod("intersection", DictItemView.class));
        BuiltinTypes.DICT_ITEM_VIEW_TYPE.addBinaryMethod(PythonBinaryOperators.SUBTRACT,
                DictItemView.class.getMethod("difference", DictItemView.class));
        BuiltinTypes.DICT_ITEM_VIEW_TYPE.addBinaryMethod(PythonBinaryOperators.XOR,
                DictItemView.class.getMethod("symmetricDifference", DictItemView.class));

        return BuiltinTypes.DICT_ITEM_VIEW_TYPE;
    }

    public DictItemView(PythonLikeDict mapping) {
        super(BuiltinTypes.DICT_ITEM_VIEW_TYPE);
        this.mapping = mapping;
        this.entrySet = mapping.delegate.entrySet();
        __setAttribute("mapping", mapping);
    }

    private List<PythonLikeObject> getEntriesAsTuples() {
        List<PythonLikeObject> out = new ArrayList<>(entrySet.size());
        for (Map.Entry<PythonLikeObject, PythonLikeObject> entry : entrySet) {
            out.add(PythonLikeTuple.fromList(List.of(entry.getKey(), entry.getValue())));
        }
        return out;
    }

    public PythonInteger getItemsSize() {
        return PythonInteger.valueOf(entrySet.size());
    }

    public PythonIterator<PythonLikeObject> getItemsIterator() {
        return new PythonIterator<>(
                IteratorUtils.iteratorMap(entrySet.iterator(),
                        entry -> PythonLikeTuple.fromList(List.of(entry.getKey(), entry.getValue()))));
    }

    public PythonBoolean containsItem(PythonLikeObject o) {
        if (o instanceof PythonLikeTuple) {
            PythonLikeTuple item = (PythonLikeTuple) o;
            if (item.size() != 2) {
                return PythonBoolean.FALSE;
            }
            Map.Entry<PythonLikeObject, PythonLikeObject> entry = new AbstractMap.SimpleEntry<>(item.get(0), item.get(1));
            return PythonBoolean.valueOf(entrySet.contains(entry));
        } else {
            return PythonBoolean.FALSE;
        }
    }

    public PythonIterator<PythonLikeObject> getReversedItemIterator() {
        return new PythonIterator<>(IteratorUtils.iteratorMap(mapping.reversed(),
                key -> PythonLikeTuple.fromList(List.of(key, mapping.delegate.get(key)))));
    }

    public PythonBoolean isDisjoint(DictItemView other) {
        return PythonBoolean.valueOf(Collections.disjoint(entrySet, other.entrySet));
    }

    public PythonBoolean isSubset(DictItemView other) {
        return PythonBoolean.valueOf(other.entrySet.containsAll(entrySet));
    }

    public PythonBoolean isStrictSubset(DictItemView other) {
        return PythonBoolean.valueOf(other.entrySet.containsAll(entrySet) && !entrySet.containsAll(other.entrySet));
    }

    public PythonBoolean isSuperset(DictItemView other) {
        return PythonBoolean.valueOf(entrySet.containsAll(other.entrySet));
    }

    public PythonBoolean isStrictSuperset(DictItemView other) {
        return PythonBoolean.valueOf(entrySet.containsAll(other.entrySet) && !other.entrySet.containsAll(entrySet));
    }

    public PythonLikeSet union(DictItemView other) {
        PythonLikeSet out = new PythonLikeSet();
        out.delegate.addAll(getEntriesAsTuples());
        out.delegate.addAll(other.getEntriesAsTuples());
        return out;
    }

    public PythonLikeSet intersection(DictItemView other) {
        PythonLikeSet out = new PythonLikeSet();
        out.delegate.addAll(getEntriesAsTuples());
        out.delegate.retainAll(other.getEntriesAsTuples());
        return out;
    }

    public PythonLikeSet difference(DictItemView other) {
        PythonLikeSet out = new PythonLikeSet();
        out.delegate.addAll(getEntriesAsTuples());
        other.getEntriesAsTuples().forEach(out.delegate::remove);
        return out;
    }

    public PythonLikeSet symmetricDifference(DictItemView other) {
        PythonLikeSet out = new PythonLikeSet();
        out.delegate.addAll(getEntriesAsTuples());
        other.getEntriesAsTuples().stream() // for each item in other
                .filter(Predicate.not(out.delegate::add)) // add each item
                .forEach(out.delegate::remove); // add return false iff item already in set, so this remove
        // all items in both this and other
        return out;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof DictItemView) {
            DictItemView other = (DictItemView) o;
            return entrySet.equals(other.entrySet);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return entrySet.hashCode();
    }

    public PythonString toRepresentation() {
        return PythonString.valueOf(toString());
    }

    @Override
    public String toString() {
        StringBuilder out = new StringBuilder("dict_items([");

        for (Map.Entry<PythonLikeObject, PythonLikeObject> entry : entrySet) {
            out.append("(");
            out.append(UnaryDunderBuiltin.REPRESENTATION.invoke(entry.getKey()));
            out.append(", ");
            out.append(UnaryDunderBuiltin.REPRESENTATION.invoke(entry.getValue()));
            out.append("), ");
        }
        out.delete(out.length() - 2, out.length());
        out.append("])");

        return out.toString();
    }
}
