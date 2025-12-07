package mocha;

import ir.cfg.BasicBlock;
import ir.tac.*;
import java.util.*;
import ast.AST;

public class FunctionInliner {

    private final Map<String, List<BasicBlock>> functionMap;
    private final Map<String, AST.FunctionDeclaration> astFuncs;
    private final Set<String> globalVars;
    private int nextBlockId = 10000; // Start high to avoid collision
    private int nextTacId = 10000;
    private int tmpCounter = 0;

    public FunctionInliner(Map<String, AST.FunctionDeclaration> astFuncs, Set<String> globalVars) {
        this.functionMap = new HashMap<>();
        this.astFuncs = astFuncs;
        this.globalVars = globalVars;
    }

    public void inline(List<BasicBlock> blocks) {
        buildFunctionMap(blocks);

        boolean changed;
        int maxPasses = 10; // Prevent infinite loops
        int pass = 0;

        do {
            changed = false;
            pass++;
            if (pass > maxPasses)
                break;

            List<InlineCandidate> candidates = new ArrayList<>();

            for (BasicBlock bb : blocks) {
                for (TAC t : bb.instructions()) {
                    if (t instanceof Call c) {
                        String funcName = c.function().name();
                        if (functionMap.containsKey(funcName) && astFuncs.containsKey(funcName)) {
                            candidates.add(new InlineCandidate(bb, c));
                        }
                    }
                }
            }

            if (!candidates.isEmpty()) {
                // Inline the first valid candidate
                for (InlineCandidate cand : candidates) {
                    if (performInlining(blocks, cand)) {
                        changed = true;
                        break; // Restart scan after modification
                    }
                }
            }

        } while (changed);
    }

    private void buildFunctionMap(List<BasicBlock> blocks) {
        functionMap.clear();

        // 1) Find entry block for each function from the IR labels
        Map<String, BasicBlock> entryBlocks = new HashMap<>();
        for (BasicBlock bb : blocks) {
            List<TAC> ins = bb.instructions();
            if (ins == null || ins.isEmpty()) continue;

            TAC first = ins.get(0);
            if (first instanceof Assign a && "label".equals(a.opcode())) {
                String name = a.dest().toString();   // usually the function name
                if (astFuncs.containsKey(name)) {    // only care about real functions
                    entryBlocks.put(name, bb);
                }
            }
        }

        // 2) For each function, BFS/DFS from its entry, staying inside that function
        for (Map.Entry<String, AST.FunctionDeclaration> e : astFuncs.entrySet()) {
            String fname = e.getKey();
            BasicBlock entry = entryBlocks.get(fname);
            if (entry == null) {
                // No label block for this function in IR – skip
                continue;
            }

            Set<BasicBlock> visited = new LinkedHashSet<>();
            Deque<BasicBlock> work = new ArrayDeque<>();
            visited.add(entry);
            work.add(entry);

            while (!work.isEmpty()) {
                BasicBlock b = work.removeFirst();

                for (BasicBlock s : b.succs()) {
                    if (s == null || visited.contains(s)) continue;

                    List<TAC> sins = s.instructions();
                    if (sins != null && !sins.isEmpty()) {
                        TAC first = sins.get(0);
                        if (first instanceof Assign a && "label".equals(a.opcode())) {
                            String otherName = a.dest().toString();
                            // Don't walk into *other* function entries
                            if (!otherName.equals(fname)) {
                                continue;
                            }
                        }
                    }

                    visited.add(s);
                    work.addLast(s);
                }
            }

            functionMap.put(fname, new ArrayList<>(visited));
        }
    }

