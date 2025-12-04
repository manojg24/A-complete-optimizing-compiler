package types;

import ast.*;
import ast.AST.*;
import mocha.Symbol;
import mocha.SymbolTable;
import ast.NodeVisitor;
import java.util.List;
import java.util.ArrayList;

public class TypeChecker implements NodeVisitor {

    private StringBuilder errorBuffer;
    private SymbolTable table;
    private Symbol currentFunction;

    public boolean check(Visitable ast) {
        this.table = new SymbolTable();
        this.errorBuffer = new StringBuilder();

        // Check if the AST root is null (from a parse failure)
        if (ast instanceof AST && ((AST)ast).getRoot() == null) {
            // If parsing failed, we can't type check. Consider this an error.
            return false;
        }
        
        ast.accept(this);
        
        // Return false if errors were found, true otherwise.
        return !hasError();
    }

    private void reportError(int lineNum, int charPos, String message) {
        errorBuffer.append("TypeError(" + lineNum + "," + charPos + ")");
        errorBuffer.append("[" + message + "]\n");
    }

    public boolean hasError() {
        return errorBuffer.length() != 0;
    }

    public String errorReport() {
        return errorBuffer.toString();
    }
    
    private String mangleFunc(String base, List<Type> paramTypes) {
        StringBuilder sb = new StringBuilder(base).append("#");
        for (Type t : paramTypes) sb.append(codeOf(t));
        return sb.toString();
    }

    private String codeOf(Type t) {
        if (t instanceof IntType)   return "I";
        if (t instanceof FloatType) return "F";
        if (t instanceof BoolType)  return "B";
        if (t instanceof VoidType)  return "V";
        if (t instanceof ArrayType) return "A" + codeOf(((ArrayType) t).getBase());
        return "T";
    }
    
    private String shortName(Type t) {
        if (t instanceof BoolType)  return "bool";
        if (t instanceof IntType)   return "int";
        if (t instanceof FloatType) return "float";
        return t == null ? "null" : t.toString();   // ErrorType prints nicely
    }

    private List<Type> toJavaList(TypeList tl) {
        // build a java.util.List from your TypeList
        List<Type> out = new ArrayList<>();
        for (int i = 0; i < tl.size(); i++) out.add(tl.get(i));
        return out;
    }

    private TypeList toTypeList(List<Type> list) {
        TypeList tl = new TypeList();
        for (Type t : list) tl.append(t);
        return tl;
    }

    // Track the base function name for nicer return error messages
    private String currentFunctionName = null;

    // A helper for visiting binary expressions
    private void visitBinaryExpression(Expression node, Expression left, Expression right, String operation) {
        left.accept(this);
        right.accept(this);

        Type leftType = left.getType();
        Type rightType = right.getType();

        // Do NOT short-circuit on ErrorType. Let the type ops produce nested messages.
        if (leftType == null)  leftType  = new ErrorType("Unresolved left operand.");
        if (rightType == null) rightType = new ErrorType("Unresolved right operand.");

        Type resultType;
        switch (operation) {
            case "+":  resultType = leftType.add(rightType);  break;
            case "-":  resultType = leftType.sub(rightType);  break;
            case "*":  resultType = leftType.mul(rightType);  break;
            case "/":  resultType = leftType.div(rightType);  break;
            case "%":
                if (leftType instanceof IntType && rightType instanceof IntType) {
                    resultType = new IntType();
                } else {
                    resultType = new ErrorType("Modulo operator requires int operands.");
                }
                break;
            case "&&": resultType = leftType.and(rightType);  break;
            case "||": resultType = leftType.or(rightType);   break;
            case "compare": resultType = leftType.compare(rightType); break;
            default:  resultType = new ErrorType("Unknown binary operation: " + operation);
        }

        if (resultType instanceof ErrorType) {
            reportError(node.lineNumber(), node.charPosition(), ((ErrorType) resultType).getMessage());
        }
        node.setType(resultType);
    }
    
