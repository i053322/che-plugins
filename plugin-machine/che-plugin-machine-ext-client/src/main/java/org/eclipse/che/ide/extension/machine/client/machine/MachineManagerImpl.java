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
package org.eclipse.che.ide.extension.machine.client.machine;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.api.machine.gwt.client.ExtServerStateController;
import org.eclipse.che.api.machine.gwt.client.MachineManager;
import org.eclipse.che.api.machine.gwt.client.MachineServiceClient;
import org.eclipse.che.api.machine.gwt.client.OutputMessageUnmarshaller;
import org.eclipse.che.api.machine.gwt.client.events.DevMachineStateEvent;
import org.eclipse.che.api.machine.gwt.client.events.DevMachineStateHandler;
import org.eclipse.che.api.machine.shared.dto.ChannelsDto;
import org.eclipse.che.api.machine.shared.dto.LimitsDto;
import org.eclipse.che.api.machine.shared.dto.MachineConfigDto;
import org.eclipse.che.api.machine.shared.dto.MachineDto;
import org.eclipse.che.api.machine.shared.dto.MachineSourceDto;
import org.eclipse.che.api.machine.shared.dto.MachineStateDto;
import org.eclipse.che.api.machine.shared.dto.event.MachineStatusEvent;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.api.workspace.gwt.client.WorkspaceServiceClient;
import org.eclipse.che.api.workspace.shared.dto.UsersWorkspaceDto;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.parts.PerspectiveManager;
import org.eclipse.che.ide.dto.DtoFactory;
import org.eclipse.che.ide.extension.machine.client.inject.factories.EntityFactory;
import org.eclipse.che.ide.extension.machine.client.machine.MachineStatusNotifier.RunningListener;
import org.eclipse.che.ide.extension.machine.client.machine.console.MachineConsolePresenter;
import org.eclipse.che.api.machine.gwt.client.events.MachineStartingEvent;
import org.eclipse.che.ide.extension.machine.client.machine.events.MachineStateEvent;
import org.eclipse.che.ide.extension.machine.client.machine.events.MachineStateHandler;
import org.eclipse.che.ide.extension.machine.client.watcher.SystemFileWatcher;
import org.eclipse.che.ide.rest.DtoUnmarshallerFactory;
import org.eclipse.che.ide.ui.loaders.initializationLoader.InitialLoadingInfo;
import org.eclipse.che.ide.util.loging.Log;
import org.eclipse.che.ide.websocket.MessageBus;
import org.eclipse.che.ide.websocket.MessageBusProvider;
import org.eclipse.che.ide.websocket.WebSocketException;
import org.eclipse.che.ide.websocket.rest.SubscriptionHandler;
import org.eclipse.che.ide.websocket.rest.Unmarshallable;
import org.eclipse.che.api.workspace.gwt.client.event.StartWorkspaceEvent;
import org.eclipse.che.api.workspace.gwt.client.event.StartWorkspaceHandler;

import static org.eclipse.che.api.machine.gwt.client.MachineManager.MachineOperationType.START;
import static org.eclipse.che.api.machine.gwt.client.MachineManager.MachineOperationType.RESTART;
import static org.eclipse.che.api.machine.gwt.client.MachineManager.MachineOperationType.DESTROY;
import static org.eclipse.che.ide.ui.loaders.initializationLoader.InitialLoadingInfo.Operations.MACHINE_BOOTING;
import static org.eclipse.che.ide.ui.loaders.initializationLoader.OperationInfo.Status.ERROR;
import static org.eclipse.che.ide.ui.loaders.initializationLoader.OperationInfo.Status.IN_PROGRESS;
import static org.eclipse.che.ide.ui.loaders.initializationLoader.OperationInfo.Status.SUCCESS;
import static org.eclipse.che.ide.extension.machine.client.perspective.MachinePerspective.MACHINE_PERSPECTIVE_ID;

/**
 * Manager for machine operations.
 *
 * @author Artem Zatsarynnyi
 */
@Singleton
public class MachineManagerImpl implements MachineManager {

