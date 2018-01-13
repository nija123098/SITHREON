package com.nija123098.sithreon.backend.command;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotates a method which will be called for the
 * {@link Command} class's invocation as a command.
 *
 * @author nija123098
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CommandMethod {
}
