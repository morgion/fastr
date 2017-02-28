/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.truffle.r.test.library.base;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import com.oracle.truffle.r.test.TestBase;
import com.oracle.truffle.r.test.TestRBase;

// Checkstyle: stop line length check
public class TestConnections extends TestRBase {
    private static final class TestDir {
        private final Path testDirPath;

        TestDir() {
            testDirPath = TestBase.createTestDir("com.oracle.truffle.r.test.library.base.conn");
        }

        String[] subDir(String p) {
            return new String[]{testDirPath.resolve(p).toString()};
        }
    }

    private static TestDir testDir;
    private static Path tempFileGzip;
    private static final List<Path> TEMP_FILES = new ArrayList<>();

    @Override
    protected String getTestDir() {
        return "builtins/connection";
    }

    @BeforeClass
    public static void setup() throws IOException {
        testDir = new TestDir();

        Path path = Paths.get(System.getProperty("java.io.tmpdir"));

        // create a gzipped file
        tempFileGzip = path.resolve("gzipped_____5137528280012599068___.gz");
        OutputStream gzos = new GZIPOutputStream(Files.newOutputStream(tempFileGzip, StandardOpenOption.WRITE, StandardOpenOption.CREATE));
        gzos.write("Hello, World!".getBytes());
        gzos.close();

        TEMP_FILES.add(path.resolve("file3408688236"));
        TEMP_FILES.add(path.resolve("file4039819292"));
        TEMP_FILES.add(path.resolve("file9823674682"));
        TEMP_FILES.add(path.resolve("file3762346723"));
        TEMP_FILES.add(path.resolve("file8723674332"));
    }

    @AfterClass
    public static void teardown() {
        if (!deleteDir(testDir.testDirPath)) {
            System.err.println("WARNING: error deleting : " + testDir.testDirPath);
        }
        deleteFile(tempFileGzip);
        for (Path p : TEMP_FILES) {
            deleteFile(p);
        }
    }

    private static void deleteFile(Path p) {
        try {
            Files.delete(p);
        } catch (IOException e) {
            // ignore
        }
    }

    @Test
    public void testFileWriteReadLines() {
        assertEval(TestBase.template("{ writeLines(c(\"line1\", \"line2\"), file(\"%0\")) }", testDir.subDir("wl1")));
        assertEval(TestBase.template("{ readLines(file(\"%0\"), 2) }", testDir.subDir("wl1")));
        assertEval(TestBase.template("{ con <- file(\"%0\"); writeLines(c(\"line1\", \"line2\"), con) }", testDir.subDir("wl2")));
        assertEval(TestBase.template("{ con <- file(\"%0\"); readLines(con, 2) }", testDir.subDir("wl2")));
    }

    @Test
    public void testFileWriteReadChar() {
        assertEval(TestBase.template("{ writeChar(\"abc\", file(\"%0\")) }", testDir.subDir("wc1")));
        assertEval(TestBase.template("{ readChar(file(\"%0\"), 3) }", testDir.subDir("wc1")));
    }

    @Test
    public void testFileWriteReadBin() {
        assertEval(TestBase.template("{ writeBin(\"abc\", file(\"%0\", open=\"wb\")) }", testDir.subDir("wb1")));
        assertEval(TestBase.template("{ readBin(file(\"%0\", \"rb\"), 3) }", testDir.subDir("wb1")));
    }

    @Test
    public void testWriteTextReadConnection() {
        assertEval(Output.IgnoreErrorContext, "{ writeChar(\"x\", textConnection(\"abc\")) }");
    }

    @Test
    public void testTextReadConnection() {
        assertEval("{ con <- textConnection(c(\"1\", \"2\", \"3\",\"4\")); readLines(con) }");
        assertEval("{ con <- textConnection(c(\"1\", \"2\", \"3\",\"4\")); readLines(con, 2) }");
        assertEval("{ con <- textConnection(c(\"1\", \"2\", \"3\",\"4\")); readLines(con, 2); readLines(con, 2) }");
        assertEval("{ con <- textConnection(c(\"1\", \"2\", \"3\",\"4\")); readLines(con, 2); readLines(con, 2); readLines(con, 2) }");
    }

