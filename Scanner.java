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
        return nextToken != null && nextToken.kind != Token.Kind.EOF;
    }

    /*
     *	returns next Token from input
     *
     *  invariants:
     *  1. call assumes that nextChar is already holding an unread character
     *  2. return leaves nextChar containing an untokenized character
     *  3. closes reader when emitting EOF
     */
    @Override
    public Token next () 
    {
        if (!hasNext()) 
        {
            throw new NoSuchElementException();
        }
        Token current = nextToken;
        advance();
        return current;
    }

    private void advance() 
    {
        skipWhitespaceAndComments();
        if (nextChar == -1) 
        {
        	nextToken = new Token(Token.Kind.EOF, "", lineNum, charPos);
            return;
        }
        char c = (char) nextChar;
        scan = "";

        // Negative numbers (no space between '-' and digit)
        if (c == '-') 
        {
            readChar();
            if (Character.isDigit((char) nextChar)) 
            {
                scan = "-" + consumeDigits();
                nextToken = new Token(Token.Kind.INT_VAL, scan, lineNum, charPos);
                return;
            } 
            else 
            {
            	nextToken = new Token(Token.Kind.SUB, "-", lineNum, charPos);
                return;
            }
        }

        // Numbers
        if (Character.isDigit(c)) 
        {
            scan = consumeDigits();
            nextToken = new Token(Token.Kind.INT_VAL, scan, lineNum, charPos);
            return;
        }

        // Identifiers/Keywords
        if (Character.isLetter(c) || c == '_') 
        {
            scan = consumeIdentifier();
            Token.Kind kind = Token.lookupKind(scan);
            if (kind == null) kind = Token.Kind.IDENT;
            nextToken = new Token(kind, scan, lineNum, charPos);
            return;
        }

        // Operators and punctuation (maximal munch)
        String op = consumeOperator();
        if (op != null) 
        {
            Token.Kind kind = Token.lookupKind(op);
            if (kind == null) 
            {
                Error("Unknown operator: " + op, null);
                nextToken = new Token(Token.Kind.IDENT, op, lineNum, charPos);
            } 
            else 
            {
            	nextToken = new Token(kind, op, lineNum, charPos);
            }
            return;
        }

        // Unknown character
        scan = Character.toString(c);
        readChar();
        nextToken = new Token(Token.Kind.IDENT, scan, lineNum, charPos);
    }

    // Helpers
    private void skipWhitespaceAndComments() 
    {
        while (nextChar != -1) 
        {
            char c = (char) nextChar;
            if (Character.isWhitespace(c)) 
            {
                readChar();
                continue;
            }
            // line comment
            if (c == '/') 
            {
                try { input.mark(2); } 
                catch (IOException e) {}
                readChar();
                if (nextChar == '/') 
                {
                    // skip until end of line
                    while (nextChar != -1 && nextChar != '\n') readChar();
                    continue;
                } 
                else 
                {
                    try { input.reset(); } 
                    catch (IOException e) {}
                }
            }
            break;
        }
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
        sb.append((char) nextChar);
        readChar();
        while (nextChar != -1 && (Character.isLetterOrDigit((char) nextChar) || (char) nextChar == '_')) 
        {
            sb.append((char) nextChar);
            readChar();
        }
        return sb.toString();
    }

    private String consumeOperator() 
    {
        StringBuilder sb = new StringBuilder();
        sb.append((char) nextChar);
        readChar();
        if (nextChar != -1) 
        {
            String two = sb.toString() + (char) nextChar;
            if (Token.lookupKind(two) != null || two.equals("//")) 
            {
                sb.append((char) nextChar);
                readChar();
                return sb.toString();
            }
        }
        return sb.toString();
    }
}