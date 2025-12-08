// ir/tac/ArrayStore.java
package ir.tac;

public final class ArrayStore extends TAC {
    private final Variable base;  // arr
    private final Value index;    // linear index
    private final Value value;    // RHS

    public ArrayStore(int id, Variable base, Value index, Value value) {
        super(id);
        this.base = base;
        this.index = index;
        this.value = value;
    }

    public Variable base() { return base; }
    public Value    index() { return index; }
    public Value    value() { return value; }

    @Override
    public String toString() {
        return base + "[" + index + "] = " + value;
    }
}