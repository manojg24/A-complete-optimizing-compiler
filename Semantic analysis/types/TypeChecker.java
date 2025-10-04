package types;

import ast.*;
import ast.AST.*;
import mocha.Symbol;
import mocha.SymbolTable;
import ast.NodeVisitor;
import java.util.List;

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

    // A helper for visiting binary expressions
    private void visitBinaryExpression(Expression node, Expression left, Expression right, String operation) {
        left.accept(this);
        right.accept(this);
        Type leftType = left.getType();
        Type rightType = right.getType();
        Type resultType;

        switch (operation) {
            case "+": resultType = leftType.add(rightType); break;
            case "-": resultType = leftType.sub(rightType); break;
            case "*": resultType = leftType.mul(rightType); break;
            case "/": resultType = leftType.div(rightType); break;
            case "%": resultType = leftType.div(rightType); break; // Modulo has same type rules as division
            case "&&": resultType = leftType.and(rightType); break;
            case "||": resultType = leftType.or(rightType); break;
            case "compare": resultType = leftType.compare(rightType); break;
            default: resultType = new ErrorType("Unknown binary operation: " + operation); break;
        }

        if (resultType instanceof ErrorType) {
            reportError(node.lineNumber(), node.charPosition(), ((ErrorType) resultType).getMessage());
        }
        node.setType(resultType);
    }

    @Override
    public void visit(Computation node) {
        node.variables().accept(this);
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
        } catch (Exception e) {
            reportError(node.lineNumber(), node.charPosition(), "Symbol not found: " + node.getIdentifier().getName());
            node.setType(new ErrorType("Symbol not found."));
        }
    }

    @Override
    public void visit(ArrayIndex node) {
        node.getBase().accept(this);
        node.getIndex().accept(this);
        Type baseType = node.getBase().getType();
        Type indexType = node.getIndex().getType();
        Type resultType = baseType.index(indexType);

        if (resultType instanceof ErrorType) {
            reportError(node.lineNumber(), node.charPosition(), ((ErrorType) resultType).getMessage());
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
            reportError(node.lineNumber(), node.charPosition(), "WhileStat requires a boolean condition, not " + condType + ".");
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
            reportError(node.lineNumber(), node.charPosition(), "RepeatStat requires a boolean condition, not " + condType + ".");
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
            s.accept(this);
        }
        node.setType(new VoidType());
    }
    
    // FUNCTION-RELATED
    @Override
    public void visit(FunctionCall node) {
        node.getArguments().accept(this);
        TypeList argTypes = (TypeList) node.getArguments().getType();

        try {
            Symbol funcSymbol = table.lookup(node.getIdentifier().getName());
            node.getIdentifier().setSymbol(funcSymbol);
            Type resultType = funcSymbol.type().call(argTypes);

            if (resultType instanceof ErrorType) {
                reportError(node.lineNumber(), node.charPosition(), ((ErrorType) resultType).getMessage());
            }
            node.setType(resultType);
        } catch (Exception e) {
            reportError(node.lineNumber(), node.charPosition(), "Function " + node.getIdentifier().getName() + " not found.");
            node.setType(new ErrorType("Function not found."));
        }
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
        TypeList paramTypes = new TypeList();
        for (FormalParameter param : node.getParameters()) {
            param.getTypeNode().accept(this);
            paramTypes.append(param.getTypeNode().getType());
        }
        node.getReturnType().accept(this);
        Type returnType = node.getReturnType().getType();
        FuncType functionType = new FuncType(paramTypes, returnType);

        try {
            Symbol funcSymbol = table.insert(node.getIdentifier().getName(), functionType);
            node.getIdentifier().setSymbol(funcSymbol);
            this.currentFunction = funcSymbol;
        } catch (Exception e) {
            reportError(node.lineNumber(), node.charPosition(), e.getMessage());
            this.currentFunction = null;
            return;
        }

        table.enterScope();
        for (FormalParameter param : node.getParameters()) {
            try {
                Symbol paramSymbol = table.insert(param.getIdentifier().getName(), param.getTypeNode().getType());
                param.getIdentifier().setSymbol(paramSymbol);
            } catch (Exception e) {
                reportError(param.lineNumber(), param.charPosition(), e.getMessage());
            }
        }

        node.getBody().accept(this);
        table.exitScope();
        this.currentFunction = null;
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
        node.getTypeNode().accept(this);
        Type varType = node.getTypeNode().getType();

        try {
            Symbol sym = table.insert(node.getIdentifier().getName(), varType);
            node.getIdentifier().setSymbol(sym);
        } catch (Exception e) {
            reportError(node.lineNumber(), node.charPosition(), e.getMessage());
        }
        node.setType(new VoidType());
    }
}