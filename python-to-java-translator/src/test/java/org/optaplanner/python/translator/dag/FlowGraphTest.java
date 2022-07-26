package org.optaplanner.python.translator.dag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.optaplanner.python.translator.types.PythonBoolean.BOOLEAN_TYPE;
import static org.optaplanner.python.translator.types.PythonInteger.INT_TYPE;
import static org.optaplanner.python.translator.types.PythonIterator.ITERATOR_TYPE;
import static org.optaplanner.python.translator.types.PythonLikeTuple.TUPLE_TYPE;
import static org.optaplanner.python.translator.types.PythonLikeType.TYPE_TYPE;
import static org.optaplanner.python.translator.types.PythonNone.NONE_TYPE;
import static org.optaplanner.python.translator.types.PythonString.STRING_TYPE;
import static org.optaplanner.python.translator.types.errors.PythonAssertionError.ASSERTION_ERROR_TYPE;
import static org.optaplanner.python.translator.types.errors.PythonBaseException.BASE_EXCEPTION_TYPE;
import static org.optaplanner.python.translator.types.errors.PythonTraceback.TRACEBACK_TYPE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.optaplanner.python.translator.CompareOp;
import org.optaplanner.python.translator.FunctionMetadata;
import org.optaplanner.python.translator.OpcodeIdentifier;
import org.optaplanner.python.translator.PythonBytecodeInstruction;
import org.optaplanner.python.translator.PythonBytecodeToJavaBytecodeTranslator;
import org.optaplanner.python.translator.PythonCompiledFunction;
import org.optaplanner.python.translator.StackMetadata;
import org.optaplanner.python.translator.opcodes.Opcode;
import org.optaplanner.python.translator.types.PythonLikeType;
import org.optaplanner.python.translator.types.errors.PythonAssertionError;
import org.optaplanner.python.translator.types.errors.StopIteration;
import org.optaplanner.python.translator.util.PythonFunctionBuilder;

public class FlowGraphTest {

    private static PythonLikeType OBJECT_TYPE = PythonLikeType.getBaseType();

    static FlowGraph getFlowGraph(FunctionMetadata functionMetadata, StackMetadata initialStackMetadata,
            PythonCompiledFunction function) {
        List<Opcode> out = new ArrayList<>(function.instructionList.size());
        for (PythonBytecodeInstruction instruction : function.instructionList) {
            out.add(Opcode.lookupOpcodeForInstruction(instruction, Integer.MAX_VALUE));
        }
        return FlowGraph.createFlowGraph(functionMetadata, initialStackMetadata, out);
    }

    static FunctionMetadata getFunctionMetadata(PythonCompiledFunction function) {
        FunctionMetadata out = new FunctionMetadata();
        out.functionType = PythonBytecodeToJavaBytecodeTranslator.getFunctionType(function);
        out.className = FlowGraphTest.class.getName();
        out.pythonCompiledFunction = function;
        out.bytecodeCounterToLabelMap = new HashMap<>();
        out.bytecodeCounterToCodeArgumenterList = new HashMap<>();
        return out;
    }

    static StackMetadata getInitialStackMetadata(int locals, int cells) {
        StackMetadata initialStackMetadata = new StackMetadata();
        initialStackMetadata.stackValueSources = new ArrayList<>();
        initialStackMetadata.localVariableValueSources = new ArrayList<>(locals);
        initialStackMetadata.cellVariableValueSources = new ArrayList<>(cells);

        for (int i = 0; i < locals; i++) {
            initialStackMetadata.localVariableValueSources.add(null);
        }

        for (int i = 0; i < cells; i++) {
            initialStackMetadata.cellVariableValueSources.add(null);
        }

        return initialStackMetadata;
    }

    static List<FrameData> getFrameData(FlowGraph flowGraph) {
        List<StackMetadata> stackMetadataList = flowGraph.getStackMetadataForOperations();
        List<FrameData> out = new ArrayList<>(stackMetadataList.size());

        for (int i = 0; i < stackMetadataList.size(); i++) {
            out.add(FrameData.from(i, stackMetadataList.get(i)));
        }

        return out;
    }

