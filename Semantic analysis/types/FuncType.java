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
        if (!(args instanceof TypeList)) {
            return new ErrorType("Cannot call a function with a non-list type: " + args);
        }

        TypeList argTypes = (TypeList) args;

        // Use the equivalent method we defined in TypeList to check if the arguments match
        if (this.params.equivalent(argTypes)) {
            return this.returnType; // Success! Return the function's declared return type.
        }

        return new ErrorType("Call with args " + args + " does not match function signature " + this + ".");
    }

}
