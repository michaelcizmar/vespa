// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.yahoo.vespa.zookeeper.client.ZkClientConfigBuilder;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.admin.ZooKeeperAdmin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author hmusum
 */
@SuppressWarnings("unused") // Created by injection
public class VespaZooKeeperAdminImpl implements VespaZooKeeperAdmin {

    private static final Logger log = java.util.logging.Logger.getLogger(VespaZooKeeperAdminImpl.class.getName());

    @Override
    public void reconfigure(String connectionSpec, String servers) throws ReconfigException {
        try (ZooKeeperAdmin zooKeeperAdmin = createAdmin(connectionSpec)) {
            long fromConfig = -1;
            // Using string parameters because the List variant of reconfigure fails to join empty lists (observed on 3.5.6, fixed in 3.7.0)
            byte[] appliedConfig = zooKeeperAdmin.reconfigure(null, null, servers, fromConfig, null);
            log.log(Level.INFO, "Applied ZooKeeper config: " + new String(appliedConfig, StandardCharsets.UTF_8));
        }
        catch (   KeeperException.ReconfigInProgress
                | KeeperException.ConnectionLossException
                | KeeperException.NewConfigNoQuorum  e) {
            throw new ReconfigException(e);
        }
        catch (KeeperException | IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private ZooKeeperAdmin createAdmin(String connectionSpec) throws IOException {
        return new ZooKeeperAdmin(connectionSpec, (int) sessionTimeout().toMillis(),
                                  (event) -> log.log(Level.INFO, event.toString()), new ZkClientConfigBuilder().toConfig());
    }

}