    @Test
    public void testStackMetadataForBasicOps() {
        PythonCompiledFunction pythonCompiledFunction = PythonFunctionBuilder.newFunction()
                .loadConstant(1)
                .loadConstant("Hi")
                .op(OpcodeIdentifier.ROT_TWO)
                .tuple(2)
                .op(OpcodeIdentifier.RETURN_VALUE)
                .build();

        FunctionMetadata functionMetadata = getFunctionMetadata(pythonCompiledFunction);
        StackMetadata metadata = getInitialStackMetadata(0, 0);
        FlowGraph flowGraph = getFlowGraph(functionMetadata, metadata, pythonCompiledFunction);
        List<FrameData> stackMetadataList = getFrameData(flowGraph);

        assertThat(stackMetadataList).containsExactly(
                new FrameData(0),
                new FrameData(1).stack(INT_TYPE),
                new FrameData(2).stack(INT_TYPE, STRING_TYPE),
                new FrameData(3).stack(STRING_TYPE, INT_TYPE),
                new FrameData(4).stack(TUPLE_TYPE));
    }

    @Test
    public void testStackMetadataForLocalVariables() {
        PythonCompiledFunction pythonCompiledFunction = PythonFunctionBuilder.newFunction()
                .loadConstant(1)
                .storeVariable("one")
                .loadConstant("2")
                .storeVariable("two")
                .loadVariable("one")
                .loadVariable("two")
                .tuple(2)
                .op(OpcodeIdentifier.RETURN_VALUE)
                .build();

        FunctionMetadata functionMetadata = getFunctionMetadata(pythonCompiledFunction);
        StackMetadata metadata = getInitialStackMetadata(2, 0);
        FlowGraph flowGraph = getFlowGraph(functionMetadata, metadata, pythonCompiledFunction);
        List<FrameData> stackMetadataList = getFrameData(flowGraph);

        assertThat(stackMetadataList).containsExactly(
                new FrameData(0).locals(null, null),
                new FrameData(1).stack(INT_TYPE).locals(null, null),
                new FrameData(2).stack().locals(INT_TYPE, null),
                new FrameData(3).stack(STRING_TYPE).locals(INT_TYPE, null),
                new FrameData(4).stack().locals(INT_TYPE, STRING_TYPE),
                new FrameData(5).stack(INT_TYPE).locals(INT_TYPE, STRING_TYPE),
                new FrameData(6).stack(INT_TYPE, STRING_TYPE).locals(INT_TYPE, STRING_TYPE),
                new FrameData(7).stack(TUPLE_TYPE).locals(INT_TYPE, STRING_TYPE));
    }

