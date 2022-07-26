package org.optaplanner.python.translator;

import java.util.Arrays;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class LocalVariableHelper {

    public final Type[] parameters;
    public final int parameterSlotsEnd;
    public final int pythonCellVariablesStart;
    public final int pythonFreeVariablesStart;
    public final int pythonLocalVariablesSlotEnd;

    public final int pythonBoundVariables;
    public final int pythonFreeVariables;

    public final int currentExceptionVariableSlot;
    int usedLocals;

    public LocalVariableHelper(Type[] parameters, PythonCompiledFunction compiledFunction) {
        this.parameters = parameters;
        int slotsUsedByParameters = 1;
        for (Type parameter : parameters) {
            if (parameter.equals(Type.LONG_TYPE) || parameter.equals(Type.DOUBLE_TYPE)) {
                slotsUsedByParameters += 2;
            } else {
                slotsUsedByParameters += 1;
            }
        }

        pythonBoundVariables = compiledFunction.co_cellvars.size();
        pythonFreeVariables = compiledFunction.co_freevars.size();

        parameterSlotsEnd = slotsUsedByParameters;
        pythonCellVariablesStart = parameterSlotsEnd + compiledFunction.co_varnames.size();
        pythonFreeVariablesStart = pythonCellVariablesStart + pythonBoundVariables;
        currentExceptionVariableSlot = pythonFreeVariablesStart + pythonFreeVariables;
        pythonLocalVariablesSlotEnd = currentExceptionVariableSlot + 1;
    }

    LocalVariableHelper(Type[] parameters, int parameterSlotsEnd, int pythonCellVariablesStart,
            int pythonFreeVariablesStart, int pythonLocalVariablesSlotEnd,
            int pythonBoundVariables, int pythonFreeVariables, int currentExceptionVariableSlot) {
        this.parameters = parameters;
        this.parameterSlotsEnd = parameterSlotsEnd;
        this.pythonCellVariablesStart = pythonCellVariablesStart;
        this.pythonFreeVariablesStart = pythonFreeVariablesStart;
        this.pythonLocalVariablesSlotEnd = pythonLocalVariablesSlotEnd;
        this.pythonBoundVariables = pythonBoundVariables;
        this.pythonFreeVariables = pythonFreeVariables;
        this.currentExceptionVariableSlot = currentExceptionVariableSlot;
    }

    public LocalVariableHelper copy() {
        LocalVariableHelper out = new LocalVariableHelper(parameters, parameterSlotsEnd, pythonCellVariablesStart,
                pythonFreeVariablesStart, pythonLocalVariablesSlotEnd,
                pythonBoundVariables, pythonFreeVariables, currentExceptionVariableSlot);
        out.usedLocals = usedLocals;
        return out;
    }

    public int getParameterSlot(int parameterIndex) {
        if (parameterIndex > parameters.length) {
            throw new IndexOutOfBoundsException("Asked for the slot corresponding to the (" + parameterIndex + ") " +
                    "parameter, but there are only (" + parameters.length + ") parameters (" + Arrays.toString(parameters)
                    + ").");
        }
        int slotsUsedByParameters = 1;
        for (int i = 0; i < parameterIndex; i++) {
            if (parameters[i].equals(Type.LONG_TYPE) || parameters[i].equals(Type.DOUBLE_TYPE)) {
                slotsUsedByParameters += 2;
            } else {
                slotsUsedByParameters += 1;
            }
        }
        return slotsUsedByParameters;
    }

    public int getPythonLocalVariableSlot(int index) {
        return parameterSlotsEnd + index;
    }

    public int getPythonCellOrFreeVariableSlot(int index) {
        return pythonCellVariablesStart + index;
    }

    public int getCurrentExceptionVariableSlot() {
        return currentExceptionVariableSlot;
    }

    public int getNumberOfFreeCells() {
        return pythonFreeVariables;
    }

    public int getNumberOfBoundCells() {
        return pythonBoundVariables;
    }

    public int getNumberOfCells() {
        return pythonBoundVariables + pythonFreeVariables;
    }

    public int getNumberOfLocalVariables() {
        return pythonCellVariablesStart - parameterSlotsEnd;
    }

    public int newLocal() {
        int slot = pythonLocalVariablesSlotEnd + usedLocals;
        usedLocals++;
        return slot;
    }

    public void freeLocal() {
        usedLocals--;
    }

    public int getUsedLocals() {
        return usedLocals;
    }

    public void readLocal(MethodVisitor methodVisitor, int local) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, getPythonLocalVariableSlot(local));
    }

    public void writeLocal(MethodVisitor methodVisitor, int local) {
        methodVisitor.visitVarInsn(Opcodes.ASTORE, getPythonLocalVariableSlot(local));
    }

    public void readCell(MethodVisitor methodVisitor, int cell) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, getPythonCellOrFreeVariableSlot(cell));
    }

    public void writeCell(MethodVisitor methodVisitor, int cell) {
        methodVisitor.visitVarInsn(Opcodes.ASTORE, getPythonCellOrFreeVariableSlot(cell));
    }

    public void writeFreeCell(MethodVisitor methodVisitor, int cell) {
        methodVisitor.visitVarInsn(Opcodes.ASTORE, pythonFreeVariablesStart + cell);
    }

    public void readCurrentException(MethodVisitor methodVisitor) {
        methodVisitor.visitVarInsn(Opcodes.ALOAD, getCurrentExceptionVariableSlot());
    }

    public void writeCurrentException(MethodVisitor methodVisitor) {
        methodVisitor.visitVarInsn(Opcodes.ASTORE, getCurrentExceptionVariableSlot());
    }

    public void readTemp(MethodVisitor methodVisitor, Type type, int temp) {
        methodVisitor.visitVarInsn(type.getOpcode(Opcodes.ILOAD), temp);
    }

    public void writeTemp(MethodVisitor methodVisitor, Type type, int temp) {
        methodVisitor.visitVarInsn(type.getOpcode(Opcodes.ISTORE), temp);
    }

    public void incrementTemp(MethodVisitor methodVisitor, int temp) {
        methodVisitor.visitIincInsn(temp, 1);
    }
}
