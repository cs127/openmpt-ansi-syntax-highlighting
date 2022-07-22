import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

public class OMPTSyntaxHighlight {

    static final String HEADER = "ModPlug Tracker ";
    static final String[] FORMATS_M = {"MOD", " XM"};
    static final String[] FORMATS_S = {"S3M", " IT", "MPT"};
    static final int[] DEFAULT_COLORS = {7, 5, 4, 2, 6, 3, 1, 7};
    static final String NEWLINE = System.lineSeparator();


    public static void main(String[] args) {

        // Find the first non-option command-line argument
        int colorsArgIndex = 0;
        while (colorsArgIndex < args.length) {
            if (!args[colorsArgIndex].startsWith("-")) break;
            colorsArgIndex++;
        }

        // Get command-line options
        ArrayList<Character> options = getCLIOptions(Arrays.copyOf(args, colorsArgIndex));
        boolean help = options.contains('h');
        boolean useStdIn = options.contains('i');
        boolean useStdOut = options.contains('o');
        boolean autoMarkdown = options.contains('d');
        boolean reverse = options.contains('r');

        // Show help (and then exit) if the help option is provided
        if (help) {
            System.out.println("Usage: [EXEC] [OPTIONS] [COLORS]                                             ");
            System.out.println("                                                                             ");
            System.out.println("Options:                                                                     ");
            System.out.println("-h | --help    Help (display this screen)                                    ");
            System.out.println("-i             Read input from STDIN instead of clipboard                    ");
            System.out.println("-o             Write output to STDOUT instead of clipboard                   ");
            System.out.println("-d             Automatically wrap output in Markdown code block (for Discord)");
            System.out.println("-r             Reverse mode - removes syntax highlighting instead of adding  ");
            System.out.println("                                                                             ");
            System.out.println("Using the -d option does nothing if the -r option is enabled.                ");
            System.out.println("                                                                             ");
            System.out.println("Colors:                                                                      ");
            System.out.println("X,X,X,X,X,X,X,X  Each value from 0 to 15 (Discord only supports 0 to 7)      ");
            System.out.println("format: Default,Note,Instrument,Volume,Panning,Pitch,Global,ChannelSeparator ");
            System.out.println("if not provided: 7,5,4,2,6,3,1,7                                             ");
            System.exit(0);
        }

        // Use the first non-option command-line argument as the list of colors
        int[] colors = DEFAULT_COLORS;
        try {
            String[] colorsStr = args[colorsArgIndex].split(",");
            for (int c = 0; c < colors.length; c++) {
                colors[c] = Integer.parseInt(colorsStr[c]);
                if (colors[c] < 0 || colors[c] > 15) throw new NumberFormatException("Invalid color " + colors[c]);
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            if (!useStdOut) System.out.println("Colors not provided properly. Default colors will be used.");
        }

        // Prepare clipboard/STDIN
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        Scanner scanner = new Scanner(System.in);
        String data = "";

        // Read clipboard/STDIN
        if (useStdIn) {
            StringBuilder dataBuilder = new StringBuilder();
            while (scanner.hasNextLine()) dataBuilder.append(scanner.nextLine()).append(NEWLINE);
            data = dataBuilder.toString();
        } else {
            try {
                data = (String)clipboard.getData(DataFlavor.stringFlavor);
            } catch (UnsupportedFlavorException | IOException e) {
                System.out.println("Unable to read clipboard.");
                System.exit(1);
            }
        }

        // Try to get the module format and check if the data is valid OpenMPT pattern data
        String f = "";
        try {
            f = data.substring(HEADER.length(), HEADER.length() + 3);
            if (!data.startsWith(HEADER) || !(Arrays.asList(FORMATS_M).contains(f) || Arrays.asList(FORMATS_S).contains(f)))
                throw new IllegalArgumentException();
        } catch (RuntimeException e) {
            System.out.printf("%s does not contain OpenMPT pattern data.%n", useStdIn ? "STDIN" : "Clipboard");
            System.exit(2);
        }

        // Remove colors if the input is already syntax-highlighted
        data = data.replaceAll("\u001B\\[\\d+(;\\d+)*m", "");

        String result;

        // Add colors if reverse mode is not enabled
        if (!reverse) {
            StringBuilder resultBuilder = new StringBuilder();
            int relPos = -1;
            int color = -1;
            int previousColor = -1;
            for (int p = 0; p < data.length(); p++) {
                char c = data.charAt(p);
                if (c == '|') relPos = 0;
                if (relPos == 0) color = colors[7];                         // Channel separator
                if (relPos == 1) color = colors[getNoteColor(c)];           // Note
                if (relPos == 4) color = colors[getInstrumentColor(c)];     // Instrument
                if (relPos == 6) color = colors[getVolumeCmdColor(c)];      // Volume command
                if (relPos >= 9) {                                          // Effect command(s)
                    if (relPos % 3 == 0) color = colors[getEffectCmdColor(c, f)];
                    if (relPos % 3 != 0 && c == '.' && data.charAt(p - (relPos % 3)) != '.') c = '0';
                }
                if (color != previousColor) resultBuilder.append(getSGRCode(color));
                resultBuilder.append(c);
                previousColor = color;
                if (relPos >= 0) relPos++;
            }
            result = resultBuilder.toString();
        } else result = data;

        // Wrap in code block for Discord if specified
        if (autoMarkdown && !reverse) result = "```ansi" + NEWLINE + result + "```";

        // Write to clipboard/STDOUT
        if (useStdOut) System.out.print(result);
        else {
            StringSelection selection = new StringSelection(result);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                System.out.println("linux moment");
            }
        }

        System.exit(0);

    }