    @Test
    public void testPushBackTextConnection() {
        assertEval("{ con<-textConnection(c(\"a\",\"b\",\"c\",\"d\")); pushBackLength(con) }");
        assertEval("{ con<-textConnection(c(\"a\",\"b\",\"c\",\"d\")); pushBack(\"G\", con); pushBackLength(con) }");
        assertEval("{ con<-textConnection(c(\"a\",\"b\",\"c\",\"d\")); pushBack(\"G\", con); clearPushBack(con); pushBackLength(con) }");
        assertEval("{ con<-textConnection(c(\"a\",\"b\",\"c\",\"d\")); pushBack(\"G\", con); readLines(con, 1) }");
        assertEval("{ con<-textConnection(c(\"a\",\"b\",\"c\",\"d\")); pushBack(\"G\", con); readLines(con, 2) }");
        assertEval("{ con<-textConnection(c(\"a\",\"b\",\"c\",\"d\")); pushBack(\"G\", con, newLine=FALSE); readLines(con, 1) }");
        assertEval("{ con<-textConnection(c(\"a\",\"b\",\"c\",\"d\")); pushBack(\"G\", con, newLine=FALSE); readLines(con, 2) }");
        assertEval("{ con<-textConnection(c(\"a\",\"b\",\"c\",\"d\")); pushBack(c(\"G\", \"H\"), con); pushBackLength(con) }");
        assertEval("{ con<-textConnection(c(\"a\",\"b\",\"c\",\"d\")); pushBack(c(\"G\", \"H\"), con); readLines(con, 1) }");
        assertEval("{ con<-textConnection(c(\"a\",\"b\",\"c\",\"d\")); pushBack(c(\"G\", \"H\"), con); readLines(con, 2) }");
        assertEval("{ con<-textConnection(c(\"a\",\"b\",\"c\",\"d\")); pushBack(c(\"G\", \"H\"), con, newLine=FALSE); pushBackLength(con) }");
        assertEval("{ con<-textConnection(c(\"a\",\"b\",\"c\",\"d\")); pushBack(c(\"G\", \"H\"), con, newLine=FALSE); readLines(con, 1) }");
        assertEval("{ con<-textConnection(c(\"a\",\"b\",\"c\",\"d\")); pushBack(c(\"G\", \"H\"), con, newLine=FALSE); readLines(con, 2) }");
        assertEval("{ con<-textConnection(c(\"a\",\"b\",\"c\",\"d\")); pushBack(c(\"G\\nH\"), con); pushBackLength(con) }");
        assertEval("{ con<-textConnection(c(\"a\",\"b\",\"c\",\"d\")); pushBack(c(\"G\\nH\"), con); readLines(con, 1) }");
        assertEval("{ con<-textConnection(c(\"a\",\"b\",\"c\",\"d\")); pushBack(c(\"G\\nH\"), con); readLines(con, 2) }");
        assertEval("{ con<-textConnection(c(\"a\",\"b\",\"c\",\"d\")); pushBack(c(\"G\\nH\"), con, newLine=FALSE); pushBackLength(con) }");
        assertEval("{ con<-textConnection(c(\"a\",\"b\",\"c\",\"d\")); pushBack(c(\"G\\nH\"), con, newLine=FALSE); readLines(con, 1) }");
        assertEval("{ con<-textConnection(c(\"a\",\"b\",\"c\",\"d\")); pushBack(c(\"G\\nH\"), con, newLine=FALSE); readLines(con, 2) }");
    }

    @Test
    public void testWriteConnection() {
        assertEval("{ con <- textConnection(\"tcval\", open=\"w\"); writeLines(\"a\", con); tcval; close(con) }");
        assertEval("{ con <- textConnection(\"tcval\", open=\"w\"); writeLines(\"a\", con); writeLines(c(\"a\", \"b\"), con, sep=\".\"); tcval; close(con) }");
        assertEval("{ con <- textConnection(\"tcval\", open=\"w\"); writeLines(\"a\", con); writeLines(c(\"a\", \"b\"), con, sep=\".\"); writeLines(\"\", con); tcval; close(con) }");
        assertEval("{ con <- textConnection(\"tcval\", open=\"w\"); writeLines(\"a\\nb\", con); tcval; close(con) }");
        assertEval("c <- textConnection('out', 'w'); cat('testtext', file=c); isIncomplete(c); cat('testtext2\\n', file=c); isIncomplete(c); close(c); out");

        assertEval("{ d<-data.frame(c(1,2), c(10, 20)); buf<-character(); c<-textConnection(\"buf\", open=\"w\", local=T); write.table(d, c); buf }");
    }

