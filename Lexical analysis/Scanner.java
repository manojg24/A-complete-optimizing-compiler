package mocha;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.io.IOException;

public class Scanner implements Iterator<Token> 
{
    private BufferedReader input;   // buffered reader to read file
    private boolean closed; // flag for whether reader is closed or not

    private int lineNum;    // current line number
    private int charPos;    // character offset on current line

    private String scan;    // current lexeme being scanned in
    private int nextChar;   // contains the next char (-1 == EOF)

    // reader will be a FileReader over the source file
    public Scanner (String sourceFileName, Reader reader) 
    {
        try 
        {
            this.input = new BufferedReader(reader);
            this.lineNum = 1;
            this.charPos = 0;
            this.closed = false;
            readChar(); // initialize nextChar
            advance(); // load first token
        } 
        catch (Exception e) 
        {
            Error("Failed to initialize Scanner", e);
        }
    }
    
    public Scanner(Reader reader)
    {
        this("unknown", reader);
    }

    // signal an error message
    public void Error (String msg, Exception e) 
    {
        System.err.println("Scanner: Line - " + lineNum + ", Char - " + charPos);
        if (e != null) 
        {
            e.printStackTrace();
        }
        System.err.println(msg);
    }

    /*
     * helper function for reading a single char from input
     * can be used to catch and handle any IOExceptions,
     * advance the charPos or lineNum, etc.
     */
    private int readChar () 
    {
        try 
        {
            int ch = input.read();
            nextChar = ch;
            charPos++;
            if (ch == '\n') 
            {
                lineNum++;
                charPos = 0;
            }
            return ch;
            } 
        catch (IOException e) 
        {
            nextChar = -1;
            return -1;
        }
    }
    /*
     * function to query whether or not more characters can be read
     * depends on closed and nextChar
     */
    private Token nextToken;
    @Override
    public boolean hasNext () 
    {
        return nextToken != null;
    }
    /*
     *	returns next Token from input
     */
    @Override
    public Token next () 
    {
    	if (nextToken == null) {
            throw new NoSuchElementException();
        }

        Token current = nextToken;

        if (current.kind == Token.Kind.EOF)
        {
            // after returning EOF, stop further iteration
            nextToken = null;
        }
        else
        {
            advance();  // load the next token
        }
        return current;
    }

    private void advance()
    {
        if(skipWhitespaceAndComments())
        {
        	return;
        }
        int tokenStartLine = lineNum;
        int tokenStartPos = charPos;
        if (nextChar == -1) 
        {
            nextToken = new Token(Token.Kind.EOF, "", tokenStartLine, tokenStartPos);
            return;
        }
        char c = (char) nextChar;
        scan = "";

    	// --- Numbers (integer or float, including negative) ---
        if (Character.isDigit(c))//|| (c == '-' && Character.isDigit(peekNextChar()))) 
        {
            StringBuilder sb = new StringBuilder();
            boolean isFloat = false;

//            // Handle negative
//            if (c == '-')
//            {
//                sb.append('-');
//                readChar();
//            }

            // Integer part
            while (nextChar != -1 && Character.isDigit((char) nextChar))
            {
                sb.append((char) nextChar);
                readChar();
            }

            // Fractional part (must have digits after '.')
            if (nextChar == '.') {
                // must be ".<digit>" to be a float
                if (Character.isDigit(peekNextChar()))
                {
                    isFloat = true;
                    sb.append('.');
                    readChar(); // consume '.'
                    while (nextChar != -1 && Character.isDigit((char) nextChar))
                    {
                        sb.append((char) nextChar);
                        readChar();
                    }
                } 
                else
                {
                    // malformed float like "13."
                    sb.append('.');   // include the dot in the same token
                    readChar();       // consume the '.'
                    nextToken = new Token(Token.Kind.ERROR,"Malformed float: " + sb.toString(), tokenStartLine, tokenStartPos);
                    return;
                }
            }

            // produce token
            nextToken = isFloat
                ? new Token(Token.Kind.FLOAT_VAL, sb.toString(), tokenStartLine, tokenStartPos)
                : new Token(Token.Kind.INT_VAL, sb.toString(), tokenStartLine, tokenStartPos);
            return;
        }
        
        // --- Identifiers / Keywords ---
        if (Character.isLetter(c))
        {
            scan = consumeIdentifier();
            Token.Kind kind = Token.lookupKind(scan);
            if (kind == null) kind = Token.Kind.IDENT;
            nextToken = new Token(kind, scan, tokenStartLine, tokenStartPos);
            return;
        }

        // --- Operators and punctuation (maximal munch) ---
        if (isOperator(c)) 
        {
            // consume operators (single or multi-character)
            String op = consumeOperator();
            Token.Kind kind = Token.lookupKind(op);
            if (kind == Token.Kind.ERROR) {
                Error("Unknown operator: " + op, null);
                nextToken = new Token(Token.Kind.ERROR, op, tokenStartLine, tokenStartPos);
            } else {
                nextToken = new Token(kind, op, tokenStartLine, tokenStartPos);
            }
            return;
        }
        
        // --- Unknown single character ---
        scan = Character.toString(c);
        readChar();
        nextToken = new Token(Token.Kind.ERROR, scan, tokenStartLine, tokenStartPos);
        return;
        
//        // EOF
//        if (nextChar == -1) {
//            nextToken = new Token(Token.Kind.EOF, "", lineNum, charPos);
//            return;
//        }
    }

