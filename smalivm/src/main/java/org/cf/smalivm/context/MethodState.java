package org.cf.smalivm.context;

import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

import org.cf.smalivm.type.TypeUtil;
import org.cf.util.ImmutableUtils;
import org.cf.util.SmaliClassUtils;

public class MethodState extends BaseState {

    public static final int ResultRegister = -1;
    public static final int ReturnAddress = -3;
    public static final int ReturnRegister = -2;

    public static final String MUTABLE_PARAMETER_HEAP = "mutable";
    public static final String METHOD_HEAP = "method";

    private final int parameterCount;
    private final int parameterSize;
    private final TIntSet mutableParameters;

    public MethodState(ExecutionContext ectx, int registerCount) {
        this(ectx, registerCount, 0, 0);
    }

    public MethodState(ExecutionContext ectx, int registerCount, int parameterCount, int parameterSize) {
        super(ectx, registerCount);

        this.parameterCount = parameterCount;
        this.parameterSize = parameterSize;
        mutableParameters = new TIntHashSet(parameterCount);
    }

    MethodState(MethodState other, ExecutionContext ectx) {
        super(other, ectx);

        this.parameterCount = other.parameterCount;
        this.parameterSize = other.parameterSize;
        mutableParameters = new TIntHashSet(other.mutableParameters);
    }

    private MethodState(MethodState parent, ExecutionContext ectx, TIntSet mutableParameters) {
        super(parent, ectx);

        this.parameterCount = parent.parameterCount;
        this.parameterSize = parent.parameterSize;
        this.mutableParameters = parent.mutableParameters;
    }

    public void assignParameter(int parameterIndex, Object value) {
        assignRegister(getParameterStart() + parameterIndex, value, METHOD_HEAP);

        String type = TypeUtil.getValueType(value);
        type = SmaliClassUtils.javaClassToSmali(type);
        boolean mutable = !ImmutableUtils.isImmutableClass(type);
        if (mutable) {
            pokeRegister(parameterIndex, value, MUTABLE_PARAMETER_HEAP);
            mutableParameters.add(parameterIndex);
        }
    }

    public void assignRegister(int register, Object value) {
        super.assignRegister(register, value, METHOD_HEAP);
    }

    public void assignResultRegister(Object value) {
        assignRegister(ResultRegister, value, METHOD_HEAP);
    }

    public void assignReturnRegister(Object value) {
        pokeRegister(ReturnRegister, value, METHOD_HEAP);
    }

    public void pokeRegister(int register, Object value) {
        super.pokeRegister(register, value, METHOD_HEAP);
    }

    public Object readRegister(int register) {
        return super.readRegister(register, METHOD_HEAP);
    }

    public Object peekParameter(int parameterIndex) {
        Object value;
        if (mutableParameters.contains(parameterIndex)) {
            value = peekRegister(parameterIndex, MUTABLE_PARAMETER_HEAP);
        } else {
            value = peekRegister(getParameterStart() + parameterIndex);
        }

        return value;
    }

    public int getParameterCount() {
        return parameterCount;
    }

    public int getParameterStart() {
        return getRegisterCount() - parameterSize;
    }

    @Override
    public MethodState getParent() {
        return (MethodState) super.getParent();
    }

    public int getPseudoInstructionReturnAddress() {
        return (int) peekRegister(ReturnAddress);
    }

    public Object peekRegister(int register) {
        return super.peekRegister(register, METHOD_HEAP);
    }

    public Object readResultRegister() {
        Object result = readRegister(ResultRegister, METHOD_HEAP);
        // TODO: removeRegister and see what breaks..
        ectx.getHeap().remove(METHOD_HEAP, ResultRegister);

        return result;
    }

    public Object readReturnRegister() {
        return peekRegister(ReturnRegister);
    }

    public void setPseudoInstructionReturnAddress(int address) {
        // Pseudo instructions like array-data-payload need return addresses.
        pokeRegister(ReturnAddress, address, METHOD_HEAP);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (getParameterCount() > 0) {
            sb.append("parameters: ").append(parameterCount).append("\n[");
            boolean hadAtLeastOneParameter = false;
            for (int parameterIndex = 0; parameterIndex < getParameterCount(); parameterIndex++) {
                int register = getParameterStart() + parameterIndex;
                if (!super.hasRegister(register, METHOD_HEAP)) {
                    continue;
                }
                hadAtLeastOneParameter = true;
                sb.append("p").append(parameterIndex).append(": ");
                int targetRegister = register;
                String heapId = METHOD_HEAP;
                if (mutableParameters.contains(parameterIndex)) {
                    targetRegister = parameterIndex;
                    heapId = MUTABLE_PARAMETER_HEAP;
                }
                if (super.hasRegister(targetRegister, heapId)) {
                    sb.append(registerToString(targetRegister, heapId));
                } else {
                    sb.append("*in ancestor*");
                }
                sb.append(",\n");
            }
            if (hadAtLeastOneParameter) {
                sb.setLength(sb.length() - 2);
            }
            sb.append("]");
        }

        int localsCount = getRegisterCount() - getParameterCount();
        if (localsCount > 0) {
            boolean hadAtLeastOneLocal = false;
            sb.append("\nlocals: ").append(localsCount).append("\n[");
            for (int register = 0; register < (getRegisterCount() - getParameterCount()); register++) {
                if (!hasRegister(register, METHOD_HEAP)) {
                    continue;
                }
                hadAtLeastOneLocal = true;
                sb.append("v").append(register).append(": ").append(registerToString(register, METHOD_HEAP))
                                .append(",\n");
            }
            if (hadAtLeastOneLocal) {
                sb.setLength(sb.length() - 2);
            }
            sb.append("]");
        }

        if (hasRegister(ResultRegister, METHOD_HEAP)) {
            sb.append("\nresult: ").append(registerToString(ResultRegister, METHOD_HEAP));
        }

        if (hasRegister(ReturnRegister, METHOD_HEAP)) {
            sb.append("\nreturn: ").append(registerToString(ReturnRegister, METHOD_HEAP));
        }

        return sb.toString();
    }

    public boolean wasRegisterRead(int register) {
        return wasRegisterRead(register, METHOD_HEAP);
    }

    public void assignRegisterAndUpdateIdentities(int register, Object value) {
        assignRegisterAndUpdateIdentities(register, value, METHOD_HEAP);
    }

    MethodState getChild(ExecutionContext childContext) {
        MethodState child = new MethodState(this, childContext, mutableParameters);

        return child;
    }

}
