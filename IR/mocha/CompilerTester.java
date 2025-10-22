package mocha;

import java.io.*;
import java.util.*;
import org.apache.commons.cli.*;


public class CompilerTester {

    static final String GRAPH_DIR_NAME = "graphs";
    static final String CFG_DOT_FILE_NAME = "cfg.dot";

    public static void main(String[] args) {
        Options options = new Options();
        options.addRequiredOption("s", "src", true, "Source File");
        options.addOption("i", "in", true, "Data File");
        options.addOption("nr", "reg", true, "Num Regs");
        options.addOption("a", "astOut", false, "Print AST");
        // options.addOption("int", "interpret", false, "Interpreter mode");
        
        options.addOption("cfg", "cfg", true, "Print CFG.dot - requires graphs/");

        options.addOption("o", "opt", true, "Order-sensitive optimization -allowed to have multiple");
        options.addOption("loop", "convergence", false, "Run all optimization specified by -o until convergence");
        options.addOption("max", "maxOpt", false, "Run all optimizations till convergence");


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

        // Create graph dir if needed
        File dir = new File(GRAPH_DIR_NAME);
            if (!dir.exists()) {
                dir.mkdirs();
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

        
        mocha.Compiler c = new mocha.Compiler(s, numRegs);
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

        // if (cmd.hasOption("int")) { // Interpreter mode - at this point the program is well-formed
        //     c.interpret(in);
        // } else {
        //     System.out.println("Success type-checking file.");
        // }

        // Dot graph before optimization
        // For IR Visualizer
        String dotgraph_text = null;
        try {
            dotgraph_text = c.genIR(ast).asDotGraph();
            
            if (cmd.hasOption("cfg")) {
                String[] cfg_output_options = cmd.getOptionValues("cfg");
                
                for (String cfg_output: cfg_output_options) {
                    switch (cfg_output) {
                        case "screen":
                            System.out.println("Before optimization");
                            System.out.println("-".repeat(100));
                            System.out.println(dotgraph_text);
                            break;
                        case "file":
                            String filename = sourceFile.substring(0, sourceFile.lastIndexOf('.')) + "_"+ CFG_DOT_FILE_NAME;
                            try (PrintStream out = new PrintStream(GRAPH_DIR_NAME+File.pathSeparator+filename)) {               
                                out.print(dotgraph_text);
                            } catch (IOException e) {
                                e.printStackTrace();
                                System.err.println("Error accessing the cfg file: " + GRAPH_DIR_NAME + File.pathSeparator + filename);
                            }
                            break;
                        default:
                            break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Error caught - see stderr for stack trace " + e.getMessage());
            System.exit(-5);
        }

        // The next 3 lines are for Optimization - Comment/Uncomment them as needed
        String[] optArgs = cmd.getOptionValues("opt");
        List<String> optArguments = (optArgs!=null && optArgs.length != 0) ? Arrays.asList(optArgs) : new ArrayList<String>();
        dotgraph_text = c.optimization(optArguments, options.hasOption("loop"), options.hasOption("max"));
        // Dot graph after optimization
        System.out.println("After optimization");
        System.out.println("-".repeat(100));
        System.out.println(dotgraph_text);
        // we expect after this, there is file recording all transformations your compiler did
        // e.g., if we run -s test000.txt -o cp -o cf -o dce -loop
        // the file will have the name "record_test000_cp_cf_dce_loop.txt"
    }
}
