package fr.inria.diversify.buildSystem.ant;

import fr.inria.diversify.buildSystem.AbstractBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * User: Simon
 * Date: 02/12/13
 * Time: 11:54
 */
public class AntBuilder extends AbstractBuilder {
    protected String testTarget;
    public AntBuilder(String directory, String testTarget) {
        super(directory, null);
        this.testTarget = testTarget;
    }

    protected void runPrivate() {
        //Log.debug("run ant: sh script/runAnt.sh "+directory+ " "+testTarget);

        String[] command = {"sh", "script/runAnt.sh", directory,testTarget};
        ProcessBuilder probuilder = new ProcessBuilder( command );

        //You can set up your work directory
        probuilder.directory(new File(System.getProperty("user.dir")));

        try {
            Process process = probuilder.start();

            InputStream is = process.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line;

            String result = "";
            while ((line = br.readLine()) != null) {
                result += line + "\n";
            }

            process.waitFor();
            parseResult(result);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //TODO not working
    protected void parseResult(String r) {
        Pattern patternCompileError = Pattern.compile("\\s*\\[javac\\] (\\d+) error.*");
        Pattern patternJunitError = Pattern.compile("\\s*\\[junit\\]\\s* FAILED.*");
        Pattern patternJunitOK = Pattern.compile("\\s*BUILD SUCCESSFUL\\s*");
        for (String s : r.split("\n")) {
            //Log.debug(s);
            Matcher m = patternCompileError.matcher(s);
            if (m.matches()) {
                compileError = true;
                break;
            }
//            m = patternJunitError.matcher(s);
//            if ( m.matches()) {
            if(s.contains("[junit]") && s.contains("FAILED")) {
                status = -2;
                allTestRun = true;
               break;
            }
            m = patternJunitOK.matcher(s);
            if ( m.matches()) {
                status = 0;
                allTestRun = true;
                break;
            }
        }
        if(!compileError && status == -3)
            status = -3;
    }
}
