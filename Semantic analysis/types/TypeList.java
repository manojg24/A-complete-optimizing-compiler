package types;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TypeList extends Type {

    // It CONTAINS a list (composition)
    private final ArrayList<Type> list;

    public TypeList() {
        this.list = new ArrayList<>();
    }

    // Public methods to interact with the internal list
    public void append(Type type) {
        list.add(type);
    }

    public Type get(int index) {
        return list.get(index);
    }
    
    public int size() {
        return list.size();
    }
    
    @Override
    public boolean equivalent(Type that) {
        if (!(that instanceof TypeList)) {
            return false;
        }
        TypeList other = (TypeList) that;
        if (this.size() != other.size()) {
            return false;
        }
        for (int i = 0; i < this.size(); i++) {
            if (!this.get(i).equivalent(other.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
    	StringBuilder sb = new StringBuilder("TypeList(");
        for (int i = 0; i < size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(get(i));
        }
        sb.append(")");
        return sb.toString();
    }
}