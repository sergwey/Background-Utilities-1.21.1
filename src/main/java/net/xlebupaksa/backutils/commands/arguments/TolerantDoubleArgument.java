package net.xlebupaksa.backutils.commands.arguments;

import com.google.gson.JsonObject;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.List;

/**
 * A number argument that also accepts a decimal comma, which Brigadier's own
 * {@code DoubleArgumentType} rejects and which the decimal key produces on many keyboards. It stays
 * a <b>numeric</b> type: where {@code [seconds]} and {@code [channel]} are both optional, the parser
 * tells them apart by asking which one can read the text.
 */
public class TolerantDoubleArgument implements ArgumentType<Double> {

    private static final Collection<String> EXAMPLES =
            List.of("0", "1", "0.5", "0,5", "2");

    /** Carries the offending text, so the message says what was actually typed. */
    private static final DynamicCommandExceptionType ERROR_NOT_A_NUMBER =
            new DynamicCommandExceptionType(value -> Component.literal(
                    "'" + value + "' is not a number. Use a full stop or a comma."));

    private static final DynamicCommandExceptionType ERROR_TOO_LOW =
            new DynamicCommandExceptionType(value -> Component.literal(
                    "That is too small: the lowest allowed is " + value + "."));

    private static final DynamicCommandExceptionType ERROR_TOO_HIGH =
            new DynamicCommandExceptionType(value -> Component.literal(
                    "That is too large: the highest allowed is " + value + "."));

    private final double minimum;
    private final double maximum;

    private TolerantDoubleArgument(double minimum, double maximum) {
        this.minimum = minimum;
        this.maximum = maximum;
    }

    /** {@return an argument accepting a number between {@code minimum} and {@code maximum}} */
    public static TolerantDoubleArgument tolerantDouble(double minimum, double maximum) {
        return new TolerantDoubleArgument(minimum, maximum);
    }

    public static double getTolerantDouble(CommandContext<?> context, String name) {
        return context.getArgument(name, Double.class);
    }

    @Override
    public Double parse(StringReader reader) throws CommandSyntaxException {
        int start = reader.getCursor();
        while (reader.canRead() && isAllowed(reader.peek())) reader.skip();

        String text = reader.getString().substring(start, reader.getCursor());
        if (text.isEmpty()) {
            reader.setCursor(start);
            throw ERROR_NOT_A_NUMBER.createWithContext(reader, "");
        }

        double value;
        try {
            value = Double.parseDouble(text.replace(',', '.'));
        } catch (NumberFormatException e) {
            reader.setCursor(start);
            throw ERROR_NOT_A_NUMBER.createWithContext(reader, text);
        }

        if (value < minimum) {
            reader.setCursor(start);
            throw ERROR_TOO_LOW.createWithContext(reader, trim(minimum));
        }
        if (value > maximum) {
            reader.setCursor(start);
            throw ERROR_TOO_HIGH.createWithContext(reader, trim(maximum));
        }
        return value;
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }

    /** {@return the characters a number may be made of, Brigadier's set plus the comma} */
    private static boolean isAllowed(char c) {
        return (c >= '0' && c <= '9') || c == '.' || c == ',' || c == '-' || c == '+'
                || c == 'e' || c == 'E';
    }

    private static String trim(double value) {
        return value == Math.floor(value)
                ? String.valueOf((long) value)
                : String.valueOf(value);
    }

    /**
     * The serialiser, so the command tree can be sent to clients: a custom argument type with no
     * registered {@link ArgumentTypeInfo} cannot be written, which would break the whole tree. The
     * wire format is the simplest thing that works — two doubles — since only this mod reads it.
     */
    public static final class Info implements ArgumentTypeInfo<TolerantDoubleArgument, Info.Template> {

        /**
         * The single instance used both in the argument type registry and in {@code
         * ArgumentTypeInfos}' class map: writing a command tree asks the registry for its id by
         * identity, so a second instance would come back as -1.
         */
        public static final Info INSTANCE = new Info();

        @Override
        public void serializeToNetwork(Template template, FriendlyByteBuf buffer) {
            buffer.writeDouble(template.minimum);
            buffer.writeDouble(template.maximum);
        }

        @Override
        public Template deserializeFromNetwork(FriendlyByteBuf buffer) {
            return new Template(buffer.readDouble(), buffer.readDouble());
        }

        @Override
        public void serializeToJson(Template template, JsonObject json) {
            json.addProperty("min", template.minimum);
            json.addProperty("max", template.maximum);
        }

        @Override
        public Template unpack(TolerantDoubleArgument argument) {
            return new Template(argument.minimum, argument.maximum);
        }

        public final class Template implements ArgumentTypeInfo.Template<TolerantDoubleArgument> {

            private final double minimum;
            private final double maximum;

            Template(double minimum, double maximum) {
                this.minimum = minimum;
                this.maximum = maximum;
            }

            @Override
            public TolerantDoubleArgument instantiate(CommandBuildContext context) {
                return tolerantDouble(minimum, maximum);
            }

            @Override
            public ArgumentTypeInfo<TolerantDoubleArgument, ?> type() {
                return Info.this;
            }
        }
    }
}
