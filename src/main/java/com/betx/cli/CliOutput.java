package com.betx.cli;

import java.io.PrintStream;
import java.util.Collection;

public class CliOutput {
    private final PrintStream out;

    public CliOutput() {
        this(null);
    }

    public CliOutput(PrintStream out) {
        this.out = out;
    }

    public void println(String message) {
        stream().println(message);
    }

    public void println() {
        stream().println();
    }

    public void printlnAll(Collection<String> lines) {
        if (lines == null) {
            return;
        }
        lines.forEach(this::println);
    }

    private PrintStream stream() {
        return out == null ? System.out : out;
    }
}
