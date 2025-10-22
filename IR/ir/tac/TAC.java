package ir.tac;

public abstract class TAC implements Visitable{
    
    private int id; // instruction id

    private boolean eliminated; // if this instruction is not needed by any optimization, 
                                // note: do not physically remove instructions

    protected TAC(int id) {
        this.id = id;
        this.eliminated = false;

        // saving code position will be helpful in debugging
    }
    public int id() { return id; }

    public boolean isEliminated() {
    	return eliminated; 
    	}
    public void markEliminated() {
    	this.eliminated = true;
    	}
    public void clearEliminated() {
    	this.eliminated = false;
    	}

    @Override public void accept(TACVisitor v) {
    	v.visit(this);
    	}

    @Override public String toString() {
    	return "#" + id;
    	}
}
