// ir/tac/ArrayRef.java
package ir.tac;

public final class ArrayRef implements Value {

    private final Variable base;   // e.g. arr1
    private final Value index;     // linear index (int)

    public ArrayRef(Variable base, Value index) {
        this.base = base;
        this.index = index;
    }

    public Variable base()  { return base; }
    public Value    index() { return index; }

    @Override
    public String toString() {
        return base + "[" + index + "]";
    }

    @Override
    public void accept(TACVisitor v) {
        // We don't use TACVisitor on values right now – safe no-op.
    }
}