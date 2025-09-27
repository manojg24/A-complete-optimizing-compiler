package mocha;

import java.io.*;

import org.apache.commons.cli.*;

public class ParserTester {

    public static void main (String[] args) {
        Options options = new Options();
        options.addRequiredOption("s", "src", true, "Source File");
        options.addOption("i", "in", true, "Data File");

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
            s = new mocha.Scanner(new FileReader(sourceFile));
        }
        catch (IOException e) {
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

        mocha.Parser p = new mocha.Parser(s, in);
        p.parse();
        if (p.hasError()) {
            System.out.println("Error parsing file.");
            System.out.println(p.errorReport());
            System.exit(-4);
        } else {
            System.out.println("Success");
        }
    }
}
