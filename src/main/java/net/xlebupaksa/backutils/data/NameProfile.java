package net.xlebupaksa.backutils.data;

/** One row of the {@code name_profile} table: a display name one player may wear. */
public record NameProfile(long id, String name, String displayedName, String player) {}