    @Test
    public void testStackMetadataForLoops() {
        PythonCompiledFunction pythonCompiledFunction = PythonFunctionBuilder.newFunction()
                .loadConstant(0)
                .storeVariable("sum")
                .loadConstant(1)
                .loadConstant(2)
                .loadConstant(3)
                .tuple(3)
                .op(OpcodeIdentifier.GET_ITER)
                .loop(block -> {
                    block.loadVariable("sum");
                    block.op(OpcodeIdentifier.BINARY_ADD);
                    block.storeVariable("sum");
                })
                .loadVariable("sum")
                .op(OpcodeIdentifier.RETURN_VALUE)
                .build();

        FunctionMetadata functionMetadata = getFunctionMetadata(pythonCompiledFunction);
        StackMetadata metadata = getInitialStackMetadata(1, 0);
        FlowGraph flowGraph = getFlowGraph(functionMetadata, metadata, pythonCompiledFunction);
        List<FrameData> stackMetadataList = getFrameData(flowGraph);

        assertThat(stackMetadataList).containsExactly(
                new FrameData(0).locals((PythonLikeType) null), // LOAD_CONSTANT
                new FrameData(1).stack(INT_TYPE).locals((PythonLikeType) null), // STORE
                new FrameData(2).stack().locals(INT_TYPE), // LOAD_CONSTANT
                new FrameData(3).stack(INT_TYPE).locals(INT_TYPE), // LOAD_CONSTANT
                new FrameData(4).stack(INT_TYPE, INT_TYPE).locals(INT_TYPE), // LOAD_CONSTANT
                new FrameData(5).stack(INT_TYPE, INT_TYPE, INT_TYPE).locals(INT_TYPE), // TUPLE(3)

                // Type information is lost because Tuple is not generic
                new FrameData(6).stack(TUPLE_TYPE).locals(INT_TYPE), // ITERATOR
                new FrameData(7).stack(ITERATOR_TYPE).locals(OBJECT_TYPE), // NEXT
                new FrameData(8).stack(ITERATOR_TYPE, OBJECT_TYPE).locals(OBJECT_TYPE), // LOAD_VAR
                new FrameData(9).stack(ITERATOR_TYPE, OBJECT_TYPE, OBJECT_TYPE).locals(OBJECT_TYPE), // ADD
                new FrameData(10).stack(ITERATOR_TYPE, OBJECT_TYPE).locals(OBJECT_TYPE), // STORE
                new FrameData(11).stack(ITERATOR_TYPE).locals(OBJECT_TYPE), // JUMP_ABS
                new FrameData(12).stack().locals(OBJECT_TYPE), // NOP
                new FrameData(13).stack().locals(OBJECT_TYPE), // LOAD_VAR
                new FrameData(14).stack(OBJECT_TYPE).locals(OBJECT_TYPE) // RETURN
        );
    }

    @Test
    public void testStackMetadataForExceptions() {
        PythonCompiledFunction pythonCompiledFunction = PythonFunctionBuilder.newFunction()
                .tryCode(code -> {
                    code.loadConstant(5)
                            .loadConstant(5)
                            .compare(CompareOp.LESS_THAN)
                            .ifTrue(block -> {
                                block.loadConstant("Try").op(OpcodeIdentifier.RETURN_VALUE);
                            })
                            .op(OpcodeIdentifier.LOAD_ASSERTION_ERROR)
                            .op(OpcodeIdentifier.RAISE_VARARGS, 1);
                }, true)
                .except(PythonAssertionError.ASSERTION_ERROR_TYPE, except -> {
                    except.loadConstant("Assert").op(OpcodeIdentifier.RETURN_VALUE);
                }, true)
                .tryEnd()
                .build();

        FunctionMetadata functionMetadata = getFunctionMetadata(pythonCompiledFunction);
        StackMetadata metadata = getInitialStackMetadata(0, 0);
        FlowGraph flowGraph = getFlowGraph(functionMetadata, metadata, pythonCompiledFunction);
        List<FrameData> stackMetadataList = getFrameData(flowGraph);

        assertThat(stackMetadataList).containsExactly(
                new FrameData(0).stack(), // SETUP_TRY
                new FrameData(1).stack(), // SETUP_TRY
                new FrameData(2).stack(), // LOAD_CONSTANT
                new FrameData(3).stack(INT_TYPE), // LOAD_CONSTANT
                new FrameData(4).stack(INT_TYPE, INT_TYPE), // COMPARE
                new FrameData(5).stack(BOOLEAN_TYPE), // POP_JUMP_IF_TRUE
                new FrameData(6).stack(), // LOAD_CONSTANT
                new FrameData(7).stack(STRING_TYPE), // RETURN
                new FrameData(8).stack(), // NOP
                new FrameData(9).stack(), // LOAD_ASSERTION_ERROR
                new FrameData(10).stack(ASSERTION_ERROR_TYPE), // RAISE
                new FrameData(11).stack(NONE_TYPE, INT_TYPE, NONE_TYPE, TRACEBACK_TYPE, BASE_EXCEPTION_TYPE, TYPE_TYPE), // except handler; DUP_TOP,
                new FrameData(12).stack(NONE_TYPE, INT_TYPE, NONE_TYPE, TRACEBACK_TYPE, BASE_EXCEPTION_TYPE, TYPE_TYPE,
                        TYPE_TYPE), // LOAD_CONSTANT
                new FrameData(13).stack(NONE_TYPE, INT_TYPE, NONE_TYPE, TRACEBACK_TYPE, BASE_EXCEPTION_TYPE, TYPE_TYPE,
                        TYPE_TYPE,
                        TYPE_TYPE), // JUMP_IF_NOT_EXC_MATCH
                new FrameData(14).stack(NONE_TYPE, INT_TYPE, NONE_TYPE, TRACEBACK_TYPE, BASE_EXCEPTION_TYPE, TYPE_TYPE), // POP_TOP
                new FrameData(15).stack(NONE_TYPE, INT_TYPE, NONE_TYPE, TRACEBACK_TYPE, BASE_EXCEPTION_TYPE), // POP_TOP
                new FrameData(16).stack(NONE_TYPE, INT_TYPE, NONE_TYPE, TRACEBACK_TYPE), // POP_TOP
                new FrameData(17).stack(NONE_TYPE, INT_TYPE, NONE_TYPE), // POP_EXCEPT
                new FrameData(18).stack(), // LOAD_CONSTANT
                new FrameData(19).stack(STRING_TYPE), // RETURN
                new FrameData(20).stack(NONE_TYPE, INT_TYPE, NONE_TYPE, TRACEBACK_TYPE, BASE_EXCEPTION_TYPE,
                        TYPE_TYPE), // POP_TOP
                new FrameData(21).stack(NONE_TYPE, INT_TYPE, NONE_TYPE, TRACEBACK_TYPE, BASE_EXCEPTION_TYPE, TYPE_TYPE), // POP_TOP
                new FrameData(22).stack(NONE_TYPE, INT_TYPE, NONE_TYPE, TRACEBACK_TYPE, BASE_EXCEPTION_TYPE), // RERAISE
                new FrameData(23).stack(NONE_TYPE, INT_TYPE, NONE_TYPE, TRACEBACK_TYPE, BASE_EXCEPTION_TYPE, TYPE_TYPE) // RERAISE
        );
    }

