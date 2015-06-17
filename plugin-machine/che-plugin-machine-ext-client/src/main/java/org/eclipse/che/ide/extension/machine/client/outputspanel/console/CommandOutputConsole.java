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
package org.eclipse.che.ide.extension.machine.client.outputspanel.console;

import com.google.gwt.user.client.ui.AcceptsOneWidget;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.che.api.machine.shared.dto.event.MachineProcessEvent;
import org.eclipse.che.ide.api.notification.NotificationManager;
import org.eclipse.che.ide.extension.machine.client.OutputMessageUnmarshaller;
import org.eclipse.che.ide.extension.machine.client.command.CommandConfiguration;
import org.eclipse.che.ide.rest.DtoUnmarshallerFactory;
import org.eclipse.che.ide.util.loging.Log;
import org.eclipse.che.ide.websocket.MessageBus;
import org.eclipse.che.ide.websocket.WebSocketException;
import org.eclipse.che.ide.websocket.events.MessageHandler;
import org.eclipse.che.ide.websocket.rest.SubscriptionHandler;
import org.eclipse.che.ide.websocket.rest.Unmarshallable;

/**
 * Console for command output.
 *
 * @author Artem Zatsarynnyy
 */
public class CommandOutputConsole implements OutputConsole, OutputConsoleView.ActionDelegate {

    private final OutputConsoleView      view;
    private final MessageBus             messageBus;
    private final NotificationManager    notificationManager;
    private final DtoUnmarshallerFactory dtoUnmarshallerFactory;

    private final CommandConfiguration commandConfiguration;
    private final String               machineId;

    private String         outputChannel;
    private MessageHandler outputHandler;
    private boolean isFinished = false;

    @Inject
    public CommandOutputConsole(OutputConsoleView view,
                                MessageBus messageBus,
                                NotificationManager notificationManager,
                                DtoUnmarshallerFactory dtoUnmarshallerFactory,
                                @Assisted CommandConfiguration commandConfiguration,
                                @Assisted String machineId) {
        this.view = view;
        this.messageBus = messageBus;
        this.notificationManager = notificationManager;
        this.dtoUnmarshallerFactory = dtoUnmarshallerFactory;

        this.commandConfiguration = commandConfiguration;
        this.machineId = machineId;

        view.setDelegate(this);
        view.printCommandLine(commandConfiguration.toCommandLine());
    }

    @Override
    public void go(AcceptsOneWidget container) {
        container.setWidget(view);
    }

    @Override
    public CommandConfiguration getCommand() {
        return commandConfiguration;
    }

    @Override
    public String getTitle() {
        return commandConfiguration.getName();
    }

    @Override
    public void listenToOutput(String wsChannel) {
        outputChannel = wsChannel;
        outputHandler = new SubscriptionHandler<String>(new OutputMessageUnmarshaller()) {
            @Override
            protected void onMessageReceived(String result) {
                view.print(result, result.endsWith("\r"));
                view.scrollBottom();
            }

            @Override
            protected void onErrorReceived(Throwable exception) {
                wsUnsubscribe(outputChannel, this);
                notificationManager.showError(exception.getMessage());
            }
        };

        wsSubscribe(outputChannel, outputHandler);
    }

    @Override
    public void attachToProcess(final int pid) {
        final Unmarshallable<MachineProcessEvent> unmarshaller = dtoUnmarshallerFactory.newWSUnmarshaller(MachineProcessEvent.class);
        final String processStateChannel = "machine:process:" + machineId;
        final MessageHandler handler = new SubscriptionHandler<MachineProcessEvent>(unmarshaller) {
            @Override
            protected void onMessageReceived(MachineProcessEvent result) {
                if (pid == result.getProcessId()) {
                    switch (result.getEventType()) {
                        case STOPPED:
                        case ERROR:
                            isFinished = true;
                            wsUnsubscribe(processStateChannel, this);
                            wsUnsubscribe(outputChannel, outputHandler);
                    }
                }
            }

            @Override
            protected void onErrorReceived(Throwable exception) {
                isFinished = true;
                wsUnsubscribe(processStateChannel, this);
                wsUnsubscribe(outputChannel, outputHandler);
                notificationManager.showError(exception.getMessage());
            }
        };

        wsSubscribe(processStateChannel, handler);
    }

    private void wsSubscribe(String wsChannel, MessageHandler handler) {
        try {
            messageBus.subscribe(wsChannel, handler);
        } catch (WebSocketException e) {
            notificationManager.showError(e.getMessage());
        }
    }

    private void wsUnsubscribe(String wsChannel, MessageHandler handler) {
        try {
            messageBus.unsubscribe(wsChannel, handler);
        } catch (WebSocketException e) {
            Log.error(getClass(), e);
        }
    }

    @Override
    public boolean isFinished() {
        return isFinished;
    }
}