    @Test
    public void testSeekTextConnection() {
        assertEval("{ zz <- textConnection(\"Hello, World!\"); res <- isSeekable(zz); close(zz); res }");
        assertEval(Output.IgnoreErrorMessage, "{ zz <- textConnection(\"Hello, World!\"); res <- seek(zz, 5); close(zz); res }");
    }

    @Test
    public void testFileSummary() {

        Assert.assertTrue("Could not create required temp file for test.", Files.exists(tempFileGzip));
        assertEval("{ zz <- file(\"" + tempFileGzip + "\", \"r\"); res <- summary(zz); close(zz); res }");
    }

    @Test
    public void testFileOpenRaw() {
        Assert.assertTrue("Could not create required temp file for test.", Files.exists(tempFileGzip));
        assertEval("{ zz <- file(\"" + tempFileGzip + "\", \"r\", raw=T); res <- readBin(zz, raw(), 4); close(zz); res }");
    }

    @Test
    public void testEncoding() throws IOException {

        // read from UTF-8 file
        final Path utf8File = TEMP_FILES.get(0);
        writeEncodedString(utf8File, "utf8", "This is a sentence using german Umlauts like ö, ä, ü, Ö, Ä, Ü but also ß.\n");
        assertEval(Ignored.ImplementationError, "fin <- file(\"" + utf8File + "\", \"r\", encoding = \"latin1\"); lines <- readLines(fin, 1); close(fin); lines");
        assertEval(Ignored.ImplementationError, "fin <- file(\"" + utf8File + "\", \"r\", encoding = \"UTF-8\"); lines <- readLines(fin, 1); close(fin); lines");

        // read from ISO-8859-1 (aka Latin1) file
        final Path latin1File = TEMP_FILES.get(1);
        writeEncodedString(latin1File, "ISO-8859-1", "This is a sentence using german Umlauts like ö, ä, ü, Ö, Ä, Ü but also ß.\n");
        assertEval(Ignored.Unknown, "fin <- file(\"" + latin1File + "\", \"r\"); lines <- readLines(fin, 1, encoding = \"UTF-8\"); close(fin); lines");
        assertEval(Ignored.ImplementationError, "fin <- file(\"" + latin1File + "\", \"r\", encoding = \"latin1\"); lines <- readLines(fin, 1); close(fin); lines");
        assertEval(Ignored.ImplementationError, "fin <- file(\"" + latin1File + "\", \"r\", encoding = \"UTF-8\"); lines <- readLines(fin, 1); close(fin); lines");

        // use inexisting charset
        assertEval(Output.IgnoreErrorContext, "fin <- file(\"" + utf8File + "\", \"r\", encoding = \"___inexistingCharSet___\")");

        // write UTF-8 file
        final Path utf8File1 = TEMP_FILES.get(2);
        assertEval("{ wline <- \"Hellö\"; fin <- file(\"" + utf8File1 +
                        "\", \"w+\", encoding = \"UTF-8\"); writeLines(wline, fin); seek(fin, 0); rline <- readLines(fin, 1); close(fin); c(wline, rline, wline == rline) }");
    }

    @Test
    public void testReadLines() {
        final Path tmpFile = TEMP_FILES.get(3);

        // one line containing '\0'
        final String lineWithNul = "c(97,98,99,100,0,101,10)";

        // two lines, first containing '\0'
        final String twoLinesOneNul = "c(97,98,99,100,0,101,10,65,66,67,10)";

        // one line containing '\0' and imcomplete
        final String lineWithNulIncomp = "c(97,98,99,100,0,101)";

        // two lines, first containing '\0', second line incomplete
        final String twoLinesOneNulIncomp = "c(97,98,99,100,0,101,10,65,66,67)";

        assertEval(Output.MayIgnoreWarningContext, TestBase.template("{ fn <- \"" + tmpFile +
                        "\"; zz <- file(fn,\"w+b\", blocking=%0); writeBin(as.raw(%1), zz, useBytes=T); seek(zz, 0); res <- readLines(zz, 2, warn=%2, skipNul=%3); close(zz); unlink(fn); res }",
                        LVAL, arr(lineWithNul, twoLinesOneNul, lineWithNulIncomp, twoLinesOneNulIncomp), LVAL, LVAL));
    }

