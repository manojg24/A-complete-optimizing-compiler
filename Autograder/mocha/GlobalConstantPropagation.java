package mocha;

import java.util.*;
import ir.cfg.BasicBlock;
import ir.tac.*;

public class GlobalConstantPropagation {

    private final Set<String> globals;

    public GlobalConstantPropagation(Set<String> globals) {
        this.globals = globals != null ? globals : Collections.emptySet();
    }

    static class LatticeValue {
        enum Type {
            TOP, CONST, BOTTOM
        }

        final Type type;
        final Object value;

        static final LatticeValue TOP = new LatticeValue(Type.TOP, null);
        static final LatticeValue BOTTOM = new LatticeValue(Type.BOTTOM, null);

        private LatticeValue(Type type, Object value) {
            this.type = type;
            this.value = value;
        }

        static LatticeValue constant(Object val) {
            return new LatticeValue(Type.CONST, val);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof LatticeValue))
                return false;
            LatticeValue that = (LatticeValue) o;
            return type == that.type && Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, value);
        }

        @Override
        public String toString() {
            if (type == Type.CONST)
                return "CONST(" + value + ")";
            return type.toString();
        }

        LatticeValue meet(LatticeValue other) {
            if (this == TOP)
                return other;
            if (other == TOP)
                return this;
            if (this == BOTTOM || other == BOTTOM)
                return BOTTOM;
            if (this.type == Type.CONST && other.type == Type.CONST) {
                if (Objects.equals(this.value, other.value))
                    return this;
            }
            return BOTTOM;
        }
    }

    public void optimize(List<BasicBlock> blocks) {
        if (blocks.isEmpty())
            return;

        // 1. Initialize State
        // Map: BasicBlock -> Map<Variable, LatticeValue> (OUT set)
        Map<BasicBlock, Map<String, LatticeValue>> outStates = new HashMap<>();

        // Initialize all blocks to TOP (except maybe globals in entry block?)
        // Actually, for GCP, we assume everything is TOP initially.
        // But globals coming into the function (if we consider whole program) are
        // BOTTOM (unknown)
        // unless we know they are initialized.
        // For safety, let's assume all globals are BOTTOM at entry.

        // We'll use a worklist algorithm.
        Deque<BasicBlock> worklist = new ArrayDeque<>();

        // Initialize OUT of all blocks to empty (effectively TOP for all vars)
        for (BasicBlock bb : blocks) {
            outStates.put(bb, new HashMap<>());
            worklist.add(bb);
        }

        // Entry block handling?
        // We need to handle the "IN" of the entry block.
        // Implicitly, uninitialized locals are TOP (or 0/false if we assume default
        // init).
        // Globals are BOTTOM (could be anything).

        BasicBlock entry = blocks.get(0);

        while (!worklist.isEmpty()) {
            BasicBlock bb = worklist.removeFirst();

            // Calculate IN state from predecessors
            Map<String, LatticeValue> inState = new HashMap<>();

            if (bb == entry) {
                // Entry block IN: Globals are BOTTOM, others TOP
                for (String g : globals) {
                    inState.put(g, LatticeValue.BOTTOM);
                }
            } else {
                // Merge predecessors
                boolean first = true;
                for (BasicBlock pred : bb.preds()) {
                    Map<String, LatticeValue> predOut = outStates.get(pred);
                    if (first) {
                        inState.putAll(predOut);
                        first = false;
                    } else {
                        // Merge logic:
                        // For each variable in either map, meet them.
                        // If a variable is missing in one, it's treated as TOP (so result is the
                        // other).
                        // Wait, if missing means TOP, then:
                        // result[v] = predOut[v] meet inState[v]

                        Set<String> allVars = new HashSet<>(inState.keySet());
                        allVars.addAll(predOut.keySet());

                        for (String v : allVars) {
                            LatticeValue v1 = inState.getOrDefault(v, LatticeValue.TOP);
                            LatticeValue v2 = predOut.getOrDefault(v, LatticeValue.TOP);
                            inState.put(v, v1.meet(v2));
                        }
                    }
                }
            }

            // Transfer Function: Execute block
            Map<String, LatticeValue> current = new HashMap<>(inState);

            for (TAC t : bb.instructions()) {
                if (t instanceof Assign a) {
                    String dest = a.dest() != null ? a.dest().toString() : null;
                    if (dest != null) {
                        // Evaluate RHS
                        LatticeValue res = evaluate(a, current);
                        current.put(dest, res);
                    }
                } else if (t instanceof Call c) {
                    // Function calls invalidate globals!
                    // And if it returns a value, that value is BOTTOM (unknown)
                    if (c.dest() != null) {
                        current.put(c.dest().toString(), LatticeValue.BOTTOM);
                    }
                    // Whitelist printInt and printString to not invalidate globals
                    String funcName = c.function().name();
                    if (!"printInt".equals(funcName) && !"printString".equals(funcName)) {
                        for (String g : globals) {
                            current.put(g, LatticeValue.BOTTOM);
                        }
                    }
                }
            }

            // Check if OUT changed
            Map<String, LatticeValue> oldOut = outStates.get(bb);
            if (!current.equals(oldOut)) {
                outStates.put(bb, current);
                for (BasicBlock succ : bb.succs()) {
                    if (!worklist.contains(succ)) {
                        worklist.add(succ);
                    }
                }
            }
        }

        // 2. Rewrite Instructions
        for (BasicBlock bb : blocks) {
            // Re-run transfer to get state at each instruction
            // We need to calculate IN again
            Map<String, LatticeValue> current = new HashMap<>();
            if (bb == entry) {
                for (String g : globals) {
                    current.put(g, LatticeValue.BOTTOM);
                }
            } else {
                boolean first = true;
                for (BasicBlock pred : bb.preds()) {
                    Map<String, LatticeValue> predOut = outStates.get(pred);
                    if (first) {
                        current.putAll(predOut);
                        first = false;
                    } else {
                        Set<String> allVars = new HashSet<>(current.keySet());
                        allVars.addAll(predOut.keySet());
                        for (String v : allVars) {
                            LatticeValue v1 = current.getOrDefault(v, LatticeValue.TOP);
                            LatticeValue v2 = predOut.getOrDefault(v, LatticeValue.TOP);
                            current.put(v, v1.meet(v2));
                        }
                    }
                }
            }

            List<TAC> newIns = new ArrayList<>();
            for (TAC t : bb.instructions()) {
                // Try to constant fold uses
                TAC optimized = t;
                if (t instanceof Assign a) {
                    Value newLeft = replaceWithConst(a.left(), current);
                    Value newRight = replaceWithConst(a.right(), current);

                    // If we have new constants, create new instruction
                    if (newLeft != a.left() || newRight != a.right()) {
                        optimized = Compiler.prettyAssign(a.id(), a.dest(), a.opcode(), newLeft, newRight);
                    }

                    // Update state
                    String dest = a.dest() != null ? a.dest().toString() : null;
                    if (dest != null) {
                        LatticeValue res = evaluate((Assign) optimized, current);
                        current.put(dest, res);
                    }
                } else if (t instanceof Call c) {
                    List<Value> newArgs = new ArrayList<>();
                    boolean changed = false;
                    if (c.args() != null) {
                        for (Value v : c.args()) {
                            Value newV = replaceWithConst(v, current);
                            newArgs.add(newV);
                            if (newV != v)
                                changed = true;
                        }
                    }
                    if (changed) {
                        optimized = new Call(c.id(), c.function(), newArgs, c.dest());
                    }

                    if (c.dest() != null) {
                        current.put(c.dest().toString(), LatticeValue.BOTTOM);
                    }
                    String funcName = c.function().name();
                    if (!"printInt".equals(funcName) && !"printString".equals(funcName)) {
                        for (String g : globals) {
                            current.put(g, LatticeValue.BOTTOM);
                        }
                    }
                } else if (t instanceof Return r) {
                    Value newV = replaceWithConst(r.value(), current);
                    // Return only accepts Variable, so if we folded to a Literal, we can't use it
                    // directly in Return.
                    // We'd need to insert a move, but for now let's just keep the variable usage.
                    if (newV != r.value() && newV instanceof Variable) {
                        optimized = new Return(r.id(), (Variable) newV);
                    }
                }

                newIns.add(optimized);
            }
            bb.instructions().clear();
            bb.instructions().addAll(newIns);
        }
    }

    private Value replaceWithConst(Value v, Map<String, LatticeValue> state) {
        if (v instanceof Variable var) {
            LatticeValue lv = state.getOrDefault(var.toString(), LatticeValue.TOP);
            if (lv.type == LatticeValue.Type.CONST) {
                return new Literal(lv.value);
            }
        }
        return v;
    }

    private LatticeValue evaluate(Assign a, Map<String, LatticeValue> state) {
        String op = a.opcode();
        Value left = a.left();
        Value right = a.right();

        Object v1 = getValue(left, state);
        Object v2 = getValue(right, state);

        if (v1 == null || (right != null && v2 == null)) {
            // One operand is BOTTOM or unknown
            // If operands are TOP, result is TOP?
            // If operands are BOTTOM, result is BOTTOM.
            if (isBottom(left, state) || isBottom(right, state))
                return LatticeValue.BOTTOM;
            return LatticeValue.TOP;
        }

        // Both are constants
        try {
            if (v1 instanceof Integer i1) {
                if (v2 instanceof Integer i2) {
                    switch (op) {
                        case "add":
                            return LatticeValue.constant(i1 + i2);
                        case "sub":
                            return LatticeValue.constant(i1 - i2);
                        case "mul":
                            return LatticeValue.constant(i1 * i2);
                        case "div":
                            return i2 == 0 ? LatticeValue.BOTTOM : LatticeValue.constant(i1 / i2);
                        case "mod":
                            return i2 == 0 ? LatticeValue.BOTTOM : LatticeValue.constant(i1 % i2);
                        case "lt":
                            return LatticeValue.constant(i1 < i2);
                        case "le":
                            return LatticeValue.constant(i1 <= i2);
                        case "gt":
                            return LatticeValue.constant(i1 > i2);
                        case "ge":
                            return LatticeValue.constant(i1 >= i2);
                        case "eq":
                            return LatticeValue.constant(i1.equals(i2));
                        case "neq":
                            return LatticeValue.constant(!i1.equals(i2));
                        case "and":
                            return LatticeValue.constant((i1 != 0) && (i2 != 0)); // boolean as int?
                        case "or":
                            return LatticeValue.constant((i1 != 0) || (i2 != 0));
                    }
                }
            }
            // Handle boolean ops if values are Booleans
            if (v1 instanceof Boolean b1) {
                if (v2 instanceof Boolean b2) {
                    switch (op) {
                        case "and":
                            return LatticeValue.constant(b1 && b2);
                        case "or":
                            return LatticeValue.constant(b1 || b2);
                        case "eq":
                            return LatticeValue.constant(b1.equals(b2));
                        case "neq":
                            return LatticeValue.constant(!b1.equals(b2));
                    }
                } else if (op.equals("not")) {
                    return LatticeValue.constant(!b1);
                }
            }

            // Move/Copy
            if (op.equals("mov")) {
                return LatticeValue.constant(v1);
            }

        } catch (Exception e) {
            return LatticeValue.BOTTOM;
        }

        return LatticeValue.BOTTOM;
    }

    private boolean isBottom(Value v, Map<String, LatticeValue> state) {
        if (v instanceof Variable var) {
            LatticeValue lv = state.getOrDefault(var.toString(), LatticeValue.TOP);
            return lv.type == LatticeValue.Type.BOTTOM;
        }
        return false; // Literals are never BOTTOM
    }

    private Object getValue(Value v, Map<String, LatticeValue> state) {
        if (v == null)
            return null;
        if (v instanceof Literal l)
            return l.value();
        if (v instanceof Variable var) {
            LatticeValue lv = state.getOrDefault(var.toString(), LatticeValue.TOP);
            if (lv.type == LatticeValue.Type.CONST)
                return lv.value;
        }
        return null;
    }
}
