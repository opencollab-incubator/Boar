package ac.boar.api.anticheat.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface CheckInfo {
    String name();

    String type() default "";

    int maxVl() default -1;
}