    private final ExtServerStateController extServerStateController;
    private final DtoUnmarshallerFactory   dtoUnmarshallerFactory;
    private final MachineServiceClient     machineServiceClient;
    private final WorkspaceServiceClient   workspaceServiceClient;
    private final MachineConsolePresenter  machineConsolePresenter;
    private final MachineStatusNotifier    machineStatusNotifier;
    private final InitialLoadingInfo       initialLoadingInfo;
    private final PerspectiveManager       perspectiveManager;
    private final EntityFactory            entityFactory;
    private final AppContext               appContext;
    private final DtoFactory               dtoFactory;
    private final EventBus                 eventBus;

    private MessageBus messageBus;
    private Machine    devMachine;
    private boolean    isMachineRestarting;

    @Inject
    public MachineManagerImpl(ExtServerStateController extServerStateController,
                              DtoUnmarshallerFactory dtoUnmarshallerFactory,
                              MachineServiceClient machineServiceClient,
                              WorkspaceServiceClient workspaceServiceClient,
                              MachineConsolePresenter machineConsolePresenter,
                              MachineStatusNotifier machineStatusNotifier,
                              final MessageBusProvider messageBusProvider,
                              final InitialLoadingInfo initialLoadingInfo,
                              final PerspectiveManager perspectiveManager,
                              EntityFactory entityFactory,
                              EventBus eventBus,
                              AppContext appContext,
                              Provider<SystemFileWatcher> systemFileWatcherProvider,
                              DtoFactory dtoFactory) {
        this.extServerStateController = extServerStateController;
        this.dtoUnmarshallerFactory = dtoUnmarshallerFactory;
        this.machineServiceClient = machineServiceClient;
        this.workspaceServiceClient = workspaceServiceClient;
        this.machineConsolePresenter = machineConsolePresenter;
        this.machineStatusNotifier = machineStatusNotifier;
        this.initialLoadingInfo = initialLoadingInfo;
        this.perspectiveManager = perspectiveManager;
        this.entityFactory = entityFactory;
        this.appContext = appContext;
        this.dtoFactory = dtoFactory;
        this.eventBus = eventBus;

        this.messageBus = messageBusProvider.getMessageBus();

        systemFileWatcherProvider.get();

        eventBus.addHandler(StartWorkspaceEvent.TYPE, new StartWorkspaceHandler() {
            @Override
            public void onWorkspaceStarted(UsersWorkspaceDto workspace) {
                messageBus = messageBusProvider.getMessageBus();
            }
        });

        eventBus.addHandler(DevMachineStateEvent.TYPE, new DevMachineStateHandler() {
            @Override
            public void onMachineStarted(DevMachineStateEvent event) {
                onMachineRunning(event.getMachineId());
            }

            @Override
            public void onMachineDestroyed(DevMachineStateEvent event) {

            }
        });
    }

    @Override
    public void restartMachine(final MachineStateDto machineState) {
        eventBus.addHandler(MachineStateEvent.TYPE, new MachineStateHandler() {
            @Override
            public void onMachineRunning(MachineStateEvent event) {

            }

            @Override
            public void onMachineDestroyed(MachineStateEvent event) {
                if (isMachineRestarting) {
                    final String recipeUrl = machineState.getSource().getLocation();
                    final String displayName = machineState.getName();
                    final boolean isDev = machineState.isDev();

                    startMachine(recipeUrl, displayName, isDev, RESTART);

                    isMachineRestarting = false;
                }
            }
        });

        destroyMachine(machineState).then(new Operation<Void>() {
            @Override
            public void apply(Void arg) throws OperationException {
                isMachineRestarting = true;
            }
        });
    }

    /** Start new machine. */
    @Override
    public void startMachine(String recipeURL, String displayName) {
        startMachine(recipeURL, displayName, false, START);
    }

    /** Start new machine as dev-machine (bind workspace to running machine). */
    @Override
    public void startDevMachine(String recipeURL, String displayName) {
        startMachine(recipeURL, displayName, true, START);
    }

