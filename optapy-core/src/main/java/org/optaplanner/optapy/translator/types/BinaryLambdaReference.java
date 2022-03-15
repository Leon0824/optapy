package org.optaplanner.optapy.translator.types;

import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.optaplanner.optapy.PythonLikeObject;

public class BinaryLambdaReference implements PythonLikeFunction {
    private final BiFunction<PythonLikeObject, PythonLikeObject, PythonLikeObject> lambda;
    private final Map<String, Integer> parameterNameToIndexMap;

    public BinaryLambdaReference(BiFunction<PythonLikeObject, PythonLikeObject, PythonLikeObject> lambda, Map<String, Integer> parameterNameToIndexMap) {
        this.lambda = lambda;
        this.parameterNameToIndexMap = parameterNameToIndexMap;
    }

    @Override
    public PythonLikeObject __call__(List<PythonLikeObject> positionalArguments, Map<String, PythonLikeObject> namedArguments) {
        PythonLikeObject[] args = new PythonLikeObject[2];
        for (int i = 0; i < positionalArguments.size(); i++) {
            args[i] = positionalArguments.get(i);
        }

        if (namedArguments != null) {
            for (String key : namedArguments.keySet()) {
                args[parameterNameToIndexMap.get(key)] = namedArguments.get(key);
            }
        }

        return lambda.apply(args[0], args[1]);
    }
}
