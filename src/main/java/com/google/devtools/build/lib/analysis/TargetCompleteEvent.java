// Copyright 2014 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.analysis;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.buildeventstream.BuildEvent;
import com.google.devtools.build.lib.buildeventstream.BuildEventId;
import com.google.devtools.build.lib.buildeventstream.BuildEventStreamProtos;
import com.google.devtools.build.lib.buildeventstream.GenericBuildEvent;
import com.google.devtools.build.lib.causes.Cause;
import com.google.devtools.build.lib.collect.nestedset.NestedSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSetBuilder;
import com.google.devtools.build.lib.collect.nestedset.Order;
import com.google.devtools.build.lib.packages.TargetUtils;
import com.google.devtools.build.lib.util.Preconditions;
import com.google.devtools.build.skyframe.SkyValue;
import java.util.Collection;

/** This event is fired as soon as a target is either built or fails. */
public final class TargetCompleteEvent implements SkyValue, BuildEvent {

  private final ConfiguredTarget target;
  private final NestedSet<Cause> rootCauses;

  private TargetCompleteEvent(ConfiguredTarget target, NestedSet<Cause> rootCauses) {
    this.target = target;
    this.rootCauses =
        (rootCauses == null) ? NestedSetBuilder.<Cause>emptySet(Order.STABLE_ORDER) : rootCauses;
  }

  /**
   * Construct a successful target completion event.
   */
  public static TargetCompleteEvent createSuccessful(ConfiguredTarget ct) {
    return new TargetCompleteEvent(ct, null);
  }

  /**
   * Construct a target completion event for a failed target, with the given non-empty root causes.
   */
  public static TargetCompleteEvent createFailed(ConfiguredTarget ct, NestedSet<Cause> rootCauses) {
    Preconditions.checkArgument(!Iterables.isEmpty(rootCauses));
    return new TargetCompleteEvent(ct, rootCauses);
  }

  /**
   * Returns the target associated with the event.
   */
  public ConfiguredTarget getTarget() {
    return target;
  }

  /**
   * Determines whether the target has failed or succeeded.
   */
  public boolean failed() {
    return !rootCauses.isEmpty();
  }

  /** Get the root causes of the target. May be empty. */
  public Iterable<Cause> getRootCauses() {
    return rootCauses;
  }

  @Override
  public BuildEventId getEventId() {
    return BuildEventId.targetCompleted(getTarget().getLabel());
  }

  @Override
  public Collection<BuildEventId> getChildrenEvents() {
    ImmutableList.Builder childrenBuilder = ImmutableList.builder();
    for (Cause cause : getRootCauses()) {
      childrenBuilder.add(BuildEventId.fromCause(cause));
    }
    if (TargetUtils.isTestRule(target.getTarget())) {
      childrenBuilder.add(BuildEventId.testSummary(target.getTarget().getLabel()));
    }
    return childrenBuilder.build();
  }

  @Override
  public BuildEventStreamProtos.BuildEvent asStreamProto() {
    BuildEventStreamProtos.TargetComplete complete =
        BuildEventStreamProtos.TargetComplete.newBuilder().setSuccess(!failed()).build();
    return GenericBuildEvent.protoChaining(this).setCompleted(complete).build();
  }
}
