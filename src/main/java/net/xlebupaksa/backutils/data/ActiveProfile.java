package net.xlebupaksa.backutils.data;

/** One row of the {@code active_profile} table: the name and chat profiles a player is wearing. */
public record ActiveProfile(String player, long nameProfileId, long chatProfileId) {}