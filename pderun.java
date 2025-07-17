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
                    String[] pairs = value.split(",");
                    for (String pair : pairs) {
                        int colonIdx = pair.indexOf(':');
                        if (colonIdx > 0 && colonIdx < pair.length() - 1) {
                            String filename = pair.substring(0, colonIdx);
                            String fileurl = pair.substring(colonIdx + 1);
                            byte[] srcDecoded = java.util.Base64.getDecoder().decode(fileurl);
                            String src = new String(srcDecoded);
                            result.addFile(filename, src);
                        }
                    }
                } else if (param.startsWith("pde=")) {
                    String value = param.substring(4);
                    // Support multiple pde files in one pde=, separated by commas
                    String[] pairs = value.split(",");
                    for (String pair : pairs) {
                        int colonIdx = pair.indexOf(':');
                        if (colonIdx > 0 && colonIdx < pair.length() - 1) {
                            String filename = pair.substring(0, colonIdx);
                            String base64src = pair.substring(colonIdx + 1);
                            System.err.println(filename + "=" + base64src);
                            byte[] srcDecoded = java.util.Base64.getDecoder().decode(base64src);
                            String src = new String(srcDecoded);
                            result.extraSources.put(filename, src);
                        }
                    }
                }
            }
        }
        return result;
    }

    public static void main(String[] args) throws IOException, SketchException, NoSuchAlgorithmException {
       
        String jbangDirectives = """
            //DEPS org.processing:preprocessor:4.4.4
            //JAVA 22+
            //RUNTIME_OPTIONS --enable-native-access=ALL-UNNAMED
            """;

        PDEData pdeData = new PDEData();

        if (args.length > 0 && args[0].equals("-")) {
            // Read source from stdin
            StringBuilder sb = new StringBuilder();
            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            }

            String input = sb.toString().trim();
            if (input.startsWith("pde://sketch/base64/")) {
                pdeData = decodePdeUrl(input);
            } else {
                pdeData.source = input;
            }
        } else if (args.length > 0 && args[0].startsWith("pde://sketch/base64/")) {
            pdeData = decodePdeUrl(args[0]);
        } else if (args.length > 0 && Files.isDirectory(Paths.get(args[0]))) {
            String dir = args[0];
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
            
        } else {
            System.err.println("No source provided");
            System.exit(1);
        }
        String finalSource = pdeData.combinedSources();

        var writer = new OutputStreamWriter(System.out);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(finalSource.getBytes(StandardCharsets.UTF_8));
        String sha256hex = String.format("%064x", new BigInteger(1, hash));
        writer.write(jbangDirectives + pdeData.filesDirectives() + "\n\n");
        PdePreprocessor.builderFor("processingApp" + sha256hex).setTabSize(2).build().write(writer, finalSource);
        writer.close();
    }
}

