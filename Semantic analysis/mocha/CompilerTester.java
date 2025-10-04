package mocha;

import java.io.*;
import org.apache.commons.cli.*;


public class CompilerTester {

    public static void main(String[] args) {
        Options options = new Options();
        options.addRequiredOption("s", "src", true, "Source File");
        options.addOption("i", "in", true, "Data File");
        options.addOption("nr", "reg", true, "Num Regs"); // needed for interpreter mode
        options.addOption("a", "astOut", false, "Print AST");
        options.addOption("int", "interpret", false, "Interpreter mode");

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
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error accessing the code file: \"" + sourceFile + "\"");
            System.exit(-3);
        }

        InputStream in = System.in;
        if (cmd.hasOption("in")) {
            String inputFilename = cmd.getOptionValue("in");
            try {
                in = new FileInputStream(inputFilename);
            }
            catch (IOException e) {
                System.err.println("Error accessing the data file: \"" + inputFilename + "\"");
                System.exit(-2);
            }
        }

        String strNumRegs = cmd.getOptionValue("reg", "24");
        int numRegs = 24;
        try {
            numRegs = Integer.parseInt(strNumRegs);
            if (numRegs > 24) {
                System.err.println("reg num too large - setting to 24");
                numRegs = 24;
            }
            if (numRegs < 2) {
                System.err.println("reg num too small - setting to 2");
                numRegs = 2;
            }
        } catch (NumberFormatException e) {
            System.err.println("Error in option NumRegs -- reseting to 24 (default)");
            numRegs = 24;
        }

        
        Compiler c = new Compiler(s, numRegs);
        ast.AST ast = c.genAST();
        if (cmd.hasOption("a")) { // AST to Screen
            String ast_text = ast.printPreOrder();
            System.out.println(ast_text);
        }
        
        if (c.hasError()) {
            System.out.println("Error parsing file.");
            System.out.println(c.errorReport());
            System.exit(-8);
        }

        types.TypeChecker tc = new types.TypeChecker();

        if (!tc.check(ast)) {
            System.out.println("Error type-checking file.");
            System.out.println(tc.errorReport());
            System.exit(-4);
        }

        if (cmd.hasOption("int")) { // Interpreter mode - at this point the program is well-formed
            c.interpret(in);
        } else {
            System.out.println("Success type-checking file.");
        }
       
    }
}
