///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.processing:preprocessor:4.4.4
//DEPS info.picocli:picocli:4.7.5
//REPOS central,https://jogamp.org/deployment/maven

//JAVA 22+


import java.io.IOException;
import java.io.OutputStreamWriter;
import java.math.BigInteger;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import processing.mode.java.preproc.PdePreprocessor;
import processing.mode.java.preproc.SketchException;
import picocli.CommandLine;
import picocli.CommandLine.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(
    name = "pderun",
    mixinStandardHelpOptions = true,
    version = "1.0",
    description = "Process and run Processing sketches"
)
class pderun implements Callable<Integer> {
    
    @Parameters(
        index = "0",
        description = "Source to process. Can be: '-' for stdin, 'pde://sketch/base64/...' URL, or directory path",
        arity = "0..1"
    )
    private String source;
    
    @Option(
        names = {"--stdin", "-s"},
        description = "Read source from stdin"
    )
    private boolean readFromStdin = false;
    
    @Option(
        names = {"--verbose", "-v"},
        description = "Enable verbose output"
    )
    private boolean verbose = false;

    /** 
     * Describes a processing Dev environment
     */
    static class PDEData {
        String source;
        Map<String, String> extraSources = new HashMap<>();
        Map<String, String> files = new HashMap<>();
        
        public void addFile(String filename, String fileurl) {
            files.put(filename,fileurl);
        }

        String filesDirectives() {
            StringBuilder sb = new StringBuilder();
            for (var entry : files.entrySet()) {
                sb.append("//FILES ").append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
            }
            return sb.toString();
        }

        public String combinedSources() {

            // Concatenate extra sources to the main source before converting to Java
        if (!extraSources.isEmpty()) {
            System.err.println("Extra sources: " + extraSources.size());
            StringBuilder combined = new StringBuilder(source);
            for (var entry : extraSources.entrySet()) {
                combined.append("\n\n// ---- ").append(entry.getKey()).append(".pde ----\n");
                combined.append(entry.getValue());
            }
                return combined.toString();
            }
            return source;
        }
    }

    static PDEData decodePdeUrl(String url) {
        PDEData result = new PDEData();
        // Extract base64 part
        int base64Start = url.indexOf("base64/") + 7;
        int base64End = url.indexOf('?', base64Start);
        String base64;
        if (base64End > base64Start) {
            base64 = url.substring(base64Start, base64End);
        } else {
            base64 = url.substring(base64Start);
        }
        // Decode base64
        byte[] decodedBytes = java.util.Base64.getDecoder().decode(base64);
        result.source = new String(decodedBytes);

        // Parse query string manually to handle both encoded and decoded URLs
        int queryStart = url.indexOf('?');
        if (queryStart > 0) {
            String query = url.substring(queryStart + 1);
            String[] params = query.split("&");
            for (String param : params) {
                if (param.startsWith("data=")) {
                    String value = param.substring(5);
                    
                    // Support multiple files in one data=, separated by commas
                    result.files.putAll(getPairs(value));
                } else if (param.startsWith("pde=")) {
                    String value = param.substring(4);
                    // Support multiple pde files in one pde=, separated by commas

                    result.extraSources.putAll(getPairs(value,true));
                }
            }
        }
        return result;
    }

    private static Map<String, String> getPairs(String value) {
        return getPairs(value, false);
    }

    private static Map<String, String> getPairs(String value, boolean base64) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        String[] pairs = value.split(",");
        for (String pair : pairs) {
            int colonIdx = pair.indexOf(':');
            if (colonIdx > 0 && colonIdx < pair.length() - 1) {
                String filename = pair.substring(0, colonIdx);
                String fileurl = pair.substring(colonIdx + 1);
                if(base64) {
                    byte[] srcDecoded = java.util.Base64.getDecoder().decode(fileurl);
                    fileurl = new String(srcDecoded);
                }
                result.put(filename, fileurl);
            }
        }
        return result;
    }

    @Override
    public Integer call() throws IOException, SketchException, NoSuchAlgorithmException {
        String jbangDirectives = """
            //JAVA 22+
            //RUNTIME_OPTIONS --enable-native-access=ALL-UNNAMED
            //REPOS central,https://jogamp.org/deployment/maven
            //DEPS org.processing:preprocessor:4.4.4
            """;

        PDEData pdeData = new PDEData();

        // Handle different input sources
        if (readFromStdin || (source != null && source.equals("-"))) {
            // Read source from stdin
            if (verbose) {
                System.err.println("Reading from stdin...");
            }
            StringBuilder sb = new StringBuilder();
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }

            String input = sb.toString().trim();
            if (input.startsWith("pde://sketch/base64/")) {
                if (verbose) {
                    System.err.println("Processing PDE URL from stdin");
                }
                pdeData = decodePdeUrl(input);
            } else {
                pdeData.source = input;
            }
        } else if (source != null && source.startsWith("pde://sketch/base64/")) {
            if (verbose) {
                System.err.println("Processing PDE URL: " + source);
            }
            pdeData = decodePdeUrl(source);
        } else if (source != null && Files.isDirectory(Paths.get(source))) {
            if (verbose) {
                System.err.println("Processing directory: " + source);
            }
            String dir = source;
            String firstfile = null;
            pdeData = new PDEData();

            for (Path file : Files.list(Paths.get(dir)).toArray(Path[]::new)) {
                if (file.getFileName().toString().endsWith(".pde")) {
                    if(firstfile == null) {
                        firstfile = file.getFileName().toString();
                    }
                    pdeData.extraSources.put(file.getFileName().toString(), Files.readString(file));
                }
                if(file.getFileName().toString().equals("data") && Files.isDirectory(file)) {
                    for (Path datafile : Files.list(file).toArray(Path[]::new)) {
                        pdeData.addFile(datafile.getFileName().toString(), "data/" + datafile.getFileName().toString());
                    }
                }
            }
            if(firstfile != null) {
                pdeData.source = pdeData.extraSources.get(firstfile);
                pdeData.extraSources.remove(firstfile);
            }
            
        } else if (source == null && !readFromStdin) {
            System.err.println("No source provided. Use --help for usage information.");
            return 1;
        } else if (source != null) {
            System.err.println("Invalid source: " + source);
            return 1;
        }
        
        String finalSource = pdeData.combinedSources();

        var writer = new OutputStreamWriter(System.out);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(finalSource.getBytes(StandardCharsets.UTF_8));
        String sha256hex = String.format("%064x", new BigInteger(1, hash));
        writer.write(jbangDirectives + pdeData.filesDirectives() + "\n\n");
        PdePreprocessor.builderFor("processingApp" + sha256hex).setTabSize(2).build().write(writer, finalSource);
        writer.close();
        
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new pderun()).execute(args);
        System.exit(exitCode);
    }
}

