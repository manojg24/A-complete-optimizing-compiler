package ast;
import ast.AST.*;

public class PrettyPrinter implements NodeVisitor {

    private final StringBuilder sb = new StringBuilder();
    private int indent = 0;
    private final StringBuilder builder;
    public PrettyPrinter() {
        builder = new StringBuilder();
    }
    public String print(Node node) {
        builder.setLength(0); // reset
        node.accept(this);
        return builder.toString();
    }

    // Public method to start the printing process
    public String print(Visitable ast) {
        ast.accept(this);
        return sb.toString();
    }

    private void indent() {
        for (int i = 0; i < indent; i++) {
            sb.append("  ");
        }
    }

    private void println(String message) {
        indent();
        sb.append(message);
        sb.append("\n");
    }

    // The pattern for each visit is:
    // 1. Print the current node.
    // 2. Increase indent.
    // 3. Visit children.
    // 4. Decrease indent.

    @Override
    public void visit(Computation node) {
        println("Computation");
        indent++;
        node.variables().accept(this);
        node.functions().accept(this);
        node.mainStatementSequence().accept(this);
        indent--;
    }

    @Override
    public void visit(DeclarationList node) {
        // Don't print empty lists to keep the output clean
        if (node.isEmpty()) return;
        println("DeclarationList");
        indent++;
        for (Declaration d : node) {
            d.accept(this);
        }
        indent--;
    }
    
    @Override
    public void visit(StatementSequence node) {
        if (node.isEmpty()) return;
        println("StatementSequence");
        indent++;
        for (Statement s : node) {
            s.accept(this);
        }
        indent--;
    }

    @Override
    public void visit(VariableDeclaration node) {
        println("VariableDeclaration(" + node.getIdentifier().getName() + ")");
    }

    @Override
    public void visit(FunctionDeclaration node) {
        println("FunctionDeclaration(" + node.getIdentifier().getName() + ")");
        indent++;
        node.getBody().accept(this);
        indent--;
    }
    
    @Override
    public void visit(FunctionBody node) {
        println("FunctionBody");
        indent++;
        node.getDeclarations().accept(this);
        node.getStatements().accept(this);
        indent--;
    }

    @Override
    public void visit(Assignment node) {
        println("Assignment");
        indent++;
        node.getDestination().accept(this);
        node.getSource().accept(this);
        indent--;
    }

    @Override
    public void visit(IfStatement node) {
        println("IfStatement");
        indent++;
        node.getCondition().accept(this);
        node.getThenBlock().accept(this);
        if (node.getElseBlock() != null && !node.getElseBlock().isEmpty()) {
            println("ElseBlock");
            indent++;
            node.getElseBlock().accept(this);
            indent--;
        }
        indent--;
    }
    
    @Override
    public void visit(WhileStatement node) {
        println("WhileStatement");
        indent++;
        node.getCondition().accept(this);
        node.getBody().accept(this);
        indent--;
    }

    @Override
    public void visit(ReturnStatement node) {
        println("ReturnStatement");
        if (node.getValue() != null) {
            indent++;
            node.getValue().accept(this);
            indent--;
        }
    }

    @Override
    public void visit(FunctionCall node) {
        println("FunctionCall(" + node.getIdentifier().getName() + ")");
        indent++;
        node.getArguments().accept(this);
        indent--;
    }

    @Override
    public void visit(ArgumentList node) {
        println("ArgumentList");
        indent++;
        for (Expression arg : node.getArguments()) {
            arg.accept(this);
        }
        indent--;
    }

    @Override
    public void visit(Relation node) {
        println("Relation(" + node.getOperator() + ")");
        indent++;
        node.getLeft().accept(this);
        node.getRight().accept(this);
        indent--;
    }

    @Override
    public void visit(Addition node) {
        println("Addition");
        indent++;
        node.getLeft().accept(this);
        node.getRight().accept(this);
        indent--;
    }

    @Override
    public void visit(Subtraction node) {
        println("Subtraction");
        indent++;
        node.getLeft().accept(this);
        node.getRight().accept(this);
        indent--;
    }

    @Override
    public void visit(Multiplication node) {
        println("Multiplication");
        indent++;
        node.getLeft().accept(this);
        node.getRight().accept(this);
        indent--;
    }

    @Override
    public void visit(Division node) {
        println("Division");
        indent++;
        node.getLeft().accept(this);
        node.getRight().accept(this);
        indent--;
    }

    @Override
    public void visit(AddressOf node) {
        println("AddressOf(" + node.getIdentifier().getName() + ")");
    }
    
    @Override
    public void visit(ArrayIndex node) {
        println("ArrayIndex");
        indent++;
        node.getBase().accept(this);
        node.getIndex().accept(this);
        indent--;
    }
    
    @Override
    public void visit(IntegerLiteral node) {
        println("IntegerLiteral(" + node.getValue() + ")");
    }

    @Override
    public void visit(FloatLiteral node) {
        println("FloatLiteral(" + node.getValue() + ")");
    }
    
    @Override
    public void visit(BoolLiteral node) {
        println("BoolLiteral(" + node.getValue() + ")");
    }
    
    @Override
    public void visit(Identifier node) {
        System.out.print(node.getName());
    }
    
    @Override
    public void visit(TypeNode typeNode) {
        // For printing purposes, you can print the type's string representation
        if (typeNode.getType() != null) {
            builder.append(typeNode.getType().toString());
        } else {
            builder.append("nullType");
        }
    }

    // Add remaining stubs to satisfy the interface
    @Override public void visit(Dereference node) { println("Dereference"); }
    @Override public void visit(LogicalNot node) { println("LogicalNot"); }
    @Override public void visit(Power node) { println("Power"); }
    @Override public void visit(Modulo node) { println("Modulo"); }
    @Override public void visit(LogicalAnd node) { println("LogicalAnd"); }
    @Override public void visit(LogicalOr node) { println("LogicalOr"); }
    @Override public void visit(RepeatStatement node) { println("RepeatStatement"); }
}