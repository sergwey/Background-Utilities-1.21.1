package net.xlebupaksa.backutils.data;

/** Owns the {@code active_profile} table, in {@code active_profile.db}: one row per player. */
public record ActiveProfile(String player, long nameProfileId, long chatProfileId) {}