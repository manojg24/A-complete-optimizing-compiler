package ir.tac;

public interface Visitable {
    
    public void accept(TACVisitor visitor);
}

interface TACVisitor {
    default void visit(TAC n) {}
    default void visit(Assign n) { visit((TAC) n); }
    default void visit(Add n)    { visit((Assign) n); }
    default void visit(Call n)   { visit((TAC) n); }
    default void visit(Return n) { visit((TAC) n); }
    default void visit(Literal n) {}
    default void visit(Variable n) {}
}