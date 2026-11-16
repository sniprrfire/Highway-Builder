package net.highwaybuilder.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * A single named, typed, user-editable option. Values are stored and edited as strings
 * so they can be changed with the /highway command and saved to a simple JSON file.
 */
public abstract class Setting<T> {
    public final String group;
    public final String name;
    public final String description;
    protected final T defaultValue;
    protected T value;

    protected Setting(String group, String name, String description, T defaultValue) {
        this.group = group;
        this.name = name;
        this.description = description;
        this.defaultValue = defaultValue;
        this.value = copy(defaultValue);
    }

    public T get() {
        return value;
    }

    public void reset() {
        value = copy(defaultValue);
    }

    protected T copy(T v) {
        return v;
    }

    /** Parses and applies a value. Throws IllegalArgumentException with a readable message on bad input. */
    public abstract void parse(String input);

    public String asString() {
        return String.valueOf(value);
    }

    /** Short hint about accepted values, shown in /highway get. */
    public abstract String hint();

    // ------------------------------------------------------------------ types

    public static final class Bool extends Setting<Boolean> {
        public Bool(String group, String name, String description, boolean def) {
            super(group, name, description, def);
        }

        @Override
        public void parse(String input) {
            switch (input.trim().toLowerCase(Locale.ROOT)) {
                case "true", "on", "yes", "1" -> value = true;
                case "false", "off", "no", "0" -> value = false;
                case "toggle" -> value = !value;
                default -> throw new IllegalArgumentException("Expected true/false");
            }
        }

        @Override
        public String hint() {
            return "true | false";
        }
    }

    public static final class Int extends Setting<Integer> {
        private final int min, max;

        public Int(String group, String name, String description, int def, int min, int max) {
            super(group, name, description, def);
            this.min = min;
            this.max = max;
        }

        @Override
        public void parse(String input) {
            int v;
            try {
                v = Integer.parseInt(input.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Expected a whole number");
            }
            if (v < min || v > max) throw new IllegalArgumentException("Must be between " + min + " and " + max);
            value = v;
        }

        @Override
        public String hint() {
            return min + " - " + max;
        }

        public int min() { return min; }

        public int max() { return max; }

        public void set(int v) { value = Math.max(min, Math.min(max, v)); }
    }

    public static final class Dbl extends Setting<Double> {
        private final double min, max;

        public Dbl(String group, String name, String description, double def, double min, double max) {
            super(group, name, description, def);
            this.min = min;
            this.max = max;
        }

        @Override
        public void parse(String input) {
            double v;
            try {
                v = Double.parseDouble(input.trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Expected a number");
            }
            if (v < min || v > max) throw new IllegalArgumentException("Must be between " + min + " and " + max);
            value = v;
        }

        @Override
        public String hint() {
            return min + " - " + max;
        }

        public double min() { return min; }

        public double max() { return max; }

        public void set(double v) { value = Math.max(min, Math.min(max, v)); }
    }

    public static final class Choice<E extends Enum<E>> extends Setting<E> {
        private final Class<E> type;

        public Choice(String group, String name, String description, E def) {
            super(group, name, description, def);
            this.type = def.getDeclaringClass();
        }

        @Override
        public void parse(String input) {
            String s = input.trim().toUpperCase(Locale.ROOT).replace('-', '_');
            for (E e : type.getEnumConstants()) {
                if (e.name().equals(s)) {
                    value = e;
                    return;
                }
            }
            throw new IllegalArgumentException("Expected one of: " + hint());
        }

        @Override
        public String asString() {
            return value.name().toLowerCase(Locale.ROOT).replace('_', '-');
        }

        /** Switches to the next value (wraps around). */
        public void cycle() {
            E[] all = type.getEnumConstants();
            value = all[(value.ordinal() + 1) % all.length];
        }

        @Override
        public String hint() {
            return String.join(" | ", Arrays.stream(type.getEnumConstants())
                .map(e -> e.name().toLowerCase(Locale.ROOT).replace('_', '-')).toList());
        }
    }

    public static final class Str extends Setting<String> {
        private final Predicate<String> validator;
        private final String hint;

        public Str(String group, String name, String description, String def, Predicate<String> validator, String hint) {
            super(group, name, description, def);
            this.validator = validator;
            this.hint = hint;
        }

        @Override
        public void parse(String input) {
            String s = input.trim();
            if (!validator.test(s)) throw new IllegalArgumentException("Invalid value, expected " + hint);
            value = s;
        }

        @Override
        public String hint() {
            return hint;
        }
    }

    /** Comma separated list of registry ids, e.g. minecraft:obsidian,minecraft:crying_obsidian */
    public static final class IdList extends Setting<List<String>> {
        private final Predicate<String> validator;

        public IdList(String group, String name, String description, List<String> def, Predicate<String> validator) {
            super(group, name, description, def);
            this.validator = validator;
        }

        @Override
        protected List<String> copy(List<String> v) {
            return new ArrayList<>(v);
        }

        @Override
        public void parse(String input) {
            List<String> out = new ArrayList<>();
            String trimmed = input.trim();
            if (!trimmed.isEmpty() && !trimmed.equalsIgnoreCase("none")) {
                for (String part : trimmed.split("[,\\s]+")) {
                    if (part.isEmpty()) continue;
                    String id = part.contains(":") ? part : "minecraft:" + part;
                    id = id.toLowerCase(Locale.ROOT);
                    if (!validator.test(id)) throw new IllegalArgumentException("Unknown id: " + id);
                    if (!out.contains(id)) out.add(id);
                }
            }
            value = out;
        }

        @Override
        public String asString() {
            return value.isEmpty() ? "none" : String.join(",", value);
        }

        @Override
        public String hint() {
            return "comma separated ids, e.g. obsidian,crying_obsidian (or none)";
        }
    }
}
