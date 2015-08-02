/**
 * (c) 2014 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.datacollector.main;

import com.streamsets.datacollector.execution.Manager;
import com.streamsets.datacollector.execution.manager.standalone.StandaloneAndClusterPipelineManager;
import com.streamsets.datacollector.execution.manager.standalone.dagger.StandalonePipelineManagerModule;
import com.streamsets.datacollector.http.WebServerModule;
import com.streamsets.datacollector.main.BuildInfo;
import com.streamsets.datacollector.main.LogConfigurator;
import com.streamsets.datacollector.main.RuntimeInfo;
import com.streamsets.datacollector.store.PipelineStoreTask;
import com.streamsets.datacollector.task.Task;
import com.streamsets.datacollector.task.TaskWrapper;
import com.streamsets.datacollector.util.Configuration;

import dagger.Module;
import dagger.ObjectGraph;
import dagger.Provides;

import javax.inject.Singleton;

/**
 * Provides singleton instances of RuntimeInfo, PipelineStoreTask and PipelineTask
 */
@Module(
  injects = {TaskWrapper.class, RuntimeInfo.class, Configuration.class, PipelineStoreTask.class, LogConfigurator.class, BuildInfo.class},
  library = true,
  complete = false /* Note that all the bindings are not supplied so this must be false */
)
public class MainStandalonePipelineManagerModule { //Need better name

  private final ObjectGraph objectGraph;

  public MainStandalonePipelineManagerModule() {

    ObjectGraph objectGraph = ObjectGraph.create(StandalonePipelineManagerModule.class);
    Manager m = new StandaloneAndClusterPipelineManager(objectGraph);

    // What did we just do here?
    //1. Injected fields in StandalonePipelineManager using the StandalonePipelineManagerModule module.
    //2. Cached Object graph in StandalonePipelineManager.

    // Ok. Why do we need to cache the object graph? Why cant we use the old fashioned constructor injection?
    // Consider this case: ProductionPipeline is constructed using dagger and it depends on
    // AlertManager [one per runner/pipeline] and it also depends on pipeline store [which is a sdc/manager level
    // singleton].
    // This means when multiple pipelines are created, each owns an instance of an alert manager but all refer to the
    // same instance of pipeline store.


    //Once the manager is created, augment the object graph with web server and pipeline task modules.
    //This ensures that
    //1. PipelineTask references the above pipeline manager
    //2. Both PipelineTask and PipelineManager refer to the same instance of RuntimeInfo, PipelineStore which is
    //  a sdc level singleton.
    this.objectGraph = objectGraph.plus(new WebServerModule(m), PipelineTaskModule.class);
  }

  @Provides @Singleton
  public Task providePipelineTask(PipelineTask agent) {
    return agent;
  }

  @Provides @Singleton
  public PipelineTask providePipelineTask() {
    return objectGraph.get(PipelineTask.class);
  }

  @Provides @Singleton
  public RuntimeInfo provideRuntimeInfo() {
    return objectGraph.get(RuntimeInfo.class);
  }

  @Provides @Singleton
  public PipelineStoreTask providePipelineStoreTask() {
    return objectGraph.get(PipelineStoreTask.class);
  }

  @Provides @Singleton
  public BuildInfo provideBuildInfo() {
    return objectGraph.get(BuildInfo.class);
  }

  @Provides @Singleton
  public Configuration provideConfiguration() {
    return objectGraph.get(Configuration.class);
  }
}