# pderun

`pderun` is a command-line tool for preprocessing Processing (.pde) sketches using the Processing 4 preprocessor, written to be run with [jbang](https://www.jbang.dev/).

## Requirements

- [Java 22+](https://adoptium.net/) (or a compatible JDK)
- [jbang](https://www.jbang.dev/download/)

## Running pderun

You can run `pderun.java` directly with jbang, without compiling manually:

```bash
jbang pderun.java [input]
```

## Usage

### Input Formats

pderun supports multiple input formats:

1. **Stdin input** - Read from standard input:
   ```bash
   echo "void setup() { size(400, 400); }" | jbang pderun - | jbang -
   ```

2. **Processing URL** - Parse Processing sketch URLs:
   ```bash
   jbang pderun.java "pde://sketch/base64/..." | jbang -
   ```

3. **Directory** - Process all .pde files in a directory:
   ```bash
   jbang pderun.java /path/to/sketch/directory
   ```

### Examples

**Basic sketch from stdin:**
```bash
echo 'void setup() { size(400, 400); background(255); }' | jbang pderun.java - | jbang - 
```

**Processing a directory with multiple .pde files:**
```bash
jbang pderun.java samples/ | jbang -
```

**Processing a URL-encoded sketch:**
```bash
jbang pderun.java "pde://sketch/base64/dm9pZCBzZXR1cCgpIHsgc2l6ZSg0MDAsIDQwMCk7IH0="
```

## Features

- **Processing 4 Preprocessor**: Uses the official Processing 4 preprocessor for accurate .pde to Java conversion
- **Multiple Input Sources**: Support for stdin, URLs, and directory processing
- **Multi-file Sketches**: Handles sketches with multiple .pde files
- **Data Files**: Automatically includes data files from `data/` directories
- **Base64 URL Support**: Decodes Processing sketch URLs with embedded base64 content
- **Jbang Integration**: Runs directly with jbang without compilation

## Output

The tool outputs a complete Java program that can be compiled and run. The output includes:

- Jbang directives for dependencies
- File directives for data files
- Preprocessed Java code from the Processing sketch

## Input Format Details

### Processing URLs

URLs in the format `pde://sketch/base64/[base64-content]?[parameters]` are supported with:

- `data=` parameter for additional data files
- `pde=` parameter for additional .pde files
- Multiple files separated by commas

### Directory Structure

When processing a directory, pderun expects:
- `.pde` files containing the sketch code
- `data/` directory with any data files
- The first `.pde` file becomes the main sketch

## Dependencies

- `org.processing:preprocessor:4.4.4` - Processing 4 preprocessor
- Java 22+ for modern language features

## License

This project is open source and available under the same license as the Processing project.