    @Test
    public void testStackMetadataForTryFinally() {
        PythonCompiledFunction pythonCompiledFunction = PythonFunctionBuilder.newFunction()
                .tryCode(code -> {
                    code.loadConstant(1)
                            .loadConstant(1)
                            .compare(CompareOp.EQUALS)
                            .ifTrue(block -> {
                                block.op(OpcodeIdentifier.LOAD_ASSERTION_ERROR)
                                        .op(OpcodeIdentifier.RAISE_VARARGS, 1);
                            })
                            .loadConstant(1)
                            .loadConstant(2)
                            .compare(CompareOp.EQUALS)
                            .ifTrue(block -> {
                                block.loadConstant(new StopIteration())
                                        .op(OpcodeIdentifier.RAISE_VARARGS, 1);
                            });
                }, false)
                .except(PythonAssertionError.ASSERTION_ERROR_TYPE, except -> {
                    except.loadConstant("Assert").storeGlobalVariable("exception");
                }, false)
                .andFinally(code -> {
                    code.loadConstant("Finally")
                            .storeGlobalVariable("finally");
                }, false)
                .tryEnd()
                .loadConstant(1)
                .op(OpcodeIdentifier.RETURN_VALUE)
                .build();

        FunctionMetadata functionMetadata = getFunctionMetadata(pythonCompiledFunction);
        StackMetadata metadata = getInitialStackMetadata(0, 0);
        FlowGraph flowGraph = getFlowGraph(functionMetadata, metadata, pythonCompiledFunction);
        List<FrameData> stackMetadataList = getFrameData(flowGraph);

        assertThat(stackMetadataList).containsExactly(
                new FrameData(0).stack(), // SETUP_TRY
                new FrameData(1).stack(), // SETUP_TRY
                new FrameData(2).stack(), // LOAD_CONSTANT
                new FrameData(3).stack(INT_TYPE), // LOAD_CONSTANT
                new FrameData(4).stack(INT_TYPE, INT_TYPE), // COMPARE
                new FrameData(5).stack(BOOLEAN_TYPE), // POP_JUMP_IF_TRUE
                new FrameData(6).stack(), // LOAD_ASSERTION_ERROR
                new FrameData(7).stack(ASSERTION_ERROR_TYPE), // RAISE
                new FrameData(8).stack(), // NOP
                new FrameData(9).stack(), // LOAD_CONSTANT
                new FrameData(10).stack(INT_TYPE), // LOAD_CONSTANT
                new FrameData(11).stack(INT_TYPE, INT_TYPE), // COMPARE
                new FrameData(12).stack(BOOLEAN_TYPE), // POP_JUMP_IF_TRUE
                new FrameData(13).stack(), // LOAD_CONSTANT
                new FrameData(14).stack(StopIteration.STOP_ITERATION_TYPE), // RAISE
                new FrameData(15).stack(), // NOP
                new FrameData(16).stack(), // JUMP_ABSOLUTE
                new FrameData(17).stack(NONE_TYPE, INT_TYPE, NONE_TYPE, TRACEBACK_TYPE, BASE_EXCEPTION_TYPE, TYPE_TYPE), // except handler; DUP_TOP,
                new FrameData(18).stack(NONE_TYPE, INT_TYPE, NONE_TYPE, TRACEBACK_TYPE, BASE_EXCEPTION_TYPE, TYPE_TYPE,
                        TYPE_TYPE), // LOAD_CONSTANT
                new FrameData(19).stack(NONE_TYPE, INT_TYPE, NONE_TYPE, TRACEBACK_TYPE, BASE_EXCEPTION_TYPE, TYPE_TYPE,
                        TYPE_TYPE,
                        TYPE_TYPE), // JUMP_IF_NOT_EXC_MATCH
                new FrameData(20).stack(NONE_TYPE, INT_TYPE, NONE_TYPE, TRACEBACK_TYPE, BASE_EXCEPTION_TYPE, TYPE_TYPE), // POP_TOP
                new FrameData(21).stack(NONE_TYPE, INT_TYPE, NONE_TYPE, TRACEBACK_TYPE, BASE_EXCEPTION_TYPE), // POP_TOP
                new FrameData(22).stack(NONE_TYPE, INT_TYPE, NONE_TYPE, TRACEBACK_TYPE), // POP_TOP
                new FrameData(23).stack(NONE_TYPE, INT_TYPE, NONE_TYPE), // POP_EXCEPT
                new FrameData(24).stack(), // LOAD_CONSTANT
                new FrameData(25).stack(STRING_TYPE), // STORE_GLOBAL
                new FrameData(26).stack(), // JUMP_ABSOLUTE
                new FrameData(27).stack(NONE_TYPE, INT_TYPE, NONE_TYPE, TRACEBACK_TYPE, BASE_EXCEPTION_TYPE, TYPE_TYPE), // RERAISE
                new FrameData(28).stack(NONE_TYPE, INT_TYPE, NONE_TYPE, TRACEBACK_TYPE, BASE_EXCEPTION_TYPE, TYPE_TYPE), // POP_TOP
                new FrameData(29).stack(NONE_TYPE, INT_TYPE, NONE_TYPE, TRACEBACK_TYPE, BASE_EXCEPTION_TYPE), // POP_TOP
                new FrameData(30).stack(), // POP_TOP
                new FrameData(31).stack(), // Load constant
                new FrameData(32).stack(STRING_TYPE), // STORE
                new FrameData(33).stack(), // JUMP_ABSOLUTE
                new FrameData(34).stack(NONE_TYPE, INT_TYPE, NONE_TYPE, TRACEBACK_TYPE, BASE_EXCEPTION_TYPE, TYPE_TYPE), // NO-OP; Uncaught exception handler
                new FrameData(35).stack(NONE_TYPE, INT_TYPE, NONE_TYPE, TRACEBACK_TYPE, BASE_EXCEPTION_TYPE, TYPE_TYPE), // LOAD-CONSTANT
                new FrameData(36).stack(NONE_TYPE, INT_TYPE, NONE_TYPE, TRACEBACK_TYPE, BASE_EXCEPTION_TYPE, TYPE_TYPE,
                        STRING_TYPE), // STORE
                new FrameData(37).stack(NONE_TYPE, INT_TYPE, NONE_TYPE, TRACEBACK_TYPE, BASE_EXCEPTION_TYPE, TYPE_TYPE), //  POP-TOP
                new FrameData(38).stack(NONE_TYPE, INT_TYPE, NONE_TYPE, TRACEBACK_TYPE, BASE_EXCEPTION_TYPE), // RERAISE
                new FrameData(39).stack(), // NO-OP; After try
                new FrameData(40).stack(), // LOAD_CONSTANT
                new FrameData(41).stack(INT_TYPE) // RETURN
        );
    }

