package com.nija123098.sithreon.backend.networking;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates the value {@link MachineAction} should invoke the indicated
 * command for completing the action appropriate for data transfer.
 *
 * @author nija123098
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Action {
    MachineAction value();
}
