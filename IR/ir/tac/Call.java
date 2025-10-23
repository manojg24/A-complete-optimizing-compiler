package ir.tac;

import java.util.List;
import java.util.stream.Collectors;
import mocha.Symbol;

/** TAC for function call; optional destination holds the return value. */
public class Call extends TAC {

    private final Symbol function;
    private final List<Value> args;
    private final Variable dest; // null => void call

    /** Void call */
    public Call(int id, Symbol function, List<Value> args){
        this(id, function, args, null);
    }

    /** Call with return value in dest */
    public Call(int id, Symbol function, List<Value> args, Variable dest){
        super(id);
        this.function = function;
        this.args = args;
        this.dest = dest;
    }

    public Symbol function(){ return function; }
    public List<Value> args(){ return args; }
    public Variable dest(){ return dest; }

    @Override
    public String toString(){
        String as = (args==null) ? "" :
            args.stream().map(Object::toString).collect(Collectors.joining(", "));
        if (dest == null)
            return "call " + (function==null?"<unknown>":function.name()) + "(" + as + ")";
        return dest + " = call " + (function==null?"<unknown>":function.name()) + "(" + as + ")";
    }

    @Override public void accept(TACVisitor v){ v.visit(this); }
}