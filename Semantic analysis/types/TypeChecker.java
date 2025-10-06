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
        
        if (leftType instanceof ErrorType || rightType instanceof ErrorType) {
            node.setType(new ErrorType("Propagated type error."));
            return;
        }

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

    @Override
    public void visit(Computation node) {
        // (0) Make globals visible to function bodies
        node.variables().accept(this);

        // (1) Predeclare all functions with both mangled and base names
        for (Declaration d : node.functions()) {
            if (d instanceof AST.FunctionDeclaration fd) {

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
    public void visit(ArrayIndex node) {
        // Type-check children first
        node.getBase().accept(this);
        node.getIndex().accept(this);

        Type baseType  = node.getBase().getType();
        Type indexType = node.getIndex().getType();

        // Prevent nulls from reaching Type#index(...)
        if (baseType == null)  baseType  = new ErrorType("Unresolved array base.");
        if (indexType == null) indexType = new ErrorType("Unresolved array index.");
        
        if (baseType instanceof ArrayType arr
                && indexType instanceof IntType
                && node.getIndex() instanceof AST.IntegerLiteral lit) {

                int extent = arr.getExtent();    // only meaningful when >= 0
                int idx    = lit.getValue();
                if (extent >= 0 && (idx < 0 || idx >= extent)) {
                	String arrName = extractRootIdent(node.getBase());
                	String msg = "Array Index Out of Bounds : " + idx +
                	             " for array " + (arrName != null ? arrName : "array");
                    // point exactly at the index literal
                    reportError(lit.lineNumber(), lit.charPosition(), msg);
                    node.setType(new ErrorType(msg));
                    return; // stop here; we've produced the specific error already
                }
            }

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
        // Power has special rules, handle it separately
        node.getBase().accept(this);
        node.getExponent().accept(this);
        Type baseType = node.getBase().getType();
        Type expType = node.getExponent().getType();

        if ((baseType.equivalent(new IntType()) || baseType.equivalent(new FloatType())) &&
            (expType.equivalent(new IntType()) || expType.equivalent(new FloatType()))) {
            node.setType(new FloatType()); // Result of power is usually float
        } else {
            reportError(node.lineNumber(), node.charPosition(), "Power operator requires numeric base and exponent.");
            node.setType(new ErrorType("Invalid types for power op."));
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
            reportError(node.lineNumber(), node.charPosition(), "IfStat requires a boolean condition, not " + condType + ".");
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
            reportError(node.lineNumber(), node.charPosition(), "WhileStat requires a bool condition not " + shortName(condType) + ".");
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
            reportError(node.lineNumber(), node.charPosition(), "RepeatStat requires a bool condition not " + shortName(condType) + ".");
        }
        node.setType(new VoidType());
    }

    @Override
    public void visit(ReturnStatement node) {
        if (currentFunction == null) {
            reportError(node.lineNumber(), node.charPosition(), "Return statement is not inside a function.");
            node.setType(new VoidType());
            return;
        }

        Type expectedReturnType = ((FuncType) currentFunction.type()).returnType();

        if (expectedReturnType instanceof VoidType) {
            if (node.getValue() != null) {
                reportError(node.lineNumber(), node.charPosition(), "Function " + currentFunction.name() + " should not return a value.");
            }
        } else {
            if (node.getValue() == null) {
                reportError(node.lineNumber(), node.charPosition(), "Function " + currentFunction.name() + " must return a value of type " + expectedReturnType + ".");
            } else {
                node.getValue().accept(this);
                Type actualReturnType = node.getValue().getType();
                Type resultType = expectedReturnType.assign(actualReturnType);
                if (resultType instanceof ErrorType) {
                    reportError(node.lineNumber(), node.charPosition(), "Function " + currentFunction.name() + " returns " + actualReturnType + " instead of " + expectedReturnType + ".");
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
            funcSymbol = table.lookup(mangled);              // exact overload
            resultType = funcSymbol.type().call(argTL);
        } catch (Throwable notFound) {
            try {
                funcSymbol = table.lookup(base);             // built-ins / non-overloaded fallback
                resultType = funcSymbol.type().call(argTL);
            } catch (Throwable nf2) {
                reportError(node.lineNumber(), node.charPosition(), "Function " + base + " not found.");
                node.setType(new ErrorType("Function not found."));
                return;
            }
        }

        if (resultType instanceof ErrorType) {
            reportError(node.lineNumber(), node.charPosition(), ((ErrorType) resultType).getMessage());
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
        this.currentFunctionName = base;

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