    static ArrayList<Character> getCLIOptions(String[] args) {
        ArrayList<Character> options = new ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith("-")) {
                if (!arg.startsWith("--")) for (int c = 1; c < arg.length(); c++) options.add(arg.charAt(c));
                else if (arg.equals("--help")) options.add('h');
            }
        }
        return options;
    }

    static String getSGRCode(int color) {
        int n = color + ((color < 8) ? 30 : 82);
        return String.format("\u001B[%dm", n);
    }

    static int getNoteColor(char c) {
        return !(c == ' ' || c == '.' || c == '=' || c == '~' || c == '^') ? 1 : 0;
    }

    static int getInstrumentColor(char c) {
        return !(c == ' ' || c == '.') ? 2 : 0;
    }

    static int getVolumeCmdColor(char c) {
        int color = 0;
        switch (c) {
            case 'a', 'b', 'c', 'd', 'v' -> color = 3; // Volume
            case 'l', 'p', 'r'           -> color = 4; // Panning
            case 'e', 'f', 'g', 'h', 'u' -> color = 5; // Pitch
        }
        return color;
    }

    static int getEffectCmdColor(char c, String f) {
        int color = 0;
        if (Arrays.asList(FORMATS_S).contains(f)) { // S3M/IT/MPTM
            switch (c) {
                case 'D', 'K', 'L', 'M', 'N', 'R' -> color = 3; // Volume
                case 'P', 'X', 'Y'                -> color = 4; // Panning
                case 'E', 'F', 'G', 'H', 'U'      -> color = 5; // Pitch
                case 'A', 'B', 'C', 'T', 'V', 'W' -> color = 6; // Global
            }
        } else if (Arrays.asList(FORMATS_M).contains(f)) { // MOD/XM
            switch (c) {
                case '5', '6', '7', 'A', 'C' -> color = 3; // Volume
                case '8', 'P', 'Y'           -> color = 4; // Panning
                case '1', '2', '3', '4', 'X' -> color = 5; // Pitch
                case 'B', 'D', 'F', 'G', 'H' -> color = 6; // Global
            }
        }
        return color;
    }

}