    @Test
    public void testRawReadAppendText() {

        assertEval("{ rc <- rawConnection(raw(0), \"a+\"); close(rc); write(charToRaw(\"A\"), rc) }");
        assertEval("{ rv <- charToRaw(\"Hello\"); rc <- rawConnection(rv, \"a+\"); writeChar(\", World\", rc); res <- rawConnectionValue(rc); close(rc); res }");
        assertEval("{ rv <- charToRaw(\"Hello\"); rc <- rawConnection(rv, \"a+\"); writeChar(\", World\", rc); res <- rawToChar(rawConnectionValue(rc)); close(rc); res }");
        assertEval("{ rv <- charToRaw(\"Hello\"); rc <- rawConnection(rv, \"a+\"); write(charToRaw(\", World\"), rc); res <- rawConnectionValue(rc); close(rc); res }");
        assertEval("{ rv <- charToRaw(\"Hello\"); rc <- rawConnection(rv, \"a+\"); write(charToRaw(\", World\"), rc); res <- rawToChar(rawConnectionValue(rc)); close(rc); res }");
    }

    @Test
    public void testRawReadWriteText() {

        assertEval("{ rc <- rawConnection(raw(0), \"r+\"); close(rc); write(charToRaw(\"A\"), rc) }");
        assertEval("{ rv <- charToRaw(\"Hello\"); rc <- rawConnection(rv, \"r+\"); writeChar(\", World\", rc); res <- rawConnectionValue(rc); close(rc); res }");
        assertEval("{ rv <- charToRaw(\"Hello\"); rc <- rawConnection(rv, \"r+\"); writeChar(\", World\", rc); res <- rawToChar(rawConnectionValue(rc)); close(rc); res }");
        assertEval("{ rv <- charToRaw(\"Hello\"); rc <- rawConnection(rv, \"r+\"); write(charToRaw(\", World\"), rc); res <- rawConnectionValue(rc); close(rc); res }");
        assertEval("{ rv <- charToRaw(\"Hello\"); rc <- rawConnection(rv, \"r+\"); write(charToRaw(\", World\"), rc); res <- rawToChar(rawConnectionValue(rc)); close(rc); res }");
    }

    @Test
    public void testRawWriteText() {

        assertEval("{ s <- \"äöüß\"; rc <- rawConnection(raw(0), \"w\"); writeChar(s, rc); rawConnectionValue(rc) }");
        assertEval("{ rc <- rawConnection(raw(0), \"w\"); writeChar(\"Hello\", rc); writeChar(\", World\", rc); res <- rawConnectionValue(rc); close(rc); res }");
    }

    @Test
    public void testRawWriteBinary() {

        // this test is currently ignored, since 'charToRaw' is not compliant
        assertEval(Ignored.Unknown, "{ s <- \"äöüß\"; rc <- rawConnection(raw(0), \"wb\"); write(charToRaw(s), rc); res <- rawConnectionValue(rc); close(rc); res }");
        assertEval(Output.IgnoreWarningContext,
                        "{ zz <- rawConnection(raw(0), \"wb\"); x <- c(\"a\", \"this will be truncated\", \"abc\"); nc <- c(3, 10, 3); writeChar(x, zz, nc, eos = NULL); writeChar(x, zz, eos = \"\\r\\n\"); res <- rawConnectionValue(zz); close(zz); res }");
    }

    private static final String[] LVAL = arr("T", "F");

    private static String[] arr(String... args) {
        return args;
    }

    /**
     * Writes a string using the specified charset to the provided file. The file is created and
     * truncated.
     */
    private static void writeEncodedString(Path p, String enc, String s) throws IOException {
        ByteBuffer encode = Charset.forName(enc).encode(s);
        FileChannel open = FileChannel.open(p, CREATE, WRITE, TRUNCATE_EXISTING);
        open.write(encode);
        open.close();
    }
}
