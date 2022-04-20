package org.optaplanner.optapy.translator;

/**
 * The list of all Python Ternary Operators, which take
 * self and two other arguments.
 *
 * ex: a.__setitem__(key, value)
 */
public enum PythonTernaryOperators {
    // Descriptor operations
    // https://docs.python.org/3/howto/descriptor.html
    GET("__get__"),
    SET("__set__"),

    // List operations
    // https://docs.python.org/3/reference/datamodel.html#object.__setitem__
    SET_ITEM("__setitem__");

    final String dunderMethod;

    PythonTernaryOperators(String dunderMethod) {
        this.dunderMethod = dunderMethod;
    }

    public String getDunderMethod() {
        return dunderMethod;
    }
}
