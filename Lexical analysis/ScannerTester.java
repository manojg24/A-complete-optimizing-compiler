package mocha;

import java.io.FileReader;
import java.io.IOException;

// IMPORTANT: You need to put commons-cli-1.9.0 jar file in lib/ in your classpath
import org.apache.commons.cli.*;

public class ScannerTester {

    public static void main (String[] args) {
        Options options = new Options();
        options.addRequiredOption("s", "src", true, "Source File");


        HelpFormatter formatter = new HelpFormatter();
        CommandLineParser cmdParser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = cmdParser.parse(options, args);
        } catch (ParseException e) {
            formatter.printHelp("All Options", options);
            System.exit(-1);
        }

        mocha.Scanner s = null;
        String sourceFile = cmd.getOptionValue("src");

        try {
            s = new mocha.Scanner(sourceFile, new FileReader(sourceFile));
        }
        catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error accessing the code file: \"" + sourceFile + "\"");
            System.exit(-2);
        }

        Token t;
        while (s.hasNext()) {
            t = s.next();
            System.out.print(t.kind);
            switch (t.kind) {
                case INT_VAL:
                case FLOAT_VAL:
                case IDENT:
                case ERROR:
                    System.out.println("\t" + t.lexeme());
                    break;
                default:
                    System.out.println();
                    break;
            }
        }
    }
}
