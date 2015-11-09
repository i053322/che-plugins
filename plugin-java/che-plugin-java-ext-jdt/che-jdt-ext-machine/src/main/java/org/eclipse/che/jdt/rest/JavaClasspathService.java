/*******************************************************************************
 * Copyright (c) 2012-2015 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/

package org.eclipse.che.jdt.rest;

import org.eclipse.che.api.core.util.Cancellable;
import org.eclipse.che.api.core.util.CancellableProcessWrapper;
import org.eclipse.che.api.core.util.LineConsumer;
import org.eclipse.che.api.core.util.ProcessUtil;
import org.eclipse.che.api.core.util.StreamPump;
import org.eclipse.che.api.core.util.Watchdog;
import org.eclipse.che.ide.maven.tools.MavenUtils;
import org.eclipse.che.jdt.maven.MavenClasspathUtil;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jdt.core.IClasspathContainer;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.JavaModel;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * @author Evgen Vidolob
 */
@Path("jdt/{wsId}/classpath")
public class JavaClasspathService {
    private static final JavaModel JAVA_MODEL = JavaModelManager.getJavaModelManager().getJavaModel();
    private static final Logger    LOG        = LoggerFactory.getLogger(JavaClasspathService.class);

    @GET
    @Path("update")
    public boolean update(@QueryParam("projectpath") final String projectPath) throws JavaModelException {
        IJavaProject javaProject = JAVA_MODEL.getJavaProject(projectPath);
        File dir = new File(ResourcesPlugin.getPathToWorkspace() + projectPath);
        boolean success = dependencyUpdateProcessor(projectPath, dir);
        if (success) {
            try {
                IClasspathContainer container = MavenClasspathUtil.readMavenClasspath(javaProject);
                JavaCore.setClasspathContainer(container.getPath(), new IJavaProject[]{javaProject},
                                               new IClasspathContainer[]{container}, null);
                //TODO this is temp, remove when we will be use Project API
                JavaModelManager.getIndexManager().indexAll(javaProject.getProject());
            } catch (JavaModelException e) {
                LOG.error(e.getMessage(), e);
                throw e;
            }
        }
        return success;
    }

    private boolean dependencyUpdateProcessor(final String projectPath, File dir) {
        String command = MavenUtils.getMavenExecCommand();

        ProcessBuilder classPathProcessBuilder = new ProcessBuilder().command(command,
                                                                              "dependency:build-classpath",
                                                                              "-Dmdep.outputFile=.codenvy/classpath.maven")
                                                                     .directory(dir)
                                                                     .redirectErrorStream(true);

        if (executeBuilderProcess(projectPath, classPathProcessBuilder)) {
            ProcessBuilder sourcesProcessBuilder = new ProcessBuilder().command(command,
                                                                                "dependency:sources",
                                                                                "-Dclassifier=sources")
                                                                       .directory(dir)
                                                                       .redirectErrorStream(true);
            return executeBuilderProcess(projectPath, sourcesProcessBuilder);
        } else {
            return false;
        }
    }

    private boolean executeBuilderProcess(final String projectPath, ProcessBuilder processBuilder) {
        StreamPump output = null;
        Watchdog watcher = null;
        int timeout = 10; //10 minutes
        int result = -1;
        try {
            Process process = processBuilder.start();

            watcher = new Watchdog("Maven classpath" + "-WATCHDOG", timeout, TimeUnit.MINUTES);
            watcher.start(new CancellableProcessWrapper(process, new Cancellable.Callback() {
                @Override
                public void cancelled(Cancellable cancellable) {
                    LOG.warn("Update dependency process has been shutdown due to timeout. Project: " + projectPath);
                }
            }));
            output = new StreamPump();
            output.start(process, LineConsumer.DEV_NULL);
            try {
                result = process.waitFor();
            } catch (InterruptedException e) {
                Thread.interrupted(); // we interrupt thread when cancel task
                ProcessUtil.kill(process);
            }
            try {
                output.await(); // wait for logger
            } catch (InterruptedException e) {
                Thread.interrupted(); // we interrupt thread when cancel task, NOTE: logs may be incomplete
            }
        } catch (IOException e) {
            LOG.error("", e);
        } finally {
            if (watcher != null) {
                watcher.stop();
            }
            if (output != null) {
                output.stop();
            }
        }
        return result == 0;
    }
}
