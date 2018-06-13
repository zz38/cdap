/*
 * Copyright © 2018 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.cdap.gateway.handlers;

import co.cask.cdap.api.artifact.ArtifactSummary;
import co.cask.cdap.api.common.Bytes;
import co.cask.cdap.api.schedule.TriggeringScheduleInfo;
import co.cask.cdap.app.runtime.Arguments;
import co.cask.cdap.app.runtime.ProgramOptions;
import co.cask.cdap.common.BadRequestException;
import co.cask.cdap.common.app.RunIds;
import co.cask.cdap.common.conf.Constants;
import co.cask.cdap.gateway.handlers.util.AbstractAppFabricHttpHandler;
import co.cask.cdap.internal.app.runtime.ProgramOptionConstants;
import co.cask.cdap.internal.app.runtime.codec.ArgumentsCodec;
import co.cask.cdap.internal.app.runtime.codec.ProgramOptionsCodec;
import co.cask.cdap.internal.app.runtime.schedule.TriggeringScheduleInfoAdapter;
import co.cask.cdap.proto.Notification;
import co.cask.cdap.proto.ProgramRunStatus;
import co.cask.cdap.proto.id.ArtifactId;
import co.cask.cdap.proto.id.ProgramRunId;
import co.cask.cdap.proto.ops.DashboardProgramRunRecord;
import co.cask.cdap.reporting.ProgramHeartbeatStore;
import co.cask.http.HttpResponder;
import com.google.common.base.Splitter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.twill.api.RunId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

/**
 * {@link co.cask.http.HttpHandler} to handle program run operation dashboard and reports for v3 REST APIs
 *
 * TODO: [CDAP-13355] Move this handler into report generation app
 */
@Singleton
@Path(Constants.Gateway.API_VERSION_3)
public class OperationsDashboardHttpHandler extends AbstractAppFabricHttpHandler {
  private static final Logger LOG = LoggerFactory.getLogger(OperationsDashboardHttpHandler.class);
  private static final Gson GSON =
    TriggeringScheduleInfoAdapter.addTypeAdapters(new GsonBuilder())
      .registerTypeAdapter(Arguments.class, new ArgumentsCodec())
      .registerTypeAdapter(ProgramOptions.class, new ProgramOptionsCodec()).create();
  private final ProgramHeartbeatStore programHeartbeatStore;

  @Inject
  public OperationsDashboardHttpHandler(ProgramHeartbeatStore programHeartbeatStore) {
    this.programHeartbeatStore = programHeartbeatStore;
  }

  // TODO: [CDAP-13351] Need to support getting scheduled program runs if start + duration is ahead of the current time
  @GET
  @Path("/dashboard")
  public void readDashboardDetail(FullHttpRequest request, HttpResponder responder,
                                  @QueryParam("start") long startTimeSecs,
                                  @QueryParam("duration") int durationTimeSecs,
                                  @QueryParam("namespace") Set<String> namespaces) throws BadRequestException {
    if (startTimeSecs < 0) {
      throw new BadRequestException("'start' time cannot be smaller than 0.");
    }
    if (durationTimeSecs < 0) {
      throw new BadRequestException("'duration' cannot be smaller than 0.");
    }

    byte[] startRow = Bytes.toBytes(TimeUnit.SECONDS.toMillis(startTimeSecs));
    // endRow is exclusive, adding one after converting to millis
    byte[] endRow = Bytes.toBytes(TimeUnit.SECONDS.toMillis(startTimeSecs + durationTimeSecs) + 1);

    Collection<Notification> notifications = programHeartbeatStore.scan(startRow, endRow);
    List<DashboardProgramRunRecord> result =
      notifications.stream().filter(notification -> namespaces.contains(getNamespace(notification))).
        map(notification -> notificationToDashboardProgramRunRecord(notification)).collect(Collectors.toList());
    responder.sendJson(HttpResponseStatus.OK, GSON.toJson(result));
  }

  private String getNamespace(Notification notification) {
    ProgramRunId programRunId =
      GSON.fromJson(notification.getProperties().get(ProgramOptionConstants.PROGRAM_RUN_ID), ProgramRunId.class);
    return programRunId.getNamespace();
  }

  // TODO split this method into smalled methods
  private DashboardProgramRunRecord notificationToDashboardProgramRunRecord(Notification notification) {
    Map<String, String> properties = notification.getProperties();
    ProgramRunId programRunId =
      GSON.fromJson(properties.get(ProgramOptionConstants.PROGRAM_RUN_ID), ProgramRunId.class);

    RunId runId = RunIds.fromString(programRunId.getRun());
    long startTimeInSeconds = RunIds.getTime(runId, TimeUnit.SECONDS);
    ProgramRunStatus programRunStatus =
      ProgramRunStatus.valueOf(properties.get(ProgramOptionConstants.PROGRAM_STATUS));
    Long endTimeInSeconds = null;
    if (programRunStatus.isEndState()) {
      endTimeInSeconds =
        TimeUnit.MILLISECONDS.toSeconds(Long.parseLong(properties.get(ProgramOptionConstants.END_TIME)));
    } else if (programRunStatus.equals(ProgramRunStatus.SUSPENDED)) {
      endTimeInSeconds =
        TimeUnit.MILLISECONDS.toSeconds(Long.parseLong(properties.get(ProgramOptionConstants.SUSPEND_TIME)));
    }


    ProgramOptions programOptions = GSON.fromJson(properties.get(ProgramOptionConstants.PROGRAM_OPTIONS),
                                                  ProgramOptions.class);

    Arguments systemArguments = programOptions.getArguments();
    String artifactIdString = systemArguments.getOption(ProgramOptionConstants.ARTIFACT_ID);
    ArtifactId artifactId = ArtifactId.fromIdParts(Splitter.on(":").split(artifactIdString));
    // todo refactor
    String user = systemArguments.getOption(ProgramOptionConstants.PRINCIPAL, "cdap");
    long runTimeInSeconds =
      TimeUnit.MILLISECONDS.toSeconds(Long.parseLong(properties.get(ProgramOptionConstants.LOGICAL_START_TIME)));

    String startMethod = "manual";
    String scheduleInfoJson = systemArguments.getOption(ProgramOptionConstants.TRIGGERING_SCHEDULE_INFO);
    if (scheduleInfoJson != null) {
      TriggeringScheduleInfo scheduleInfo = GSON.fromJson(scheduleInfoJson, TriggeringScheduleInfo.class);
      // assume there's no composite trigger, since composite is not supported in the UI yet
      startMethod = scheduleInfo.getTriggerInfos().stream().findFirst()
        .map(trigger -> trigger.getType().name())
        // return "manual" if there's no trigger in the schedule info, but this should never happen
        .orElseGet(() -> "manual");
    }
    return new DashboardProgramRunRecord(programRunId.getNamespaceId().getNamespace(),
                                         ArtifactSummary.from(artifactId.toApiArtifactId()),
                                         new DashboardProgramRunRecord.ApplicationNameVersion(
                                           programRunId.getApplication(), programRunId.getVersion()),
                                         programRunId.getType().getPrettyName(), programRunId.getProgram(),
                                         programRunId.getRun(), user, startMethod, startTimeInSeconds,
                                         runTimeInSeconds, endTimeInSeconds, programRunStatus);
  }
}
