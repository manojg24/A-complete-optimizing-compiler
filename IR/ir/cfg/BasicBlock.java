package ir.cfg;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import ir.tac.TAC;

public class BasicBlock extends Block implements Iterable<TAC> {

    private final int num;
    private final List<TAC> instructions = new ArrayList<>();
    private final List<BasicBlock> predecessors = new ArrayList<>();
    private final List<BasicBlock> successors   = new ArrayList<>();

    public BasicBlock(int num) { this.num = num; }

    public int number() { return num; }

    public void addInstruction(TAC t) { instructions.add(t); }
    public List<TAC> instructions() { return instructions; }

    public void addSuccessor(BasicBlock b) {
        if (b == null) return;
        successors.add(b);
        b.predecessors.add(this);
    }

    public List<BasicBlock> preds() { return predecessors; }
    public List<BasicBlock> succs() { return successors; }

    public String dotNodeName() { return "bb" + num; }

    /** Record label used by CFGPrinter. */
    public String dotRecordLabel() {
        StringBuilder sb = new StringBuilder();
        sb.append("<b> BB").append(num).append(" |{");
        if (instructions.isEmpty()) {
            sb.append("\\< empty \\>");
        } else {
            for (int i = 0; i < instructions.size(); i++) {
                sb.append(instructions.get(i).toString());
                if (i != instructions.size() - 1) sb.append(" |");
            }
        }
        sb.append("}}");
        return sb.toString();
    }

    @Override public Iterator<TAC> iterator() { return instructions.iterator(); }

    @Override public void accept(CFGVisitor v) { v.visit(this); }

    @Override public void resetVisited() { visited = false; }
}