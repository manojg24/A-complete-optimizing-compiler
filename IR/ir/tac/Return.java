package ir.tac;

public class Return extends TAC{
    
    private final Variable var;

    public Return(int id, Variable v){
        super(id);
        this.var = v;
    }

    public Variable value(){ return var; }

    @Override public String toString(){ return "return " + (var==null?"":var.toString()); }

    @Override public void accept(TACVisitor v){ v.visit(this); }
}