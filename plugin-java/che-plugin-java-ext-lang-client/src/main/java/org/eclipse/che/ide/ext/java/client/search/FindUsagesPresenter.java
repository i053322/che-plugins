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

package org.eclipse.che.ide.ext.java.client.search;

import com.google.gwt.json.client.JSONParser;
import com.google.gwt.user.client.ui.AcceptsOneWidget;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.api.promises.client.PromiseError;
import org.eclipse.che.ide.api.notification.NotificationManager;
import org.eclipse.che.ide.api.parts.PartStackType;
import org.eclipse.che.ide.api.parts.WorkspaceAgent;
import org.eclipse.che.ide.api.parts.base.BasePresenter;
import org.eclipse.che.ide.api.project.tree.VirtualFile;
import org.eclipse.che.ide.commons.exception.ServerException;
import org.eclipse.che.ide.dto.DtoFactory;
import org.eclipse.che.ide.ext.java.client.JavaLocalizationConstant;
import org.eclipse.che.ide.ext.java.client.JavaResources;
import org.eclipse.che.ide.ext.java.client.projecttree.JavaSourceFolderUtil;
import org.eclipse.che.ide.ext.java.shared.dto.search.FindUsagesRequest;
import org.eclipse.che.ide.ext.java.shared.dto.search.FindUsagesResponse;
import org.eclipse.che.ide.jseditor.client.texteditor.TextEditor;
import org.eclipse.che.ide.rest.HTTPStatus;
import org.eclipse.che.ide.util.loging.Log;
import org.vectomatic.dom.svg.ui.SVGResource;

/**
 * Presenter for Find Usages tree
 *
 * @author Evgen Vidolob
 */
@Singleton
public class FindUsagesPresenter extends BasePresenter implements FindUsagesView.ActionDelegate {


    private WorkspaceAgent           workspaceAgent;
    private JavaLocalizationConstant localizationConstant;
    private FindUsagesView           view;
    private JavaSearchService        searchService;
    private DtoFactory               dtoFactory;
    private NotificationManager      manager;
    private JavaResources            resources;

    @Inject
    public FindUsagesPresenter(WorkspaceAgent workspaceAgent,
                               JavaLocalizationConstant localizationConstant,
                               FindUsagesView view,
                               JavaSearchService searchService,
                               DtoFactory dtoFactory,
                               NotificationManager manager,
                               JavaResources resources) {
        this.workspaceAgent = workspaceAgent;
        this.localizationConstant = localizationConstant;
        this.view = view;
        this.searchService = searchService;
        this.dtoFactory = dtoFactory;
        this.manager = manager;
        this.resources = resources;
        view.setDelegate(this);
    }

    @Override
    public String getTitle() {
        return localizationConstant.findUsagesPartTitle();
    }

    @Override
    public void setVisible(boolean visible) {
        view.setVisible(visible);
    }

    @Override
    public IsWidget getView() {
        return view;
    }

    @Override
    public String getTitleToolTip() {
        return null;
    }

    @Override
    public SVGResource getTitleSVGImage() {
        return resources.findUsages();
    }

    @Override
    public void go(AcceptsOneWidget container) {
        container.setWidget(view);
    }

    public void findUsages(TextEditor activeEditor) {

        VirtualFile virtualFile = activeEditor.getEditorInput().getFile();

        String projectPath = virtualFile.getProject().getProjectDescriptor().getPath();
        FindUsagesRequest request = dtoFactory.createDto(FindUsagesRequest.class);
        request.setFQN(JavaSourceFolderUtil.getFQNForFile(virtualFile));
        request.setProjectPath(projectPath);
        request.setOffset(activeEditor.getCursorOffset());

        Promise<FindUsagesResponse> promise = searchService.findUsages(request);
        promise.then(new Operation<FindUsagesResponse>() {
            @Override
            public void apply(FindUsagesResponse arg) throws OperationException {
                handleResponse(arg);
            }
        }).catchError(new Operation<PromiseError>() {
            @Override
            public void apply(PromiseError arg) throws OperationException {
                Throwable cause = arg.getCause();
                if (cause instanceof ServerException) {
                    handleError(((ServerException)cause).getHTTPStatus(), cause.getMessage());
                    return;
                }
                //in case websocket request
                if (cause instanceof org.eclipse.che.ide.websocket.rest.exceptions.ServerException) {
                    handleError(((org.eclipse.che.ide.websocket.rest.exceptions.ServerException)cause).getHTTPStatus(), cause.getMessage());
                    return;
                }
                Log.error(getClass(), arg);
                manager.showError(arg.getMessage());
            }
        });

    }

    private void handleError(int statusCode, String message) {
        if (statusCode == HTTPStatus.BAD_REQUEST) {
            manager.showInfo(JSONParser.parseLenient(message).isObject().get("message").isString().stringValue());
        } else {
            manager.showError(message);
        }
    }

    private void handleResponse(FindUsagesResponse response) {
        workspaceAgent.openPart(this, PartStackType.INFORMATION);
        workspaceAgent.setActivePart(this);
        view.showUsages(response);
    }
}