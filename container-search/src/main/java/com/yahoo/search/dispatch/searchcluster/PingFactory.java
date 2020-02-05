// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.searchcluster;

import com.yahoo.search.cluster.ClusterMonitor;


public interface PingFactory {

    Pinger createPinger(Node node, ClusterMonitor<Node> monitor, PongHandler pongHandler);

}
