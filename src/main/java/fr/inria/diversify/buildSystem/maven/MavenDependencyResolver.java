package fr.inria.diversify.buildSystem.maven;


//import fr.inria.diversify.util.Log;

import org.apache.log4j.Logger;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Repository;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.kevoree.resolver.MavenResolver;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * User: Simon
 * Date: 6/12/13
 * Time: 3:47 PM
 */
public class MavenDependencyResolver {

    final static Logger logger = Logger.getLogger(MavenDependencyResolver.class);

    private boolean fullResolve = false;

    public boolean isFullResolve() {
        return fullResolve;
    }

    private HashSet<String> resolved = new HashSet<>();

    /**
     * Indicates whether the path must be fully resolved or not. When the POM is fully resolved
     * the dependencies of its dependencies will be also added to the class path recursively.
     *
     * @param fullResolve True to fully resolve the POM
     */
    public void setFullResolve(boolean fullResolve) {
        this.fullResolve = fullResolve;
    }


    /**
     * Adds to the class path all dependencies of a project and the project itself
     *
     * @param pomFile
     * @throws Exception
     */
    public void resolveDependencies(String pomFile) throws Exception {
        MavenProject project = loadProject(new File(pomFile));
        resolveAllDependencies(project, true);
    }

    private MavenProject loadProject(File pomFile) throws IOException, XmlPullParserException {
        MavenXpp3Reader mavenReader = new MavenXpp3Reader();
        FileReader reader = new FileReader(pomFile);
        Model model = mavenReader.read(reader);
        model.setPomFile(pomFile);
        MavenProject ret = new MavenProject(model);
        reader.close();
        return ret;
    }

    private String artifactSignature(Dependency dependency, Properties properties) {
        StringBuilder sig = new StringBuilder(
                "mvn:" + resolveName(dependency.getGroupId(), properties) +
                        ":" + resolveName(dependency.getArtifactId(), properties));

        if (dependency.getVersion() != null)
            sig.append(":" + resolveName(dependency.getVersion(), properties));

        sig.append(":" + resolveName(dependency.getType(), properties));
        return sig.toString();
    }

    private File getArtifactPOM(MavenResolver resolver, Dependency dependency,
                                Properties properties, List<String> urls) {
        StringBuilder sig = new StringBuilder(
                "mvn:" + resolveName(dependency.getGroupId(), properties) +
                        ":" + resolveName(dependency.getArtifactId(), properties));
        if (dependency.getVersion() != null)
            sig.append(":" + resolveName(dependency.getVersion(), properties));
        sig.append(":pom");
        return resolver.resolve(sig.toString(), urls);
    }

    private void resolveAllDependencies(MavenProject project, boolean addSelf) throws IOException {
        MavenResolver resolver = new MavenResolver();
        resolver.setBasePath(System.getProperty("user.home") + File.separator + ".m2/repository");

        List<String> urls = new ArrayList<>(project.getDependencies().size());
        for (Repository repo : project.getRepositories()) {
            urls.add(repo.getUrl());
        }
        urls.add("http://repo1.maven.org/maven2/");

        List<URL> jarURL = new ArrayList<>();
        Properties properties = project.getProperties();
        for (Dependency dependency : project.getDependencies()) {
            try {
                //fullResolveDependency(dependency, project);
                String sig = artifactSignature(dependency, properties);
                if (!resolved.contains(sig)) {
                    addArtifactToJar(resolver, dependency, properties, jarURL, urls);
                    resolved.add(sig);
                    /*
                    try {
                        MavenProject child = loadProject(getArtifactPOM(resolver, dependency, properties, urls));
                        resolveAllDependencies(child, false);
                    } catch (Exception ex) {
                        logger.warn("Unable get child depenency of " + sig);
                    }*/
                }
            } catch (Exception ex) {
                logger.warn("Error resolving dependency " + ex.getMessage());
            }

        }

        if (addSelf) {
            try {
                Dependency projectItself = new Dependency();
                projectItself.setArtifactId(project.getArtifactId());
                projectItself.setGroupId(project.getGroupId());
                projectItself.setVersion(project.getVersion());
                projectItself.setType("jar");
                addArtifactToJar(resolver, projectItself, properties, jarURL, urls);
            } catch (Exception e) {
                logger.warn("Unable to add the project's jar to the class path");
            }
        }


        URLClassLoader child = new URLClassLoader(
                jarURL.toArray(new URL[jarURL.size()]), Thread.currentThread().getContextClassLoader());
        Thread.currentThread().setContextClassLoader(child);
    }

    /*
    private String getProperty(String k, MavenProject project) {
        if (k.startsWith("{$"))
            return project.getProperties().getProperty(k.substring(2, k.length() - 1));
        return k;
    }

    private void fullResolveDependency(Dependency dependency, MavenProject project) {
        dependency.setGroupId(getProperty(dependency.getGroupId(), project));
        dependency.setArtifactId(getProperty(dependency.getArtifactId(), project));
        dependency.setVersion(getProperty(dependency.getVersion(), project));
    }*/

    private File getPom(Dependency dependency) {
        return null;
    }

    private File addArtifactToJar(MavenResolver resolver, Dependency dependency,
                                  Properties properties, List<URL> jarURL, List<String> urls) throws MalformedURLException {
        String artifactId = artifactSignature(dependency, properties);
        logger.info("About to resolve: " + artifactId);
        File cachedFile = resolver.resolve(artifactId, urls);
        if (cachedFile != null) {
            URL url = cachedFile.toURI().toURL();
            jarURL.add(url);
            logger.info("Dependency resolved: " + url.toString());
        } else logger.warn("Unable to find artifact: " + artifactId);
        return cachedFile;
    }


    private String resolveName(String string, Properties properties) {
        char[] chars = string.toCharArray();
        int replaceBegin = -1;
        String id = "";
        for (int i = 0; i < chars.length; i++) {
            if (replaceBegin != -1 && chars[i] != '{' && chars[i] != '}') {
                id += chars[i];
            }
            if (replaceBegin != -1 && chars[i] == '}') {
                string = string.substring(0, replaceBegin) + properties.getProperty(id) + string.substring(i + 1, string.length());
                replaceBegin = -1;
                id = "";
            }
            if (chars[i] == '$' && i + 1 < chars.length && chars[i + 1] == '{') {
                replaceBegin = i;
            }
        }
        return string;
    }
}
