package ir.cfg;

public interface Visitable {
    
    public void accept (CFGVisitor visitor);
}

interface CFGVisitor {
    default void visit(BasicBlock bb) {}
    default void finishGraph() {}     // optional hook for printers
}