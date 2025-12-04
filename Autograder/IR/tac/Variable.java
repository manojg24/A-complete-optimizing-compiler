package ir.tac;

import mocha.Symbol;

public class Variable implements Value {

    private Symbol sym;
    
    public Variable(Symbol sym){
    	this.sym = sym;
    	}

    public Symbol symbol(){
    	return sym;
    	}

    @Override public String toString(){ return sym == null ? "<null>" : sym.name(); }

    @Override public void accept(TACVisitor visitor) {
    	visitor.visit(this);
    	}

    
}
