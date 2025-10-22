package ir.tac;

import java.util.List;
import java.util.stream.Collectors;
import mocha.Symbol;

public class Call extends TAC{
    
    private final Symbol function;
    private final List<Value> args;

    public Call(int id, Symbol function, List<Value> args){
        super(id);
        this.function = function;
        this.args = args;
    }

    public Symbol function(){ return function; }
    public List<Value> args(){ return args; }

    @Override
    public String toString(){
        String as = (args==null) ? "" :
            args.stream().map(Object::toString).collect(Collectors.joining(", "));
        return "call " + (function==null?"<unknown>":function.name()) + "(" + as + ")";
    }

    @Override public void accept(TACVisitor v){ v.visit(this); }
}