    private boolean performInlining(List<BasicBlock> blocks, InlineCandidate cand) {
        BasicBlock callBlock = cand.bb;
        Call call = cand.call;
        String funcName = call.function().name();
        List<BasicBlock> calleeBody = functionMap.get(funcName);
        AST.FunctionDeclaration fd = astFuncs.get(funcName);

        if (calleeBody == null || fd == null)
            return false;

        // Prevent direct recursion for now
        // (If we are inside 'funcName', don't inline 'funcName')
        // We can check if callBlock is part of calleeBody's original blocks?
        // Or just simpler: if we are inlining 'foo', and 'foo' calls 'foo', we stop?
        // The loop limit handles infinite expansion.

        // 1. Split callBlock
        List<TAC> preIns = new ArrayList<>();
        List<TAC> postIns = new ArrayList<>();
        boolean seenCall = false;

        for (TAC t : callBlock.instructions()) {
            if (t == call) {
                seenCall = true;
                continue;
            }
            if (!seenCall)
                preIns.add(t);
            else
                postIns.add(t);
        }

        callBlock.instructions().clear();
        callBlock.instructions().addAll(preIns);

        BasicBlock postBlock = new BasicBlock(++nextBlockId);
        postBlock.instructions().addAll(postIns);
        postBlock.succs().addAll(callBlock.succs());
        callBlock.succs().clear();
        blocks.add(postBlock); // Add to main list

        // 2. Clone Callee
        Map<BasicBlock, BasicBlock> blockMap = new HashMap<>();
        List<BasicBlock> clonedBody = new ArrayList<>();
        Map<String, String> varMap = new HashMap<>();

        for (BasicBlock orig : calleeBody) {
            BasicBlock clone = new BasicBlock(++nextBlockId);
            blockMap.put(orig, clone);
            clonedBody.add(clone);
            blocks.add(clone);
        }

        // 3. Map Arguments to Parameters
        // Insert movs at the beginning of the first cloned block
        BasicBlock entryClone = clonedBody.get(0);
        List<TAC> argMoves = new ArrayList<>();
        List<AST.FormalParameter> params = fd.getParameters();
        List<Value> args = call.args();

        for (int i = 0; i < params.size(); i++) {
            String paramName = params.get(i).getIdentifier().getName();
            Value argVal = args.get(i);
            String newParamName = getNewVarName(paramName, varMap);

            // param = arg
            argMoves.add(Compiler.prettyAssign(++nextTacId, new Variable(new Symbol(newParamName, null)), "mov", argVal,
                    null));
        }
        entryClone.instructions().addAll(0, argMoves);

        // 4. Stitch and Clone Instructions
        callBlock.succs().add(entryClone);
        entryClone.preds().add(callBlock);

        for (BasicBlock orig : calleeBody) {
            BasicBlock clone = blockMap.get(orig);

            // Successors
            for (BasicBlock s : orig.succs()) {
                if (blockMap.containsKey(s)) {
                    clone.succs().add(blockMap.get(s));
                    blockMap.get(s).preds().add(clone);
                }
            }

            // Instructions
            for (TAC t : orig.instructions()) {
                if (t instanceof Assign a && "label".equals(a.opcode()))
                    continue;

                if (t instanceof Return r) {
                    if (call.dest() != null && r.value() != null) {
                        Value retVal = renameValue(r.value(), varMap);
                        clone.addInstruction(Compiler.prettyAssign(++nextTacId, call.dest(), "mov", retVal, null));
                    }
                    clone.succs().add(postBlock);
                    postBlock.preds().add(clone);
                    continue;
                }
                
                if (t instanceof Assign a && "ret".equals(a.opcode())) {
                    if (call.dest() != null && a.left() != null) {
                        // ret R1  => move that value into the call destination (b in your test)
                        Value retVal = renameValue(a.left(), varMap);
                        clone.addInstruction(
                            Compiler.prettyAssign(++nextTacId, call.dest(), "mov", retVal, null)
                        );
                    }
                    clone.succs().add(postBlock);
                    postBlock.preds().add(clone);
                    continue;
                }

                clone.addInstruction(cloneTAC(t, varMap));
            }
        }

        // If the function falls through without return (void), link last blocks to
        // postBlock
        for (BasicBlock b : clonedBody) {
            if (b.succs().isEmpty()) {
                b.succs().add(postBlock);
                postBlock.preds().add(b);
            }
        }

        return true;
    }

    private String getNewVarName(String old, Map<String, String> varMap) {
        if (!varMap.containsKey(old)) {
            varMap.put(old, old + "_inl_" + (++tmpCounter));
        }
        return varMap.get(old);
    }

    private Value renameValue(Value v, Map<String, String> varMap) {
        if (v instanceof Variable var) {
            String name = var.toString();
            if (globalVars.contains(name)) {
                return v;
            }
            return new Variable(new Symbol(getNewVarName(name, varMap), null));
        }
        return v;
    }

    private TAC cloneTAC(TAC t, Map<String, String> varMap) {
        if (t instanceof Assign a) {
            // Safety: never clone raw "ret" assigns
            if ("ret".equals(a.opcode())) {
                // Should have been handled in performInlining; make it a dead mov
                return Compiler.prettyAssign(
                    ++nextTacId,
                    new Variable(new Symbol("_dead_ret_" + (++tmpCounter), null)),
                    "mov",
                    new Literal(0),
                    null
                );
            }

            Value L = renameValue(a.left(), varMap);
            Value R = renameValue(a.right(), varMap);
            Variable D = (Variable) renameValue(a.dest(), varMap);
            return Compiler.prettyAssign(++nextTacId, D, a.opcode(), L, R);
        } else if (t instanceof Call c) {
            List<Value> newArgs = new ArrayList<>();
            if (c.args() != null) {
                for (Value v : c.args())
                    newArgs.add(renameValue(v, varMap));
            }
            Variable D = (c.dest() != null) ? (Variable) renameValue(c.dest(), varMap) : null;
            return new Call(++nextTacId, c.function(), newArgs, D);
        }
        return t;
    }

    private record InlineCandidate(BasicBlock bb, Call call) {
    }
}
