package net.xlebupaksa.backutils.data;

/** Owns the {@code name_profile} table, in {@code name_profile.db}. */
public record NameProfile(long id, String name, String displayedName, String player) {}