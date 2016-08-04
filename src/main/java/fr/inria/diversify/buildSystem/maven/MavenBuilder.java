package fr.inria.diversify.buildSystem.maven;


import fr.inria.diversify.buildSystem.AbstractBuilder;
//import fr.inria.diversify.util.logger;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.maven.shared.invoker.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * User: Simon
 * Date: 5/17/13
 * Time: 11:34 AM
 */
public class MavenBuilder extends AbstractBuilder {

    final static Logger logger = Logger.getLogger(MavenBuilder.class);

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Verbose output
     */
    private boolean verbose;

    public MavenBuilder(String directory, String srcDir) throws IOException {
        super(directory, srcDir);
    }

    protected void runPrivate() {

        logger.debug("run maven (timeout" + timeOut + ")");

        InvocationRequest request = new DefaultInvocationRequest();

        request.setPomFile(new File(directory + "/pom.xml"));

        List<String> l = new ArrayList<String>();

        for (String phase : phases)
            l.add(phase);
        request.setLocalRepositoryDirectory(setting);
        request.setGoals(l);

        MavenInvoker invoker = new MavenInvoker();
        //freebsd
        File mvnHome = new File("/usr/local/share/java/maven3");
        if (!mvnHome.exists())
            //ubuntu
            mvnHome = new File("/usr/share/maven");
        if (!mvnHome.exists())
            //osx
            mvnHome = new File("/usr/local/Cellar/maven/3.1.1/libexec/");
        if (!mvnHome.exists())
            //win
            mvnHome = new File(System.getenv("M2_HOME"));

        invoker.setMavenHome(mvnHome);
        invoker.setTimeOut(timeOut);

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream stream = new PrintStream(os);
        PrintStreamHandler psh = verbose ? new StdOutPrintStreamHandler(stream, true)
                : new PrintStreamHandler(stream, true);
        invoker.setOutputHandler(psh);
        invoker.setErrorHandler(psh);
        try {
            invoker.execute(request);
            String output = os.toString();
            if (getSaveOutputToFile()) { saveOutputToFile(output); }
            if (clojureTest)
                parseClojureResult(output);
            else
                parseResult(output);

        } catch (MavenInvocationException e) {
            logger.debug("Error in run Maven", e);
        }
        try {
            stream.close();
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //Tell the main thread that we are done
        latch.countDown();
    }

    /**
     * Parse status from the maven output
     * @param r
     */
    protected void parseResult(String r) {
        //Save r to further analysis
        MavenOutputParser parser = new MavenOutputParser();
        parser.setAcceptedErrors(acceptedErrors);
        parser.parse(r, "\n");
        errors = parser.getCompileErrors();
        failedTests = parser.getFailedTests();
        status = parser.getStatus();
    }


    protected void parseClojureResult(String r) {
        Integer tmpFailure = null;
        for (String s : r.split("\n")) {
            logger.debug(s);
            if (s.startsWith("[ERROR] COMPILATION ERROR")) {
                tmpFailure = -2;
                compileError = true;
            }
            if (s.startsWith("setCompile-clojure:")) {
                tmpFailure = -1;
            }
            if (s.contains("test:")) {
                tmpFailure = -1;
                allTestRun = true;
            }
            if (s.contains("[INFO] BUILD SUCCESS")) {
                allTestRun = true;
                tmpFailure = 0;
            }
        }
        status = tmpFailure;
    }

    public void initPom(String newPomFile) throws Exception {
        super.initPom(newPomFile);
        /*
        File failFastDir = new File(directory + "/" + srcDir + "/fr/inria/diversify/buildSystem/builder");
        FileUtils.forceMkdir(failFastDir);
        FileUtils.copyFileToDirectory(new File("src/main/java/fr/inria/diversify/transformation/builder/FailFastListener.java"), failFastDir);
        */
    }
}
