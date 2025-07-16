///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS org.processing:preprocessor:4.4.4
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
import java.util.Map;

import processing.mode.java.preproc.PdePreprocessor;
import processing.mode.java.preproc.SketchException;

class pderun {
    
    /** 
     * Describes a processing Dev environment
     */
    static class PDEData {
        String source;
        Map<String, String> extraSources = new HashMap<>();
        Map<String, String> files = new HashMap<>();
        
        public void addFile(String filename, String fileurl) {
            files.put(filename, fileurl);
        }

        String filesDirectives() {
            StringBuilder sb = new StringBuilder();
            for (var entry : files.entrySet()) {
                sb.append("//FILES ").append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
            }
            return sb.toString();
        }

        public String combinedSources() {
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

    public static void main(String[] args) throws IOException, SketchException, NoSuchAlgorithmException {
        PDEData pdeData = parseInput(args);
        String finalSource = pdeData.combinedSources();
        generateOutput(finalSource, pdeData);
    }

    private static PDEData parseInput(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("No source provided");
            System.exit(1);
        }

        if (args[0].equals("-")) {
            return parseFromStdin();
        } else if (args[0].startsWith("pde://sketch/base64/")) {
            return decodePdeUrl(args[0]);
        } else if (Files.isDirectory(Paths.get(args[0]))) {
            return parseFromDirectory(args[0]);
        } else {
            System.err.println("Invalid input format");
            System.exit(1);
            return null; // unreachable
        }
    }

    private static PDEData parseFromStdin() throws IOException {
        String input = readStdinContent();
        if (input.startsWith("pde://sketch/base64/")) {
            return decodePdeUrl(input);
        } else {
            PDEData pdeData = new PDEData();
            pdeData.source = input;
            return pdeData;
        }
    }

    private static String readStdinContent() throws IOException {
        StringBuilder sb = new StringBuilder();
        try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private static PDEData parseFromDirectory(String dir) throws IOException {
        PDEData pdeData = new PDEData();
        String firstFile = null;

        for (Path file : Files.list(Paths.get(dir)).toArray(Path[]::new)) {
            if (file.getFileName().toString().endsWith(".pde")) {
                if (firstFile == null) {
                    firstFile = file.getFileName().toString();
                }
                pdeData.extraSources.put(file.getFileName().toString(), Files.readString(file));
            }
            if (file.getFileName().toString().equals("data") && Files.isDirectory(file)) {
                addDataFiles(pdeData, file);
            }
        }

        if (firstFile != null) {
            pdeData.source = pdeData.extraSources.get(firstFile);
            pdeData.extraSources.remove(firstFile);
        }

        return pdeData;
    }

    private static void addDataFiles(PDEData pdeData, Path dataDir) throws IOException {
        for (Path datafile : Files.list(dataDir).toArray(Path[]::new)) {
            pdeData.addFile(datafile.getFileName().toString(), 
                           "data/" + datafile.getFileName().toString());
        }
    }

    static PDEData decodePdeUrl(String url) {
        PDEData result = new PDEData();
        String base64Content = extractBase64Content(url);
        result.source = decodeBase64ToString(base64Content);
        parseQueryParameters(url, result);
        return result;
    }

    private static String extractBase64Content(String url) {
        int base64Start = url.indexOf("base64/") + 7;
        int base64End = url.indexOf('?', base64Start);
        
        if (base64End > base64Start) {
            return url.substring(base64Start, base64End);
        } else {
            return url.substring(base64Start);
        }
    }

    private static String decodeBase64ToString(String base64) {
        byte[] decodedBytes = java.util.Base64.getDecoder().decode(base64);
        return new String(decodedBytes);
    }

    private static void parseQueryParameters(String url, PDEData result) {
        int queryStart = url.indexOf('?');
        if (queryStart <= 0) return;

        String query = url.substring(queryStart + 1);
        String[] params = query.split("&");
        
        for (String param : params) {
            if (param.startsWith("data=")) {
                parseDataParameter(param, result);
            } else if (param.startsWith("pde=")) {
                parsePdeParameter(param, result);
            }
        }
    }

    private static void parseDataParameter(String param, PDEData result) {
        String value = param.substring(5);
        String[] pairs = value.split(",");
        
        for (String pair : pairs) {
            parseFilePair(pair, result, true);
        }
    }

    private static void parsePdeParameter(String param, PDEData result) {
        String value = param.substring(4);
        String[] pairs = value.split(",");
        
        for (String pair : pairs) {
            parseFilePair(pair, result, false);
        }
    }

    private static void parseFilePair(String pair, PDEData result, boolean isDataFile) {
        int colonIdx = pair.indexOf(':');
        if (colonIdx <= 0 || colonIdx >= pair.length() - 1) return;

        String filename = pair.substring(0, colonIdx);
        String base64Content = pair.substring(colonIdx + 1);
        
        if (isDataFile) {
            String decodedContent = decodeBase64ToString(base64Content);
            result.addFile(filename, decodedContent);
        } else {
            System.err.println(filename + "=" + base64Content);
            String decodedContent = decodeBase64ToString(base64Content);
            result.extraSources.put(filename, decodedContent);
        }
    }

    private static void generateOutput(String finalSource, PDEData pdeData) throws IOException, NoSuchAlgorithmException, SketchException {
        String jbangDirectives = getJbangDirectives();
        String sha256Hash = calculateSha256Hash(finalSource);
        String output = buildOutput(jbangDirectives, pdeData, finalSource, sha256Hash);
        
        var writer = new OutputStreamWriter(System.out);
        writer.write(output);
        writer.close();
    }

    private static String getJbangDirectives() {
        return """
            //DEPS org.processing:preprocessor:4.4.4
            //JAVA 22+
            //RUNTIME_OPTIONS --enable-native-access=ALL-UNNAMED
            """;
    }

    private static String calculateSha256Hash(String content) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
        return String.format("%064x", new BigInteger(1, hash));
    }

    private static String buildOutput(String jbangDirectives, PDEData pdeData, String finalSource, String sha256Hash) throws IOException, SketchException {
        StringBuilder output = new StringBuilder();
        output.append(jbangDirectives);
        output.append(pdeData.filesDirectives());
        output.append("\n\n");
        
        // Use a temporary writer to capture the preprocessor output
        var stringWriter = new java.io.StringWriter();
        PdePreprocessor.builderFor("processingApp" + sha256Hash)
                      .setTabSize(2)
                      .build()
                      .write(stringWriter, finalSource);
        
        output.append(stringWriter.toString());
        return output.toString();
    }
}

