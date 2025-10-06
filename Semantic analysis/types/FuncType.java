package types;

public class FuncType extends Type {

    private TypeList params;
    private Type returnType;

    public FuncType(TypeList params, Type returnType) {
        this.params = params;
        this.returnType = returnType;
    }

    /**
     * Getter for the function's parameter types.
     * The TypeChecker will use this.
     */
    public TypeList arguments() {
        return params;
    }

    /**
     * Getter for the function's return type.
     * The TypeChecker will use this.
     */
    public Type returnType() {
        return returnType;
    }

    /**
     * This helps with debugging by creating a clean string representation.
     * For example: func(int)->void
     */
    @Override
    public String toString() {
        return "func(" + params + ")->" + returnType;
    }

    /**
     * This method is crucial for the TypeChecker to see if two function
     * types are identical (i.e., same parameters and same return type).
     */
    @Override
    public boolean equivalent(Type that) {
        if (!(that instanceof FuncType)) {
            return false;
        }
        FuncType other = (FuncType) that;
        return this.returnType.equivalent(other.returnType) && this.params.equivalent(other.params);
    }
    
    @Override
    public Type call(Type args) {
    	TypeList actuals;

        if (args instanceof TypeList) {
            actuals = (TypeList) args;
        } else {
            // Box a single Type (or any other object) into a 1-elem TypeList for the message
            actuals = new TypeList();
            if (args instanceof Type) {
                actuals.append((Type) args);
            } else {
                actuals.append(new ErrorType(String.valueOf(args)));
            }
            return new ErrorType("Call with args " + actuals + " matches no function signature.");
        }

        if (actuals.size() != this.params.size()) {
            return new ErrorType("Call with args " + actuals + " matches no function signature.");
        }

        // Check each parameter using assign(formal <- actual) so arrays like int[-1] match any length.
        for (int i = 0; i < params.size(); i++) {
            Type formal = params.get(i);
            Type actual = actuals.get(i);
            if (formal.assign(actual) instanceof ErrorType) {
                return new ErrorType("Call with args " + actuals + " matches no function signature.");
            }
        }
        return this.returnType;
    }

}
