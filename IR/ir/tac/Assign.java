package ir.tac;

public abstract class Assign extends TAC{
    
    private Variable dest; // lhs
    private Value left; // operand_1 
    private Value right; // operand_2

    protected Assign(int id, Variable dest, Value left, Value right) {
        super(id);
        this.dest = dest;
        this.left = left;
        this.right = right;
    }
    
    public Variable dest() { return dest; }
    public Value left() { return left; }
    public Value right() { return right; }

    /** textual opcode (e.g., "add","sub","mul","phi",...) */
    protected abstract String op();

    @Override
    public String toString() {
        if (right == null) return dest + " = " + op() + " " + left;
        return dest + " = " + op() + " " + left + " " + right;
    }

    @Override public void accept(TACVisitor v){ v.visit(this); }
}