    @Test
    public void testStackMetadataForIfStatementsThatExitEarly() {
        PythonCompiledFunction pythonCompiledFunction = PythonFunctionBuilder.newFunction()
                .loadConstant(5)
                .storeVariable("a")
                .loadVariable("a")
                .loadConstant(5)
                .compare(CompareOp.LESS_THAN)
                .ifTrue(block -> {
                    block.loadConstant("10");
                    block.storeVariable("a");
                    block.loadVariable("a");
                    block.op(OpcodeIdentifier.RETURN_VALUE);
                })
                .loadConstant(-10)
                .op(OpcodeIdentifier.RETURN_VALUE)
                .build();

        FunctionMetadata functionMetadata = getFunctionMetadata(pythonCompiledFunction);
        StackMetadata metadata = getInitialStackMetadata(1, 0);
        FlowGraph flowGraph = getFlowGraph(functionMetadata, metadata, pythonCompiledFunction);
        List<FrameData> stackMetadataList = getFrameData(flowGraph);

        assertThat(stackMetadataList).containsExactly(
                new FrameData(0).stack().locals((PythonLikeType) null), // LOAD_CONSTANT
                new FrameData(1).stack(INT_TYPE).locals((PythonLikeType) null), // STORE
                new FrameData(2).stack().locals(INT_TYPE), // LOAD_VARIABLE
                new FrameData(3).stack(INT_TYPE).locals(INT_TYPE), // LOAD_CONSTANT
                new FrameData(4).stack(INT_TYPE, INT_TYPE).locals(INT_TYPE), // COMPARE_OP
                new FrameData(5).stack(BOOLEAN_TYPE).locals(INT_TYPE), // POP_JUMP_IF_TRUE
                new FrameData(6).stack().locals(INT_TYPE), // LOAD_CONSTANT
                new FrameData(7).stack(STRING_TYPE).locals(INT_TYPE), // STORE
                new FrameData(8).stack().locals(STRING_TYPE), // LOAD_VARIABLE
                new FrameData(9).stack(STRING_TYPE).locals(STRING_TYPE), // RETURN
                new FrameData(10).stack().locals(INT_TYPE), // NOP
                new FrameData(11).stack().locals(INT_TYPE), // LOAD_CONSTANT
                new FrameData(12).stack(INT_TYPE).locals(INT_TYPE) // RETURN
        );
    }

