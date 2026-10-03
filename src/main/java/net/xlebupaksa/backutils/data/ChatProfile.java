package net.xlebupaksa.backutils.data;

/**
 * One chat profile: the format a player's messages are wrapped in.
 *
 * @param format the markup, containing {@code {m}} where the message goes
 * @param sound  the sound the line types itself out with, or "" for none; always a member of the
 *               server's palette, which the editor offers and the server checks against
 */
public record ChatProfile(long id, String name, String format, String sound, String player) {}
