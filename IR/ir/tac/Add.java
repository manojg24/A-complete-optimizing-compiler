package ir.tac;

public class Add extends Assign{
    // either do this way or blend the operator's meaning into Assign
	public Add(int id, Variable dest, Value left, Value right){
        super(id, dest, left, right);
    }
    @Override protected String op(){
    	return "add";
    	}
    @Override public void accept(TACVisitor v){
    	v.visit(this);
    	}
}