    @Test
    public void testStackMetadataForIfStatementsThatDoNotExitEarly() {
        PythonCompiledFunction pythonCompiledFunction = PythonFunctionBuilder.newFunction()
                .loadConstant(5)
                .storeVariable("a")
                .loadVariable("a")
                .loadConstant(5)
                .compare(CompareOp.LESS_THAN)
                .ifTrue(block -> {
                    block.loadConstant("10");
                    block.storeVariable("a");
                })
                .loadConstant(-10)
                .op(OpcodeIdentifier.RETURN_VALUE)
                .build();

        FunctionMetadata functionMetadata = getFunctionMetadata(pythonCompiledFunction);
        StackMetadata metadata = getInitialStackMetadata(1, 0);
        FlowGraph flowGraph = getFlowGraph(functionMetadata, metadata, pythonCompiledFunction);
        List<FrameData> stackMetadataList = getFrameData(flowGraph);

        assertThat(stackMetadataList).containsExactly(
                new FrameData(0).stack().locals((PythonLikeType) null), // LOAD_CONSTANT
                new FrameData(1).stack(INT_TYPE).locals((PythonLikeType) null), // STORE
                new FrameData(2).stack().locals(INT_TYPE), // LOAD_VARIABLE
                new FrameData(3).stack(INT_TYPE).locals(INT_TYPE), // LOAD_CONSTANT
                new FrameData(4).stack(INT_TYPE, INT_TYPE).locals(INT_TYPE), // COMPARE_OP
                new FrameData(5).stack(BOOLEAN_TYPE).locals(INT_TYPE), // POP_JUMP_IF_TRUE
                new FrameData(6).stack().locals(INT_TYPE), // LOAD_CONSTANT
                new FrameData(7).stack(STRING_TYPE).locals(INT_TYPE), // STORE
                new FrameData(8).stack().locals(OBJECT_TYPE), // NOP
                new FrameData(9).stack().locals(OBJECT_TYPE), // LOAD_CONSTANT
                new FrameData(10).stack(INT_TYPE).locals(OBJECT_TYPE) // RETURN
        );
    }

