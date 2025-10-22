package ir.cfg;

import java.util.Collection;

public class CFGPrinter implements CFGVisitor {

    private final StringBuilder out = new StringBuilder();
    private boolean started = false;

    public String print(Collection<BasicBlock> blocks) {
        start();

        // nodes
        for (BasicBlock bb : blocks) {
            bb.accept(this);
        }
        // edges
        for (BasicBlock bb : blocks) {
            for (BasicBlock succ : bb.succs()) {
                out.append(bb.dotNodeName()).append(" : s -> ")
                   .append(succ.dotNodeName()).append(" : n ")
                   .append("[ label =\"").append(edgeLabel(bb, succ)).append("\" ];\n");
            }
        }

        end();
        return out.toString();
    }

    private void start() {
        if (started) return;
        started = true;
        out.append("digraph G {\n");
    }

    private void end() {
        out.append("}\n");
        finishGraph();
    }

    private String edgeLabel(BasicBlock from, BasicBlock to) {
        int idx = from.succs().indexOf(to);
        return (idx == 0) ? "fall-through" : "branch";
    }

    @Override
    public void visit(BasicBlock bb) {
        out.append(bb.dotNodeName())
           .append(" [ shape = record , label = \" ")
           .append(bb.dotRecordLabel())
           .append(" \"];\n");
    }
}