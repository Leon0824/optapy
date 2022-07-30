package org.optaplanner.python.translator.opcodes.stack;

import java.util.Optional;

import org.optaplanner.python.translator.PythonBytecodeInstruction;
import org.optaplanner.python.translator.PythonVersion;
import org.optaplanner.python.translator.opcodes.Opcode;

public class StackManipulationOpcodes {
    public static Optional<Opcode> lookupOpcodeForInstruction(PythonBytecodeInstruction instruction,
            PythonVersion pythonVersion) {
        switch (instruction.opcode) {
            case NOP: {
                return Optional.of(new NopOpcode(instruction));
            }
            case POP_TOP: {
                return Optional.of(new PopOpcode(instruction));
            }
            case ROT_TWO: {
                return Optional.of(new RotateTwoOpcode(instruction));
            }
            case ROT_THREE: {
                return Optional.of(new RotateThreeOpcode(instruction));
            }
            case ROT_FOUR: {
                return Optional.of(new RotateFourOpcode(instruction));
            }
            case DUP_TOP: {
                return Optional.of(new DupOpcode(instruction));
            }
            case DUP_TOP_TWO: {
                return Optional.of(new DupTwoOpcode(instruction));
            }
            default: {
                return Optional.empty();
            }
        }
    }
}