    private String fmtArgs(java.util.List<Type> types) {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(shortName(types.get(i))); // prints int/float/bool
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public void visit(Computation node) {
        // (0) Make globals visible to function bodies
        node.variables().accept(this);

        // (1) Predeclare all functions with both mangled and base names
        for (Declaration d : node.functions()) {
            if (d instanceof AST.FunctionDeclaration) {

            	AST.FunctionDeclaration fd = (AST.FunctionDeclaration) d;
                // Build the parameter typelist and ensure TypeNodes are set
                TypeList tl = new TypeList();
                for (AST.FormalParameter p : fd.getParameters()) {
                    p.getTypeNode().accept(this);
                    tl.append(p.getTypeNode().getType());
                }
                // Resolve return type
                fd.getReturnType().accept(this);
                Type ret = fd.getReturnType().getType();
                FuncType fty = new FuncType(tl, ret);

                // Mangled name for exact overload match
                java.util.List<Type> paramList = new java.util.ArrayList<>();
                for (AST.FormalParameter p : fd.getParameters()) {
                    paramList.add(p.getTypeNode().getType());
                }
                String base = fd.getIdentifier().getName();
                String mangled = mangleFunc(base, paramList);

                try {
                    table.insert(mangled, fty);
                } catch (Throwable e) {
                    reportError(fd.lineNumber(), fd.charPosition(), e.getMessage());
                }

                // Also insert the plain base name ONCE (for fallback mismatch diagnostics)
                try {
                    table.lookup(base); // already present? fine
                } catch (Throwable nf) {
                    try { table.insert(base, fty); }
                    catch (Throwable ignore) { /* okay if another file inserted it */ }
                }
            }
        }

        // (2) Now type-check function bodies and main
        node.functions().accept(this);
        node.mainStatementSequence().accept(this);
        node.setType(new VoidType());
    }
    
    // LITERALS
    @Override
    public void visit(BoolLiteral node) {
        node.setType(new BoolType());
    }

    @Override
    public void visit(IntegerLiteral node) {
        node.setType(new IntType());
    }

    @Override
    public void visit(FloatLiteral node) {
        node.setType(new FloatType());
    }

    // DESIGNATORS
    @Override
    public void visit(AddressOf node) {
        try {
            Symbol symbol = table.lookup(node.getIdentifier().getName());
            node.getIdentifier().setSymbol(symbol);
            node.setType(symbol.type());
        } catch (Throwable e) {
            reportError(node.lineNumber(), node.charPosition(), "Symbol not found: " + node.getIdentifier().getName());
            node.setType(new ErrorType("Symbol not found."));
        }
    }
    
    private String extractRootIdent(Expression e) {
        while (e instanceof AST.ArrayIndex) {
            e = ((AST.ArrayIndex) e).getBase();
        }
        if (e instanceof AST.Identifier) {
            return ((AST.Identifier) e).getName();
        }
        return null;
    }

    @Override
    public void visit(AST.ArrayIndex node) {
        // Type-check children first
        node.getBase().accept(this);
        node.getIndex().accept(this);

        Type baseType  = node.getBase().getType();
        Type indexType = node.getIndex().getType();

        if (baseType == null)  baseType  = new ErrorType("Unresolved array base.");
        if (indexType == null) indexType = new ErrorType("Unresolved array index.");

        // ---- If base is NOT an array, craft the message the old tests expect ----
        if (!(baseType instanceof ArrayType)) {
            // Print AddressOf(<type>) when the base is a simple identifier (lvalue-like)
            boolean looksLikeLValue = (node.getBase() instanceof AST.Identifier)
                                   || (node.getBase() instanceof AST.AddressOf);
            String baseDesc = looksLikeLValue
                    ? "AddressOf(" + shortName(baseType) + ")"
                    : shortName(baseType);
            String idxDesc = shortName(indexType);

            String msg = "Cannot index " + baseDesc + " with " + idxDesc + ".";
            reportError(node.lineNumber(), node.charPosition(), msg);
            node.setType(new ErrorType(msg));
            return;
        }

        // ---- Optional static bounds check for literal indices (kept as before) ----
        if (indexType instanceof IntType && node.getIndex() instanceof AST.IntegerLiteral) {
            ArrayType arr = (ArrayType) baseType;
            AST.IntegerLiteral lit = (AST.IntegerLiteral) node.getIndex();
            int extent = arr.getExtent();
            int idx    = lit.getValue();
            if (extent >= 0 && (idx < 0 || idx >= extent)) {
                String msg = "Array Index Out of Bounds : " + idx + " for array " +
                             (node.getBase() instanceof AST.Identifier
                              ? ((AST.Identifier)node.getBase()).getName() : "array");
                reportError(lit.lineNumber(), lit.charPosition(), msg);
                node.setType(new ErrorType(msg));
                return;
            }
        }

        // Normal rule
        Type resultType = baseType.index(indexType);
        if (resultType instanceof ErrorType) {
            reportError(node.lineNumber(), node.charPosition(),
                ((ErrorType) resultType).getMessage());
        }
        node.setType(resultType);
    }
    
    @Override
    public void visit(Dereference node) {
        // This would be for pointers, which may not be in your language.
        // For now, we can treat it as an error or a placeholder.
        reportError(node.lineNumber(), node.charPosition(), "Dereference operator not supported.");
        node.setType(new ErrorType("Dereference not supported."));
    }

    // EXPRESSIONS
    @Override
    public void visit(LogicalNot node) {
        node.getExpression().accept(this);
        Type exprType = node.getExpression().getType();
        Type resultType = exprType.not();

        if (resultType instanceof ErrorType) {
            reportError(node.lineNumber(), node.charPosition(), ((ErrorType) resultType).getMessage());
        }
        node.setType(resultType);
    }

    @Override
    public void visit(Power node) {
        node.getBase().accept(this);
        node.getExponent().accept(this);

        Type baseType = node.getBase().getType();
        Type expType  = node.getExponent().getType();

        boolean baseNumeric = (baseType instanceof IntType) || (baseType instanceof FloatType);
        boolean expNumeric  = (expType  instanceof IntType) || (expType  instanceof FloatType);

        if (!baseNumeric || !expNumeric) {
            reportError(node.lineNumber(), node.charPosition(),
                "Power operator requires numeric base and exponent.");
            node.setType(new ErrorType("Invalid types for power op."));
            return;
        }

        // int ^ int -> int, else -> float
        if (baseType instanceof IntType && expType instanceof IntType) {
            node.setType(new IntType());
        } else {
            node.setType(new FloatType());
        }
    }

    @Override
    public void visit(Multiplication node) {
        visitBinaryExpression(node, node.getLeft(), node.getRight(), "*");
    }

    @Override
    public void visit(Division node) {
        visitBinaryExpression(node, node.getLeft(), node.getRight(), "/");
    }

    @Override
    public void visit(Modulo node) {
        visitBinaryExpression(node, node.getLeft(), node.getRight(), "%");
    }
    
    @Override
    public void visit(LogicalAnd node) {
        visitBinaryExpression(node, node.getLeft(), node.getRight(), "&&");
    }

    @Override
    public void visit(Addition node) {
        visitBinaryExpression(node, node.getLeft(), node.getRight(), "+");
    }

    @Override
    public void visit(Subtraction node) {
        visitBinaryExpression(node, node.getLeft(), node.getRight(), "-");
    }
    
    @Override
    public void visit(LogicalOr node) {
        visitBinaryExpression(node, node.getLeft(), node.getRight(), "||");
    }

    @Override
    public void visit(Relation node) {
        visitBinaryExpression(node, node.getLeft(), node.getRight(), "compare");
    }

    // STATEMENTS
    @Override
    public void visit(Assignment node) {
        node.getDestination().accept(this);
        node.getSource().accept(this);
        Type destType = node.getDestination().getType();
        Type sourceType = node.getSource().getType();
        
        if (destType instanceof ErrorType || sourceType instanceof ErrorType) {
            node.setType(new VoidType());
            return;
        }

        if (destType == null) destType = new ErrorType("Unresolved LHS.");
        if (sourceType == null) sourceType = new ErrorType("Unresolved RHS.");
        
        Type resultType = destType.assign(sourceType);

        if (resultType instanceof ErrorType) {
            reportError(node.lineNumber(), node.charPosition(), ((ErrorType) resultType).getMessage());
        }
        node.setType(new VoidType());
    }

    @Override
    public void visit(IfStatement node) {
        node.getCondition().accept(this);
        Type condType = node.getCondition().getType();

        if (!(condType instanceof BoolType)) {
            reportError(node.lineNumber(), node.charPosition(), "IfStat requires bool condition not " + condType + ".");
        }

        table.enterScope();
        node.getThenBlock().accept(this);
        table.exitScope();

        if (node.getElseBlock() != null) {
            table.enterScope();
            node.getElseBlock().accept(this);
            table.exitScope();
        }
        node.setType(new VoidType());
    }
    
    @Override
    public void visit(WhileStatement node) {
        node.getCondition().accept(this);
        Type condType = node.getCondition().getType();

        if (!(condType instanceof BoolType)) {
            reportError(node.lineNumber(), node.charPosition(), "WhileStat requires bool condition not " + shortName(condType) + ".");
        }

        table.enterScope();
        node.getBody().accept(this);
        table.exitScope();
        node.setType(new VoidType());
    }

    @Override
    public void visit(RepeatStatement node) {
        table.enterScope();
        node.getBody().accept(this);
        table.exitScope();
        
        node.getCondition().accept(this);
        Type condType = node.getCondition().getType();

        if (!(condType instanceof BoolType)) {
            reportError(node.lineNumber(), node.charPosition(), "RepeatStat requires bool condition not " + shortName(condType) + ".");
        }
        node.setType(new VoidType());
    }

    @Override
    public void visit(ReturnStatement node) {
        // In main (not inside a function) a bare `return;` is allowed — no error.
        if (currentFunction == null) {
            node.setType(new VoidType());
            return;
        }

        Type expected = ((FuncType) currentFunction.type()).returnType();
        String fnName = (currentFunctionName != null) ? currentFunctionName : "<unknown>";

        if (expected instanceof VoidType) {
            if (node.getValue() != null) {
                node.getValue().accept(this);
                Type actual = node.getValue().getType();
                // legacy wording you want:
                reportError(node.lineNumber(), node.charPosition(),
                    "Function " + fnName + " returns " + actual + " instead of void.");
            }
        } else {
            if (node.getValue() == null) {
                reportError(node.lineNumber(), node.charPosition(),
                    "Function " + fnName + " must return a value of type " + expected + ".");
            } else {
                node.getValue().accept(this);
                Type actual = node.getValue().getType();
                Type ok = expected.assign(actual);
                if (ok instanceof ErrorType) {
                    reportError(node.lineNumber(), node.charPosition(),
                        "Function " + fnName + " returns " + actual + " instead of " + expected + ".");
                }
            }
        }

        node.setType(new VoidType());
    }

    @Override
    public void visit(StatementSequence node) {
        for (Statement s : node) {
            if (s != null) s.accept(this);
        }
        node.setType(new VoidType());
    }
    
    // FUNCTION-RELATED
    @Override
    public void visit(FunctionCall node) {
        // 1) type-check args and collect both TypeList + java list
        node.getArguments().accept(this);
        TypeList argTL = (TypeList) node.getArguments().getType();

        java.util.List<Type> argList = new java.util.ArrayList<>();
        for (Expression e : node.getArguments().getArguments()) {
            argList.add(e.getType());
        }

        String base = node.getIdentifier().getName();
        String mangled = mangleFunc(base, argList);

        Symbol funcSymbol;
        Type resultType;

        try {
            // exact overload
            funcSymbol = table.lookup(mangled);
            resultType = funcSymbol.type().call(argTL);
        } catch (Throwable notFound) {
            try {
                // fallback: base name (built-ins / non-overloaded insertions)
                funcSymbol = table.lookup(base);
                resultType = funcSymbol.type().call(argTL);
            } catch (Throwable nf2) {
                reportError(node.lineNumber(), node.charPosition(),
                    "Function " + base + " not found.");
                node.setType(new ErrorType("Function not found."));
                return;
            }
        }

        if (resultType instanceof ErrorType) {
            // Force the exact legacy text (no TypeList(...) anywhere)
            String argsStr = fmtArgs(argList); // e.g. "(float, int)"
            String msg = "Call with args " + argsStr + " matches no function signature.";
            reportError(node.lineNumber(), node.charPosition(), msg);
            node.setType(new ErrorType(msg));  // overwrite the ErrorType so downstream prints our message
            return;
        }

        node.setType(resultType);
        node.getIdentifier().setSymbol(funcSymbol);
    }

    @Override
    public void visit(ArgumentList node) {
        TypeList argTypes = new TypeList();
        for (Expression expr : node.getArguments()) {
            expr.accept(this);
            // Change this line from .add() to .append()
            argTypes.append(expr.getType());
        }
        node.setType(argTypes);
    }
    
    @Override
    public void visit(FunctionDeclaration node) {
        // Build types (ensures TypeNodes are set)
        TypeList paramTL = new TypeList();
        List<Type> paramList = new ArrayList<>();
        for (FormalParameter p : node.getParameters()) {
            p.getTypeNode().accept(this);
            Type pt = p.getTypeNode().getType();
            paramTL.append(pt);
            paramList.add(pt);
        }
        node.getReturnType().accept(this);
        Type ret = node.getReturnType().getType();

        String base = node.getIdentifier().getName();
        String mangled = mangleFunc(base, paramList);

        // Lookup predeclared
        Symbol funcSymbol;
        try {
            funcSymbol = table.lookup(mangled);
        } catch (Throwable miss) {
            // fallback: insert now (shouldn't happen if predecl worked)
            try { funcSymbol = table.insert(mangled, new FuncType(paramTL, ret)); }
            catch (Throwable e) { reportError(node.lineNumber(), node.charPosition(), e.getMessage()); return; }
        }

        node.getIdentifier().setSymbol(funcSymbol);
        this.currentFunction = funcSymbol;
        this.currentFunctionName = node.getIdentifier().getName();

        // function scope: declare params by plain names
        table.enterScope();
        for (FormalParameter p : node.getParameters()) {
            try {
                Symbol ps = table.insert(p.getIdentifier().getName(), p.getTypeNode().getType());
                p.getIdentifier().setSymbol(ps);
            } catch (Throwable e) {
                reportError(p.lineNumber(), p.charPosition(), e.getMessage());
            }
        }

        node.getBody().accept(this);
        table.exitScope();

        this.currentFunction = null;
        this.currentFunctionName = null;
        node.setType(new VoidType());
    }

    @Override
    public void visit(FunctionBody node) {
        node.getDeclarations().accept(this);
        node.getStatements().accept(this);
        node.setType(new VoidType());
    }

    // DECLARATIONS
    @Override
    public void visit(DeclarationList node) {
        for (Declaration d : node) {
            d.accept(this);
        }
        node.setType(new VoidType());
    }

    @Override
    public void visit(VariableDeclaration node) {
        // First, visit the type node
        node.getTypeNode().accept(this);
        Type varType = node.getTypeNode().getType();

        // Check for invalid array sizes
        AST.Identifier id = node.getIdentifier();
        checkArrayDimensions(varType, id.lineNumber(), id.charPosition(), id.getName());

        try {
            Symbol sym = table.insert(node.getIdentifier().getName(), varType);
            node.getIdentifier().setSymbol(sym);
        } catch (Throwable e) {
            reportError(node.lineNumber(), node.charPosition(), e.getMessage());
        }

        // Variable declarations themselves do not have a runtime type
        node.setType(new VoidType());
    }

    // Recursive check for multidimensional arrays
    private void checkArrayDimensions(Type type, int line, int col, String varName) {
        if (type instanceof ArrayType) {
            ArrayType arr = (ArrayType) type;
            int extent = arr.getExtent();

            if (extent <= 0) {
                reportError(line, col, "Array " + varName + " has invalid size " + extent + ".");
            }

            // Recurse into base type in case of multidimensional arrays
            checkArrayDimensions(arr.getBase(), line, col, varName);
        }
    }
    
    @Override
    public void visit(Identifier node) {
        try {
            Symbol symbol = table.lookup(node.getName());
            node.setSymbol(symbol);
            node.setType(symbol.type());
        } catch (Throwable e) {
            reportError(node.lineNumber(), node.charPosition(), "Symbol not found: " + node.getName());
            node.setType(new ErrorType("Symbol not found."));
        }
    }
    
    @Override
    public void visit(AST.TypeNode typeNode) {
        // Example: propagate the type
        typeNode.setType(typeNode.getActualType());
    }
    
    @Override
    public void visit(UnaryMinus unaryMinus) {
        // Typecheck the inner expression
        unaryMinus.getExpr().accept(this);

        // Check if the type is numeric
        Type exprType = unaryMinus.getExpr().getType();
        if (!(exprType instanceof IntType || exprType instanceof FloatType)) {
            unaryMinus.setType(new ErrorType("Unary minus applied to non-numeric type"));
        } else {
            unaryMinus.setType(exprType);
        }
    }
}