    private static class FrameData {
        int index;
        List<PythonLikeType> stackTypes;
        List<PythonLikeType> localVariableTypes;
        List<PythonLikeType> cellTypes;

        public FrameData(int index) {
            this.index = index;
            stackTypes = new ArrayList<>();
            localVariableTypes = new ArrayList<>();
            cellTypes = new ArrayList<>();
        }

        public static FrameData from(int index, StackMetadata stackMetadata) {
            FrameData out = new FrameData(index);
            stackMetadata.stackValueSources.forEach(valueSourceInfo -> {
                if (valueSourceInfo != null) {
                    out.stackTypes.add(valueSourceInfo.getValueType());
                } else {
                    out.stackTypes.add(null);
                }
            });
            stackMetadata.localVariableValueSources.forEach(valueSourceInfo -> {
                if (valueSourceInfo != null) {
                    out.localVariableTypes.add(valueSourceInfo.getValueType());
                } else {
                    out.localVariableTypes.add(null);
                }
            });
            stackMetadata.cellVariableValueSources.forEach(valueSourceInfo -> {
                if (valueSourceInfo != null) {
                    out.cellTypes.add(valueSourceInfo.getValueType());
                } else {
                    out.cellTypes.add(null);
                }
            });
            return out;
        }

        public FrameData copy() {
            FrameData out = new FrameData(index);
            out.stackTypes.addAll(stackTypes);
            out.localVariableTypes.addAll(localVariableTypes);
            out.cellTypes.addAll(cellTypes);
            return out;
        }

        public FrameData stack(PythonLikeType... valueTypes) {
            FrameData out = copy();
            out.stackTypes.addAll(Arrays.asList(valueTypes));
            return out;
        }

        public FrameData locals(PythonLikeType... valueTypes) {
            FrameData out = copy();
            out.localVariableTypes.addAll(Arrays.asList(valueTypes));
            return out;
        }

        public FrameData cells(PythonLikeType... valueTypes) {
            FrameData out = copy();
            out.cellTypes.addAll(Arrays.asList(valueTypes));
            return out;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            FrameData frameData = (FrameData) o;
            return index == frameData.index
                    && Objects.equals(stackTypes, frameData.stackTypes)
                    && Objects.equals(localVariableTypes, frameData.localVariableTypes)
                    && Objects.equals(cellTypes, frameData.cellTypes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(index, stackTypes, localVariableTypes, cellTypes);
        }

        @Override
        public String toString() {
            return "FrameData{" +
                    "index=" + index +
                    ", stackTypes=" + stackTypes +
                    ", localVariableTypes=" + localVariableTypes +
                    ", cellTypes=" + cellTypes +
                    '}';
        }
    }
}
