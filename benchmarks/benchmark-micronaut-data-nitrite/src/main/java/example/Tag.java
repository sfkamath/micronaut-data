package example;

import io.micronaut.core.annotation.Introspected;

/**
 * Introspected POJO for testing SERDE property strategy.
 */
@Introspected
public final class Tag implements java.io.Serializable {
    private String name;
    private String color;

    public Tag() {
    }

    public Tag(String name, String color) {
        this.name = name;
        this.color = color;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
