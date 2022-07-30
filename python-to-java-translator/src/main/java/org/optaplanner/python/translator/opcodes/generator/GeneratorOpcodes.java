package org.optaplanner.python.translator.opcodes.generator;

import java.util.Optional;

import org.optaplanner.python.translator.PythonBytecodeInstruction;
import org.optaplanner.python.translator.PythonVersion;
import org.optaplanner.python.translator.opcodes.Opcode;

public class GeneratorOpcodes {
    public static Optional<Opcode> lookupOpcodeForInstruction(PythonBytecodeInstruction instruction,
            PythonVersion pythonVersion) {
        switch (instruction.opcode) {
            case YIELD_VALUE: {
                return Optional.of(new YieldValueOpcode(instruction));
            }
            case YIELD_FROM: {
                return Optional.of(new YieldFromOpcode(instruction));
            }
            case GET_YIELD_FROM_ITER: {
                return Optional.of(new GetYieldFromIterOpcode(instruction));
            }
            case GEN_START: {
                return Optional.of(new GeneratorStartOpcode(instruction));
            }
            default: {
                return Optional.empty();
            }
        }
    }
}
