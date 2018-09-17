package com.nija123098.sithreon.backend.objects;

import java.util.regex.Pattern;

/**
 * Represents a {@link TeamMember} belonging to a specific
 * {@link Team} and {@link Match} in case a {@link TeamMember}
 * is on multiple {@link Team}s or on the same {@link Team} multiple times.
 */
public class Competitor extends TeamMember {
    private final String matchId;
    private final int teamNumber;
    public Competitor(Repository repository, String hash, String matchId, int teamNumber) {
        super(repository, hash);
        this.matchId = matchId;
        this.teamNumber = teamNumber;
    }

    public Competitor(TeamMember teamMember, String matchId, int teamNumber) {
        this(teamMember.getRepository(), teamMember.getHash(), matchId, teamNumber);
    }

    public Competitor(String input) {
        this(input, 0);
    }// This should get me charged for war crimes.
    private Competitor(String input, int location) {
        super(Repository.getRepo(input.substring(0, (location = input.indexOf("#")))), input.substring(location, location + 40));
        String[] split = input.substring(location + 41).split(Pattern.quote("#"));
        this.matchId = split[0];
        this.teamNumber = Integer.parseInt(split[1]);
    }

    /**
     * The id associated with this instance's {@link Match}.
     *
     * @return the string representation of the {@link Match}'s id.
     */
    public String getMatchId() {
        return this.matchId;
    }

    public int getTeamNumber() {
        return this.teamNumber;
    }// Has to know this immediately.

    @Override
    public String toString() {
        return this.getRepository() + "#" + this.getHash() + "#" + this.matchId + "#" + this.teamNumber;
    }
}
