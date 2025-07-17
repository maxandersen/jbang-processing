///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS org.assertj:assertj-core:3.27.2
//DEPS org.junit.jupiter:junit-jupiter-api:5.13.3
//DEPS org.junit.jupiter:junit-jupiter-engine:5.13.3
//DEPS org.junit.platform:junit-platform-launcher:1.13.3

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

//SOURCES pderun.java

import org.junit.jupiter.api.Test;

// Run this with junit:
// `jbang build genTest.java; jbang --java 22 cli@junit-team --class-path `jbang genTest.java` --scan-classpath
public class pderunTest {

    // Define each Unit test here and run them separately in the IDE
    @Test
    public void testParse() throws IOException {
        var result = pderun.decodePdeUrl(Files.readString(Paths.get("samples/singlefile.pdelink")));
        assertThat(result.source).contains("* Move the mouse left and right to change the field of view (fov).");

        assertThat(result.filesDirectives()).isEmpty();
        assertThat(result.extraSources).isEmpty();
    }   

    @Test
    public void testParseWithFiles() throws IOException {  

        var result = pderun.decodePdeUrl(Files.readString(Paths.get("samples/multiplefiles.pdelink")));

        assertThat(result.source).contains("// Use % to cycle through frames");
        assertThat(result.files).hasSize(12);
        assertThat(result.filesDirectives()).contains("//FILES PT_anim0000.gif=https://processing.org/static/28335b3c461476731fb51c761813d8a4/PT_anim0000.gif");
        assertThat(result.extraSources).isEmpty();
    }

    @Test
    public void testWithFilesAndSources() throws IOException {  

        var result = pderun.decodePdeUrl(Files.readString(Paths.get("samples/animatedsprite.pdelink")));

        assertThat(result.source).contains("Animated Sprite (Shifty + Teddy)");
        assertThat(result.files).hasSize(0);
        assertThat(result.extraSources).isEmpty();
    }

}
