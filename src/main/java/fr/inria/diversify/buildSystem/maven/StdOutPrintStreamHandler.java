package fr.inria.diversify.buildSystem.maven;

import org.apache.maven.shared.invoker.PrintStreamHandler;

import java.io.PrintStream;

/**
 * Created by marodrig on 25/07/2015.
 */
public class StdOutPrintStreamHandler extends PrintStreamHandler {

    public StdOutPrintStreamHandler() {
        super();
    }

    public StdOutPrintStreamHandler(PrintStream out, boolean alwaysFlush) {
        super(out, alwaysFlush);
    }

    public void consumeLine(String line) {
        System.out.println(line);
        super.consumeLine(line);
    }

}