    // Helpers
    private boolean isOperator(char c)
    {
        switch (c)
        {
            case '+':
            case '-':
            case '*':
            case '/':
            case '%':
            case '^':
            case '=':
            case '!':
            case '<':
            case '>':
            case ';':
            case ',':
            case ':':
            case '.':
            case '{':
            case '}':
            case '[':
            case ']':
            case '(':
            case ')':
                return true;
            default:
                return false;
        }
    }
    
    private int peekNextChar() 
    {
    	try 
    	{
    		input.mark(1);
    		int ch = input.read();
    		input.reset();
    		return ch;
    	} 
    	catch (IOException e) 
    	{
           return -1;
    	}
    }
    
    private boolean skipWhitespaceAndComments()
    {
        while (true)
        {
            // Skip whitespace
            while (nextChar != -1 && Character.isWhitespace((char) nextChar))
            {
                readChar();
            }

            // Skip line comments //
            if (nextChar == '/' && peekNextChar() == '/') {
                while (nextChar != -1 && nextChar != '\n') readChar();
                continue;
            }

            // Skip block comments /* ... */
            if (nextChar == '/' && peekNextChar() == '*')
            {
                readChar(); // consume '/'
                readChar(); // consume '*'
                while (true) {
                    if (nextChar == -1)
                    {
                        // EOF inside block comment → produce ERROR
                        nextToken = new Token(Token.Kind.ERROR, "ERROR", lineNum, charPos);
                        return true; // signal error
                    }
                    if (nextChar == '*' && peekNextChar() == '/')
                    {
                        readChar(); // consume '*'
                        readChar(); // consume '/'
                        break;
                    }
                    readChar();
                }
                continue;
            }
            break; // no more whitespace/comments
        }
        return false; // no error found
    }
    
    private String consumeDigits() 
    {
        StringBuilder sb = new StringBuilder();
        sb.append((char) nextChar);
        readChar();
        while (nextChar != -1 && Character.isDigit((char) nextChar)) 
        {
            sb.append((char) nextChar);
            readChar();
        }
        return sb.toString();
    }

    private String consumeIdentifier()
    {
        StringBuilder sb = new StringBuilder();

        // first char is guaranteed to be a letter (checked before calling)
        sb.append((char) nextChar);
        readChar();

        // allow letters, digits, underscores after the first char
        while (nextChar != -1 && (Character.isLetterOrDigit((char) nextChar) || (char) nextChar == '_'))
        {
            sb.append((char) nextChar);
            readChar();
        }
        return sb.toString();
    }

    private String consumeOperator() 
    {
        char first = (char) nextChar;
        readChar(); // move to next character

        if (nextChar != -1) 
        {
            String two = "" + first + (char) nextChar;
            if (Token.lookupKind(two) != Token.Kind.ERROR) 
            {  // check two-char operator
                char second = (char) nextChar;
                readChar(); // consume second character
                return "" + first + second;
            }
        }

        // single-character operator
        return "" + first;
    }
    public Token peekToken()
    {
        return nextToken;
    }
}