    private void startMachine(final String recipeURL,
                              final String displayName,
                              final boolean isDev,
                              final MachineOperationType operationType) {

        LimitsDto limitsDto = dtoFactory.createDto(LimitsDto.class).withMemory(1024);
        if (isDev) {
            limitsDto.withMemory(3072);
        }
        MachineSourceDto sourceDto = dtoFactory.createDto(MachineSourceDto.class).withType("Recipe").withLocation(recipeURL);

        MachineConfigDto configDto = dtoFactory.createDto(MachineConfigDto.class)
                                               .withDev(isDev)
                                               .withName(displayName)
                                               .withSource(sourceDto)
                                               .withLimits(limitsDto)
                                               .withType("docker");

        Promise<MachineStateDto> machineStatePromise = workspaceServiceClient.createMachine(appContext.getWorkspace().getId(), configDto);

        machineStatePromise.then(new Operation<MachineStateDto>() {
            @Override
            public void apply(final MachineStateDto machineStateDto) throws OperationException {
                eventBus.fireEvent(new MachineStartingEvent(machineStateDto));

                subscribeToOutput(machineStateDto.getChannels().getOutput());

                RunningListener runningListener = null;

                if (isDev) {
                    runningListener = new RunningListener() {
                        @Override
                        public void onRunning() {
                            onMachineRunning(machineStateDto.getId());
                        }
                    };
                }

                machineStatusNotifier.trackMachine(machineStateDto, runningListener, operationType);
            }
        });
    }

    @Override
    public void onMachineRunning(final String machineId) {
        machineServiceClient.getMachine(machineId).then(new Operation<MachineDto>() {
            @Override
            public void apply(MachineDto machineDto) throws OperationException {
                appContext.setDevMachineId(machineId);
                appContext.setProjectsRoot(machineDto.getMetadata().projectsRoot());
                devMachine = entityFactory.createMachine(machineDto);
                extServerStateController.initialize(devMachine.getWsServerExtensionsUrl() + "/" + appContext.getWorkspace().getId());
            }
        });
    }

    @Override
    public Promise<Void> destroyMachine(final MachineStateDto machineState) {
        return machineServiceClient.destroyMachine(machineState.getId()).then(new Operation<Void>() {
            @Override
            public void apply(Void arg) throws OperationException {
                machineStatusNotifier.trackMachine(machineState, DESTROY);

                final String devMachineId = appContext.getDevMachineId();
                if (devMachineId != null && machineState.getId().equals(devMachineId)) {
                    appContext.setDevMachineId(null);
                }
            }
        });
    }

    private void subscribeToOutput(final String channel) {
        try {
            messageBus.subscribe(
                    channel,
                    new SubscriptionHandler<String>(new OutputMessageUnmarshaller()) {
                        @Override
                        protected void onMessageReceived(String result) {
                            machineConsolePresenter.print(result);
                        }

                        @Override
                        protected void onErrorReceived(Throwable exception) {
                            Log.error(MachineManagerImpl.class, exception);
                        }
                    });
        } catch (WebSocketException e) {
            Log.error(MachineManagerImpl.class, e);
        }
    }

    private void subscribeToMachineStatus(String machineStatusChanel) {
        final Unmarshallable<MachineStatusEvent> unmarshaller = dtoUnmarshallerFactory.newWSUnmarshaller(MachineStatusEvent.class);
        try {
            messageBus.subscribe(machineStatusChanel, new SubscriptionHandler<MachineStatusEvent>(unmarshaller) {
                @Override
                protected void onMessageReceived(MachineStatusEvent event) {
                    onMachineStatusChanged(event);
                }

                @Override
                protected void onErrorReceived(Throwable exception) {
                    Log.error(MachineManagerImpl.class, exception);
                }
            });
        } catch (WebSocketException exception) {
            Log.error(getClass(), exception);
        }
    }

    private void onMachineStatusChanged(MachineStatusEvent event) {
        switch (event.getEventType()) {
            case RUNNING:
                initialLoadingInfo.setOperationStatus(MACHINE_BOOTING.getValue(), SUCCESS);

                String machineId = event.getMachineId();
                appContext.setDevMachineId(machineId);
                onMachineRunning(machineId);

                eventBus.fireEvent(new DevMachineStateEvent(event));
                break;
            case ERROR:
                initialLoadingInfo.setOperationStatus(MACHINE_BOOTING.getValue(), ERROR);
                break;
            default:
        }
    }


    @Override
    public void onDevMachineCreating(MachineStateDto machineState) {
        perspectiveManager.setPerspectiveId(MACHINE_PERSPECTIVE_ID);
        initialLoadingInfo.setOperationStatus(MACHINE_BOOTING.getValue(), IN_PROGRESS);

        ChannelsDto channels = machineState.getChannels();
        subscribeToOutput(channels.getOutput());
        subscribeToMachineStatus(channels.getStatus());
    }
}
