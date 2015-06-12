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
package org.eclipse.che.ide.extension.machine.client.outputspanel;

import com.google.gwt.user.client.ui.IsWidget;

import org.eclipse.che.ide.api.mvp.View;
import org.eclipse.che.ide.api.parts.base.BaseActionDelegate;

/**
 * View of {@link OutputsContainerPresenter}.
 *
 * @author Artem Zatsarynnyy
 */
public interface OutputsContainerView extends View<OutputsContainerView.ActionDelegate> {

    void addConsole(String title, IsWidget widget);

    /** Show console by the given index. */
    void showConsole(int index);

    void closeAllConsoles();

    /**
     * Set view's title.
     *
     * @param title
     *         new title
     */
    void setTitle(String title);

    interface ActionDelegate extends BaseActionDelegate {

        /** Called when console with the given {@code index} is selected. */
        void onConsoleSelected(int index);
    }
}