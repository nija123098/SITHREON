package com.nija123098.sithreon.backend.networking;

import com.nija123098.sithreon.backend.Machine;
import com.nija123098.sithreon.backend.machines.CheckClient;
import com.nija123098.sithreon.backend.machines.GameServer;
import com.nija123098.sithreon.backend.machines.RunnerClient;

/**
 * An enum representing the types of {@link Machine}s that report
 * to servers for use of indicating being ready to serve.
 */
public enum ManagedMachineType {
    /**
     * The representation of {@link CheckClient}.
     */
    CODE_CHECK,
    /**
     * The representation of {@link GameServer}.
     */
    GAME_SERVER,
    /**
     * The representation of {@link RunnerClient}.
     */
    GAME_RUNNER,
    ;
}
