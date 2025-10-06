package mocha;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import types.*;

public class SymbolTable {

    // TODO: Create Symbol Table structure
	private Stack<Map<String, Symbol>> table;

	public SymbolTable () {
	    this.table = new Stack<>();
	    enterScope(); // Enter the global scope

	    try {
	        // Built-in: printInt(int)
	        TypeList printIntParams = new TypeList();
	        printIntParams.append(new IntType());
	        FuncType printIntType = new FuncType(printIntParams, new VoidType());
	        insert("printInt", printIntType);

	        // Built-in: printFloat(float)
	        TypeList printFloatParams = new TypeList();
	        printFloatParams.append(new FloatType());
	        FuncType printFloatType = new FuncType(printFloatParams, new VoidType());
	        insert("printFloat", printFloatType);

	        // Built-in: printBool(bool)
	        TypeList printBoolParams = new TypeList();
	        printBoolParams.append(new BoolType());
	        FuncType printBoolType = new FuncType(printBoolParams, new VoidType());
	        insert("printBool", printBoolType);

	        // Built-in: readInt(): int
	        FuncType readIntType = new FuncType(new TypeList(), new IntType());
	        insert("readInt", readIntType);

	        // Built-in: readFloat(): float
	        FuncType readFloatType = new FuncType(new TypeList(), new FloatType());
	        insert("readFloat", readFloatType);
	        
	    	// println(): void
	        FuncType printlnType = new FuncType(new TypeList(), new VoidType());
	        insert("println", printlnType);

	        // readBool(): bool
	        FuncType readBoolType = new FuncType(new TypeList(), new BoolType());
	        insert("readBool", readBoolType);

	    } catch (RedeclarationError e) {
	        // Should never happen with predefined functions
	        e.printStackTrace();
	    }
	}
    
    public void enterScope()
    {
        table.push(new HashMap<String, Symbol>());
    }

    /**
     * Exits the current scope by popping the top HashMap from the stack.
     */
    public void exitScope()
    {
        if (!table.isEmpty())
        {
            table.pop();
        }
    }

    // lookup name in SymbolTable
    public Symbol lookup (String name) throws SymbolNotFoundError {
    	for (int i = table.size() - 1; i >= 0; i--) {
            Map<String, Symbol> scope = table.get(i);
            if (scope.containsKey(name)) {
                return scope.get(name);
            }
        }
        // If we search all scopes and don't find it
        throw new SymbolNotFoundError(name);
    }
    
    public void insert(Symbol symbol) throws RedeclarationError {
        if (table.isEmpty()) {
            enterScope(); // Should not happen, but as a safeguard
        }

        Map<String, Symbol> currentScope = table.peek();
        String name = symbol.name();

        // Check for redeclaration ONLY in the current scope
        if (currentScope.containsKey(name)) {
            throw new RedeclarationError(name);
        }
        
        currentScope.put(name, symbol);
    }

    // insert name in SymbolTable
    public Symbol insert (String name, Type type) throws RedeclarationError {
    	Symbol s = new Symbol(name, type);
        insert(s);
        return s;
    }

}

class SymbolNotFoundError extends Error {

    private static final long serialVersionUID = 1L;
    private final String name;

    public SymbolNotFoundError (String name) {
        super("Symbol " + name + " not found.");
        this.name = name;
    }

    public String name () {
        return name;
    }
}

class RedeclarationError extends Error {

    private static final long serialVersionUID = 1L;
    private final String name;

    public RedeclarationError (String name) {
        super("Symbol " + name + " being redeclared.");
        this.name = name;
    }

    public String name () {
        return name;
    }
}
